package com.netmgmt.server.monitor;

import com.netmgmt.server.config.Props;
import com.netmgmt.server.dao.AlarmDao;
import com.netmgmt.server.dao.DeviceDao;
import com.netmgmt.server.dao.MonitorLogDao;
import com.netmgmt.server.model.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MonitorScheduler {
  private static final Logger log = LoggerFactory.getLogger(MonitorScheduler.class);

  private final DeviceDao deviceDao;
  private final MonitorLogDao monitorLogDao;
  private final AlarmDao alarmDao;

  private final ScheduledExecutorService scheduler;
  private final ExecutorService workerPool;
  private final SnmpCollector snmpCollector;
  private final SshMetricCollector sshMetricCollector;
  private final int intervalSeconds;
  private final int pingTimeoutMs;
  private final int maxRunSeconds;
  private final int dedupMinutes;
  private final int dataRetentionDays;
  private final double cpuThreshold;
  private final double memThreshold;

  public MonitorScheduler(DataSource ds, Props props) {
    this.deviceDao = new DeviceDao(ds);
    this.monitorLogDao = new MonitorLogDao(ds);
    this.alarmDao = new AlarmDao(ds);
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "monitor-scheduler");
      t.setDaemon(true);
      return t;
    });
    int workerThreads = Math.max(1, props.getInt("monitor.workerThreads", 8));
    this.workerPool = Executors.newFixedThreadPool(workerThreads, r -> {
      Thread t = new Thread(r, "monitor-worker");
      t.setDaemon(true);
      return t;
    });
    this.snmpCollector = new SnmpCollector(props);
    this.sshMetricCollector = new SshMetricCollector(props);
    this.intervalSeconds = props.getInt("monitor.intervalSeconds", 30);
    this.pingTimeoutMs = props.getInt("monitor.pingTimeoutMs", 1500);
    this.maxRunSeconds = props.getInt("monitor.maxRunSeconds", 10);
    this.dedupMinutes = props.getInt("alarm.dedupMinutes", 5);
    this.dataRetentionDays = props.getInt("monitor.dataRetentionDays", 30);
    this.cpuThreshold = props.getDouble("alarm.cpuThreshold", 80.0);
    this.memThreshold = props.getDouble("alarm.memThreshold", 80.0);
  }

  public void start() {
    scheduler.scheduleWithFixedDelay(this::safeRunOnce, 2, intervalSeconds, TimeUnit.SECONDS);
    scheduler.scheduleWithFixedDelay(this::cleanOldData, 10, 24 * 3600, TimeUnit.SECONDS);
  }

  private void cleanOldData() {
    try {
      int deleted = monitorLogDao.deleteOlderThan(dataRetentionDays);
      if (deleted > 0) {
        log.info("已清理 {} 条超过 {} 天的监控日志", deleted, dataRetentionDays);
      }
    } catch (Exception e) {
      log.warn("清理旧数据失败", e);
    }
  }

  public void stop() {
    scheduler.shutdownNow();
    workerPool.shutdownNow();
    try {
      snmpCollector.close();
    } catch (Exception e) {
      log.warn("关闭 SNMP 采集器失败", e);
    }
  }

  public void runOnceNowAsync() {
    scheduler.execute(this::safeRunOnce);
  }

  private void safeRunOnce() {
    try {
      runOnce();
    } catch (Exception e) {
      log.warn("Monitor run failed", e);
    }
  }

  private void runOnce() throws Exception {
    List<Device> devices = deviceDao.listAll();
    if (devices.isEmpty()) return;

    LocalDateTime now = LocalDateTime.now();
    long cycleStart = System.nanoTime();
    List<Future<?>> futures = new ArrayList<>();

    for (Device d : devices) {
      futures.add(workerPool.submit(() -> collectDevice(d, now)));
    }

    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(maxRunSeconds);
    for (Future<?> future : futures) {
      long remainingNanos = deadline - System.nanoTime();
      if (remainingNanos <= 0) {
        future.cancel(true);
        continue;
      }
      try {
        future.get(remainingNanos, TimeUnit.NANOSECONDS);
      } catch (Exception e) {
        future.cancel(true);
        log.warn("设备监控任务超时或失败: {}", e.getMessage());
      }
    }

    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cycleStart);
    log.info("本轮监控完成: 设备 {} 台, 用时 {} ms", devices.size(), elapsedMs);
  }

  private void collectDevice(Device d, LocalDateTime now) {
    try {
      boolean ok = PingChecker.ping(d.ip(), pingTimeoutMs);
      int status = ok ? 1 : 2;
      int prevStatus = d.status();

      deviceDao.updateStatus(d.id(), status, now);

      SnmpSample sample = ok ? snmpCollector.collect(d) : new SnmpSample(null, null, null, false, null);
      Double cpu = sample.cpu();
      Double mem = sample.mem();
      String ifJson = sample.interfaceStatusJson();
      SshMetricCollector.MetricSample sshMetrics = ok
          ? sshMetricCollector.fillMissing(d, cpu, mem)
          : new SshMetricCollector.MetricSample(cpu, mem, false, null);
      cpu = sshMetrics.cpu();
      mem = sshMetrics.mem();
      LocalHostMetricCollector.Snapshot localSnapshot = ok && isLocalAddress(d.ip())
          ? LocalHostMetricCollector.collect(cpu, mem, ifJson)
          : new LocalHostMetricCollector.Snapshot(cpu, mem, ifJson, false, null);
      cpu = localSnapshot.cpu();
      mem = localSnapshot.mem();
      ifJson = localSnapshot.interfaceJson();

      monitorLogDao.insert(d.id(), cpu, mem, ok ? 1 : 0, ifJson, now);

      if (isDemoLoopbackDevice(d.ip())) {
        alarmDao.recoverByDevice(d.id(), "OFFLINE");
        alarmDao.recoverByDevice(d.id(), "CPU_HIGH");
        alarmDao.recoverByDevice(d.id(), "MEM_HIGH");
        return;
      }

      if (!ok) {
        if (!alarmDao.hasRecentUnacked(d.id(), "OFFLINE", dedupMinutes)) {
          alarmDao.insert(d.id(), "OFFLINE", "设备不可达，Ping超时", now);
        }
        return;
      }

      if (prevStatus == 2 && alarmDao.recoverByDevice(d.id(), "OFFLINE") > 0) {
        log.info("设备恢复在线: {} ({}), 自动恢复 OFFLINE 告警", d.ip(), d.id());
      }

      if (cpu != null && cpu > cpuThreshold) {
        if (!alarmDao.hasRecentUnacked(d.id(), "CPU_HIGH", dedupMinutes)) {
          alarmDao.insert(d.id(), "CPU_HIGH",
              String.format("CPU使用率 %.1f%% 超过阈值 %.0f%%", cpu, cpuThreshold), now);
        }
      } else if (cpu != null && alarmDao.recoverByDevice(d.id(), "CPU_HIGH") > 0) {
        log.info("设备 CPU 告警恢复: {} ({})", d.ip(), d.id());
      }

      if (mem != null && mem > memThreshold) {
        if (!alarmDao.hasRecentUnacked(d.id(), "MEM_HIGH", dedupMinutes)) {
          alarmDao.insert(d.id(), "MEM_HIGH",
              String.format("内存使用率 %.1f%% 超过阈值 %.0f%%", mem, memThreshold), now);
        }
      } else if (mem != null && alarmDao.recoverByDevice(d.id(), "MEM_HIGH") > 0) {
        log.info("设备内存告警恢复: {} ({})", d.ip(), d.id());
      }

      if (!sample.success() && sample.errorMessage() != null && !sample.errorMessage().isBlank()) {
        if (sshMetrics.filled()) {
          log.info("设备 {} SNMP 指标未采集完整，已通过补采获得 Windows CPU/内存: {}", d.ip(), sshMetrics.message());
        } else {
          log.info("设备 {} Ping 正常，但 SNMP 指标未采集到: {}", d.ip(), sample.errorMessage());
        }
      }

      if (!sshMetrics.filled() && sshMetrics.message() != null && !sshMetrics.message().isBlank()
          && (cpu == null || mem == null)) {
        log.info("设备 {} 补采 Windows CPU/内存失败: {}", d.ip(), sshMetrics.message());
      }

      if (localSnapshot.filled() && localSnapshot.message() != null && !localSnapshot.message().isBlank()) {
        log.info("设备 {} {}", d.ip(), localSnapshot.message());
      }
    } catch (Exception e) {
      log.warn("设备 {} 采集失败", d.ip(), e);
    }
  }

  private static boolean isLocalAddress(String ip) {
    if (ip == null) return false;
    String normalized = ip.trim().toLowerCase();
    return normalized.equals("localhost") || normalized.equals("::1") || normalized.startsWith("127.");
  }

  private static boolean isDemoLoopbackDevice(String ip) {
    if (ip == null) return false;
    String normalized = ip.trim().toLowerCase();
    return normalized.startsWith("127.") && !"127.0.0.1".equals(normalized);
  }
}

