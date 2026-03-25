package com.netmgmt.client.net;

import com.netmgmt.client.model.Alarm;
import com.netmgmt.client.model.ConfigBackup;
import com.netmgmt.client.model.Device;
import com.netmgmt.client.model.MonitorLog;
import com.netmgmt.common.json.Json;
import com.netmgmt.common.protocol.ApiResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ApiClient {
  private final ServerConnector connector;

  public ApiClient(ServerConnector connector) {
    this.connector = connector;
  }

  public List<Device> listDevices() throws IOException {
    ApiResponse resp = connector.call("DEVICE_LIST", Map.of());
    ensureOk(resp);
    if (resp.data() == null) return Collections.emptyList();
    List<Device> list = Json.mapper().convertValue(resp.data(),
        Json.mapper().getTypeFactory().constructCollectionType(List.class, Device.class));
    return list != null ? list : Collections.emptyList();
  }

  public long addDevice(String ip, String type, String community, String sshUser, String sshPwd) throws IOException {
    Map<String, Object> data = new HashMap<>();
    data.put("ip", ip);
    data.put("type", type);
    data.put("community", community);
    data.put("sshUser", sshUser);
    data.put("sshPwd", sshPwd);
    ApiResponse resp = connector.call("DEVICE_ADD", data);
    ensureOk(resp);
    Map<?, ?> m = Json.mapper().convertValue(resp.data(), Map.class);
    Object rid = m.get("id");
    return ((Number) rid).longValue();
  }

  public boolean updateDevice(long id, String ip, String type, String community, String sshUser, String sshPwd) throws IOException {
    Map<String, Object> data = new HashMap<>();
    data.put("id", id);
    data.put("ip", ip);
    data.put("type", type);
    data.put("community", community);
    data.put("sshUser", sshUser);
    data.put("sshPwd", sshPwd);
    ApiResponse resp = connector.call("DEVICE_UPDATE", data);
    ensureOk(resp);
    Map<?, ?> m = Json.mapper().convertValue(resp.data(), Map.class);
    return Boolean.TRUE.equals(m.get("updated"));
  }

  public boolean deleteDevice(long id) throws IOException {
    ApiResponse resp = connector.call("DEVICE_DELETE", Map.of("id", id));
    ensureOk(resp);
    Map<?, ?> m = Json.mapper().convertValue(resp.data(), Map.class);
    return Boolean.TRUE.equals(m.get("deleted"));
  }

  public List<Alarm> listAlarms(int limit) throws IOException {
    ApiResponse resp = connector.call("ALARM_LIST", Map.of("limit", limit));
    ensureOk(resp);
    if (resp.data() == null) return Collections.emptyList();
    List<Alarm> list = Json.mapper().convertValue(resp.data(),
        Json.mapper().getTypeFactory().constructCollectionType(List.class, Alarm.class));
    return list != null ? list : Collections.emptyList();
  }

  public boolean ackAlarm(long id) throws IOException {
    ApiResponse resp = connector.call("ALARM_ACK", Map.of("id", id));
    ensureOk(resp);
    Map<?, ?> m = Json.mapper().convertValue(resp.data(), Map.class);
    return Boolean.TRUE.equals(m.get("acked"));
  }

  public MonitorLog latestMonitor(long deviceId) throws IOException {
    ApiResponse resp = connector.call("MONITOR_LATEST", Map.of("deviceId", deviceId));
    ensureOk(resp);
    if (resp.data() == null) return null;
    return Json.mapper().convertValue(resp.data(), MonitorLog.class);
  }

  public List<MonitorLog> queryMonitor(long deviceId, LocalDateTime start, LocalDateTime end, int limit) throws IOException {
    ApiResponse resp = connector.call("MONITOR_QUERY", Map.of(
        "deviceId", deviceId,
        "start", start.toString(),
        "end", end.toString(),
        "limit", limit
    ));
    ensureOk(resp);
    if (resp.data() == null) return Collections.emptyList();
    List<MonitorLog> list = Json.mapper().convertValue(resp.data(),
        Json.mapper().getTypeFactory().constructCollectionType(List.class, MonitorLog.class));
    return list != null ? list : Collections.emptyList();
  }

  public void runMonitorOnce() throws IOException {
    ApiResponse resp = connector.call("MONITOR_RUN_ONCE", Map.of());
    ensureOk(resp);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> getStats() throws IOException {
    ApiResponse resp = connector.call("STATS", Map.of());
    ensureOk(resp);
    if (resp.data() == null) return Collections.emptyMap();
    Map<String, Object> m = Json.mapper().convertValue(resp.data(), Map.class);
    return m != null ? m : Collections.emptyMap();
  }

  public List<ConfigBackup> listConfigBackups(int limit) throws IOException {
    ApiResponse resp = connector.call("CONFIG_BACKUP_LIST", Map.of("limit", limit));
    ensureOk(resp);
    if (resp.data() == null) return Collections.emptyList();
    List<ConfigBackup> list = Json.mapper().convertValue(resp.data(),
        Json.mapper().getTypeFactory().constructCollectionType(List.class, ConfigBackup.class));
    return list != null ? list : Collections.emptyList();
  }

  public long backupDevice(long deviceId) throws IOException {
    ApiResponse resp = connector.call("CONFIG_BACKUP_DEVICE", Map.of("deviceId", deviceId));
    ensureOk(resp);
    Map<?, ?> m = Json.mapper().convertValue(resp.data(), Map.class);
    Object rid = m != null ? m.get("id") : null;
    return rid instanceof Number n ? n.longValue() : -1;
  }

  public int backupAll() throws IOException {
    ApiResponse resp = connector.call("CONFIG_BACKUP_ALL", Map.of());
    ensureOk(resp);
    Map<?, ?> m = Json.mapper().convertValue(resp.data(), Map.class);
    Object cnt = m != null ? m.get("count") : null;
    return cnt instanceof Number n ? n.intValue() : 0;
  }

  public ConfigBackup getConfigBackup(long id) throws IOException {
    ApiResponse resp = connector.call("CONFIG_BACKUP_GET", Map.of("id", id));
    ensureOk(resp);
    if (resp.data() == null) return null;
    return Json.mapper().convertValue(resp.data(), ConfigBackup.class);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> restoreConfigBackup(long id) throws IOException {
    ApiResponse resp = connector.call("CONFIG_BACKUP_RESTORE", Map.of("id", id));
    ensureOk(resp);
    if (resp.data() == null) return Collections.emptyMap();
    Map<String, Object> m = Json.mapper().convertValue(resp.data(), Map.class);
    return m != null ? m : Collections.emptyMap();
  }

  private static void ensureOk(ApiResponse resp) {
    if (resp == null) throw new IllegalStateException("No response");
    if (!resp.ok()) throw new IllegalStateException(resp.error() == null ? "Request failed" : resp.error());
  }
}

