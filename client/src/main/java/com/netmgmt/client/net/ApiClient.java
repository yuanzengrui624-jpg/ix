package com.netmgmt.client.net;

import com.netmgmt.client.model.Alarm;
import com.netmgmt.client.model.Device;
import com.netmgmt.client.model.MonitorLog;
import com.netmgmt.common.json.Json;
import com.netmgmt.common.protocol.ApiResponse;

import java.io.IOException;
import java.time.LocalDateTime;
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
    return Json.mapper().convertValue(resp.data(),
        Json.mapper().getTypeFactory().constructCollectionType(List.class, Device.class));
  }

  public long addDevice(String ip, String type, String community, String sshUser, String sshPwd) throws IOException {
    ApiResponse resp = connector.call("DEVICE_ADD", Map.of(
        "ip", ip,
        "type", type,
        "community", community,
        "sshUser", sshUser,
        "sshPwd", sshPwd
    ));
    ensureOk(resp);
    Map<?, ?> m = Json.mapper().convertValue(resp.data(), Map.class);
    Object id = m.get("id");
    return ((Number) id).longValue();
  }

  public boolean updateDevice(long id, String ip, String type, String community, String sshUser, String sshPwd) throws IOException {
    ApiResponse resp = connector.call("DEVICE_UPDATE", Map.of(
        "id", id,
        "ip", ip,
        "type", type,
        "community", community,
        "sshUser", sshUser,
        "sshPwd", sshPwd
    ));
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
    return Json.mapper().convertValue(resp.data(),
        Json.mapper().getTypeFactory().constructCollectionType(List.class, Alarm.class));
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
    return Json.mapper().convertValue(resp.data(),
        Json.mapper().getTypeFactory().constructCollectionType(List.class, MonitorLog.class));
  }

  public void runMonitorOnce() throws IOException {
    ApiResponse resp = connector.call("MONITOR_RUN_ONCE", Map.of());
    ensureOk(resp);
  }

  private static void ensureOk(ApiResponse resp) {
    if (resp == null) throw new IllegalStateException("No response");
    if (!resp.ok()) throw new IllegalStateException(resp.error() == null ? "Request failed" : resp.error());
  }
}

