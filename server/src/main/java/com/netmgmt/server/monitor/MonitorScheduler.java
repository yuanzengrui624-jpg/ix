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
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MonitorScheduler {
  private static final Logger log = LoggerFactory.getLogger(MonitorScheduler.class);

  private final DeviceDao deviceDao;
  private final MonitorLogDao monitorLogDao;
  private final AlarmDao alarmDao;

  private final ScheduledExecutorService scheduler;
  private final int intervalSeconds;
  private final int pingTimeoutMs;

  public MonitorScheduler(DataSource ds, Props props) {
    this.deviceDao = new DeviceDao(ds);
    this.monitorLogDao = new MonitorLogDao(ds);
    this.alarmDao = new AlarmDao(ds);
    this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "monitor-scheduler");
      t.setDaemon(true);
      return t;
    });
    this.intervalSeconds = props.getInt("monitor.intervalSeconds", 30);
    this.pingTimeoutMs = props.getInt("monitor.pingTimeoutMs", 1500);
  }

  public void start() {
    scheduler.scheduleWithFixedDelay(this::safeRunOnce, 2, intervalSeconds, TimeUnit.SECONDS);
  }

  public void stop() {
    scheduler.shutdownNow();
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
    LocalDateTime now = LocalDateTime.now();

    for (Device d : devices) {
      boolean ok = PingChecker.ping(d.ip(), pingTimeoutMs);
      int status = ok ? 1 : 2;

      deviceDao.updateStatus(d.id(), status, now);
      monitorLogDao.insert(d.id(), null, null, ok ? 1 : 0, null, now);

      if (!ok) {
        alarmDao.insert(d.id(), "OFFLINE", "设备不可达: " + d.ip(), now);
      }
    }
  }
}

