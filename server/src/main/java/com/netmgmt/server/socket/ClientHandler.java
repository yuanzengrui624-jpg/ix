package com.netmgmt.server.socket;

import com.netmgmt.common.json.Json;
import com.netmgmt.common.protocol.ApiRequest;
import com.netmgmt.common.protocol.ApiResponse;
import com.netmgmt.server.config.Props;
import com.netmgmt.server.dao.AlarmDao;
import com.netmgmt.server.dao.ConfigBackupDao;
import com.netmgmt.server.dao.DeviceDao;
import com.netmgmt.server.dao.MonitorLogDao;
import com.netmgmt.server.monitor.MonitorScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

final class ClientHandler implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

  private final Socket socket;
  private final DeviceDao deviceDao;
  private final AlarmDao alarmDao;
  private final MonitorLogDao monitorLogDao;
  private final ConfigBackupDao configBackupDao;
  private final MonitorScheduler monitorScheduler;

  ClientHandler(Socket socket, DataSource ds, MonitorScheduler monitorScheduler, Props props) {
    this.socket = socket;
    this.deviceDao = new DeviceDao(ds);
    this.alarmDao = new AlarmDao(ds);
    this.monitorLogDao = new MonitorLogDao(ds);
    this.configBackupDao = new ConfigBackupDao(ds, deviceDao, props);
    this.monitorScheduler = monitorScheduler;
  }

  @Override
  public void run() {
    try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
         BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

      String line;
      while ((line = in.readLine()) != null) {
        if (line.isBlank()) continue;
        ApiResponse resp = handle(line);
        out.write(Json.toJson(resp));
        out.write("\n");
        out.flush();
      }
    } catch (Exception e) {
      log.info("Client disconnected: {} ({})", socket.getRemoteSocketAddress(), e.toString());
    } finally {
      try {
        socket.close();
      } catch (IOException ignored) {}
    }
  }

  private ApiResponse handle(String line) {
    ApiRequest req;
    try {
      req = Json.mapper().readValue(line, ApiRequest.class);
    } catch (Exception e) {
      return ApiResponse.fail(null, "Bad request JSON: " + e.getMessage());
    }

    String id = req.id();
    String action = req.action();
    Map<String, Object> data = req.data() == null ? Map.of() : req.data();

    try {
      return switch (action) {
        case "DEVICE_LIST" -> ApiResponse.ok(id, deviceDao.listAll());
        case "DEVICE_ADD" -> ApiResponse.ok(id, Map.of("id",
            deviceDao.insert(
                str(data, "ip"),
                str(data, "type"),
                optStr(data, "community"),
                optStr(data, "sshUser"),
                optStr(data, "sshPwd")
            )));
        case "DEVICE_UPDATE" -> ApiResponse.ok(id, Map.of("updated",
            deviceDao.update(
                lng(data, "id"),
                str(data, "ip"),
                str(data, "type"),
                optStr(data, "community"),
                optStr(data, "sshUser"),
                optStr(data, "sshPwd")
            )));
        case "DEVICE_DELETE" -> ApiResponse.ok(id, Map.of("deleted", deviceDao.delete(lng(data, "id"))));

        case "ALARM_LIST" -> ApiResponse.ok(id, alarmDao.listRecent(intOr(data, "limit", 200)));
        case "ALARM_ACK" -> ApiResponse.ok(id, Map.of("acked",
            alarmDao.acknowledge(lng(data, "id"), LocalDateTime.now())));

        case "MONITOR_LATEST" -> ApiResponse.ok(id, monitorLogDao.latestByDevice(lng(data, "deviceId")));
        case "MONITOR_QUERY" -> {
          long deviceId = lng(data, "deviceId");
          LocalDateTime start = LocalDateTime.parse(str(data, "start"));
          LocalDateTime end = LocalDateTime.parse(str(data, "end"));
          int limit = intOr(data, "limit", 5000);
          yield ApiResponse.ok(id, monitorLogDao.queryByDeviceAndTime(deviceId, start, end, limit));
        }

        case "MONITOR_RUN_ONCE" -> {
          monitorScheduler.runOnceNowAsync();
          yield ApiResponse.ok(id, Map.of("scheduled", true));
        }

        case "STATS" -> {
          var deviceStats = deviceDao.countByStatus();
          int unackedAlarms = alarmDao.countUnacked();
          var alarmTrend = alarmDao.alarmTrend7Days();
          yield ApiResponse.ok(id, Map.of(
              "deviceTotal", deviceStats.get("total"),
              "deviceOnline", deviceStats.get("online"),
              "deviceOffline", deviceStats.get("offline"),
              "unackedAlarms", unackedAlarms,
              "alarmTrend", alarmTrend
          ));
        }

        case "CONFIG_BACKUP_LIST" -> ApiResponse.ok(id, configBackupDao.listRecent(intOr(data, "limit", 500)));
        case "CONFIG_BACKUP_DEVICE" -> ApiResponse.ok(id, Map.of("id", configBackupDao.backupDevice(lng(data, "deviceId"))));
        case "CONFIG_BACKUP_ALL" -> ApiResponse.ok(id, Map.of("count", configBackupDao.backupAll()));
        case "CONFIG_BACKUP_GET" -> ApiResponse.ok(id, configBackupDao.getById(lng(data, "id")));
        case "CONFIG_BACKUP_RESTORE" -> ApiResponse.ok(id, configBackupDao.restoreBackup(lng(data, "id")));

        default -> ApiResponse.fail(id, "Unknown action: " + action);
      };
    } catch (Exception e) {
      log.warn("Request failed: action={}, err={}", action, e.toString());
      return ApiResponse.fail(id, e.getMessage());
    }
  }

  private static String str(Map<String, Object> data, String key) {
    Object v = data.get(key);
    if (v == null) throw new IllegalArgumentException("Missing field: " + key);
    return String.valueOf(v);
  }

  private static String optStr(Map<String, Object> data, String key) {
    Object v = data.get(key);
    return v == null ? null : String.valueOf(v);
  }

  private static long lng(Map<String, Object> data, String key) {
    Object v = data.get(key);
    if (v == null) throw new IllegalArgumentException("Missing field: " + key);
    if (v instanceof Number n) return n.longValue();
    return Long.parseLong(String.valueOf(v));
  }

  private static int intOr(Map<String, Object> data, String key, int def) {
    Object v = data.get(key);
    if (v == null) return def;
    if (v instanceof Number n) return n.intValue();
    return Integer.parseInt(String.valueOf(v));
  }
}

