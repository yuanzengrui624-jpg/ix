package com.netmgmt.server.monitor;

import com.netmgmt.common.json.Json;
import com.netmgmt.server.config.Props;
import com.netmgmt.server.model.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.Variable;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class SnmpCollector implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(SnmpCollector.class);

  private final boolean enabled;
  private final int port;
  private final int timeoutMs;
  private final int retries;
  private final String cpuOid;
  private final String memUsageOid;
  private final String memUsedOid;
  private final String memTotalOid;
  private final String memAvailableOid;
  private final String ifNameOid;
  private final String ifDescrOid;
  private final String ifStatusOid;
  private final String ifSpeedOid;

  private final TransportMapping<UdpAddress> transport;
  private final Snmp snmp;
  private final TreeUtils treeUtils;

  public SnmpCollector(Props props) {
    try {
      this.enabled = Boolean.parseBoolean(props.getString("snmp.enabled", "true"));
      this.port = props.getInt("snmp.port", 161);
      this.timeoutMs = props.getInt("snmp.timeoutMs", 2000);
      this.retries = props.getInt("snmp.retries", 1);
      this.cpuOid = trimToNull(props.getString("snmp.cpuOid", ""));
      this.memUsageOid = trimToNull(props.getString("snmp.memUsageOid", ""));
      this.memUsedOid = trimToNull(props.getString("snmp.memUsedOid", ""));
      this.memTotalOid = trimToNull(props.getString("snmp.memTotalOid", ""));
      this.memAvailableOid = trimToNull(props.getString("snmp.memAvailableOid", ""));
      this.ifNameOid = trimToNull(props.getString("snmp.interfaceNameOid", ""));
      this.ifDescrOid = trimToNull(props.getString("snmp.interfaceDescrOid", ""));
      this.ifStatusOid = trimToNull(props.getString("snmp.interfaceStatusOid", ""));
      this.ifSpeedOid = trimToNull(props.getString("snmp.interfaceSpeedOid", ""));

      this.transport = new DefaultUdpTransportMapping();
      this.snmp = new Snmp(transport);
      this.transport.listen();
      this.treeUtils = new TreeUtils(snmp, new DefaultPDUFactory(PDU.GETNEXT));
    } catch (IOException e) {
      throw new IllegalStateException("SNMP collector init failed", e);
    }
  }

  public SnmpSample collect(Device device) {
    if (!enabled) return new SnmpSample(null, null, null, false, "SNMP disabled");
    if (device.community() == null || device.community().isBlank()) {
      return new SnmpSample(null, null, null, false, "设备未配置 SNMP community");
    }

    try {
      CommunityTarget<UdpAddress> target = buildTarget(device);
      if (!probeTarget(target)) {
        return new SnmpSample(null, null, null, false, "SNMP 无响应或 community 不正确");
      }
      Double cpu = readMetric(target, cpuOid);
      Double mem = readMemoryUsage(target);
      String interfaceJson = readInterfaces(target);
      boolean success = cpu != null || mem != null || interfaceJson != null;
      return new SnmpSample(cpu, mem, interfaceJson, success, success ? null : "未采集到任何 SNMP 指标");
    } catch (Exception e) {
      log.debug("SNMP collect failed for {}", device.ip(), e);
      return new SnmpSample(null, null, null, false, e.getMessage());
    }
  }

  private CommunityTarget<UdpAddress> buildTarget(Device device) {
    CommunityTarget<UdpAddress> target = new CommunityTarget<>();
    target.setCommunity(new OctetString(device.community()));
    target.setVersion(SnmpConstants.version2c);
    target.setAddress(new UdpAddress(device.ip() + "/" + port));
    target.setTimeout(timeoutMs);
    target.setRetries(retries);
    return target;
  }

  private boolean probeTarget(CommunityTarget<UdpAddress> target) throws IOException {
    PDU pdu = new PDU();
    pdu.setType(PDU.GET);
    pdu.add(new VariableBinding(new OID("1.3.6.1.2.1.1.1.0")));
    ResponseEvent<UdpAddress> event = snmp.send(pdu, target);
    return event != null && event.getResponse() != null && event.getResponse().size() > 0;
  }

  private Double readMemoryUsage(CommunityTarget<UdpAddress> target) throws IOException {
    Double direct = readMetric(target, memUsageOid);
    if (direct != null) return normalizePercent(direct);

    Double used = readMetric(target, memUsedOid);
    Double total = readMetric(target, memTotalOid);
    if (used != null && total != null && total > 0) {
      return normalizePercent((used / total) * 100.0);
    }

    Double available = readMetric(target, memAvailableOid);
    if (available != null && total != null && total > 0) {
      return normalizePercent(((total - available) / total) * 100.0);
    }
    return null;
  }

  private Double readMetric(CommunityTarget<UdpAddress> target, String oidText) throws IOException {
    if (oidText == null || oidText.isBlank()) return null;
    return oidText.endsWith(".0") ? readScalarDouble(target, oidText) : readWalkAverage(target, oidText);
  }

  private Double readScalarDouble(CommunityTarget<UdpAddress> target, String oidText) throws IOException {
    PDU pdu = new PDU();
    pdu.setType(PDU.GET);
    pdu.add(new VariableBinding(new OID(oidText)));
    ResponseEvent<UdpAddress> event = snmp.send(pdu, target);
    if (event == null || event.getResponse() == null || event.getResponse().size() == 0) return null;
    VariableBinding vb = event.getResponse().get(0);
    if (vb == null || vb.getVariable() == null) return null;
    return variableToDouble(vb.getVariable());
  }

  private Double readWalkAverage(CommunityTarget<UdpAddress> target, String oidText) {
    Map<String, Variable> rows = walk(target, oidText);
    if (rows.isEmpty()) return null;
    double total = 0.0;
    int count = 0;
    for (Variable variable : rows.values()) {
      Double value = variableToDouble(variable);
      if (value != null) {
        total += value;
        count++;
      }
    }
    return count == 0 ? null : normalizePercent(total / count);
  }

  private String readInterfaces(CommunityTarget<UdpAddress> target) {
    Map<String, Variable> names = walk(target, ifNameOid);
    if (names.isEmpty()) {
      names = walk(target, ifDescrOid);
    }
    Map<String, Variable> statuses = walk(target, ifStatusOid);
    Map<String, Variable> speeds = walk(target, ifSpeedOid);
    if (names.isEmpty() && statuses.isEmpty()) return null;

    LinkedHashSet<String> indexes = new LinkedHashSet<>();
    indexes.addAll(names.keySet());
    indexes.addAll(statuses.keySet());
    indexes.addAll(speeds.keySet());

    List<Map<String, Object>> rows = new ArrayList<>();
    for (String index : indexes) {
      String name = variableToText(names.get(index));
      if (name == null || name.isBlank()) name = "ifIndex-" + index;
      String status = normalizeInterfaceStatus(variableToDouble(statuses.get(index)));
      long speed = toSpeed(speeds.get(index));

      Map<String, Object> row = new LinkedHashMap<>();
      row.put("name", name);
      row.put("status", status);
      row.put("speed", speed);
      rows.add(row);
    }
    return rows.isEmpty() ? null : Json.toJson(rows);
  }

  private Map<String, Variable> walk(CommunityTarget<UdpAddress> target, String oidText) {
    Map<String, Variable> out = new LinkedHashMap<>();
    if (oidText == null || oidText.isBlank()) return out;

    OID root = new OID(oidText);
    List<TreeEvent> events = treeUtils.getSubtree(target, root);
    if (events == null) return out;

    String rootText = root.toDottedString();
    for (TreeEvent event : events) {
      if (event == null || event.isError()) continue;
      VariableBinding[] bindings = event.getVariableBindings();
      if (bindings == null) continue;
      for (VariableBinding binding : bindings) {
        if (binding == null || binding.getOid() == null || binding.getVariable() == null) continue;
        String full = binding.getOid().toDottedString();
        if (!full.startsWith(rootText)) continue;
        String suffix = full.substring(rootText.length());
        if (suffix.startsWith(".")) suffix = suffix.substring(1);
        out.put(suffix, binding.getVariable());
      }
    }
    return out;
  }

  private static Double variableToDouble(Variable variable) {
    if (variable == null) return null;
    try {
      return Double.parseDouble(variable.toString());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String variableToText(Variable variable) {
    return variable == null ? null : variable.toString();
  }

  private static long toSpeed(Variable variable) {
    Double speed = variableToDouble(variable);
    return speed == null ? 0L : Math.round(speed / 1_000_000.0);
  }

  private static String normalizeInterfaceStatus(Double statusValue) {
    if (statusValue == null) return "UNKNOWN";
    int status = statusValue.intValue();
    return status == 1 ? "UP" : "DOWN";
  }

  private static Double normalizePercent(Double value) {
    if (value == null) return null;
    double normalized = Math.max(0.0, Math.min(100.0, value));
    return Math.round(normalized * 100.0) / 100.0;
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  @Override
  public void close() throws IOException {
    snmp.close();
    transport.close();
  }
}
