package com.netmgmt.server.monitor;

import com.netmgmt.common.json.Json;

import java.lang.management.ManagementFactory;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LocalHostMetricCollector {
  private LocalHostMetricCollector() {}

  static Snapshot collect(Double currentCpu, Double currentMem, String currentInterfaceJson) {
    Double cpu = currentCpu != null ? currentCpu : readCpuPercent();
    Double mem = currentMem != null ? currentMem : readMemPercent();
    String interfaceJson = (currentInterfaceJson != null && !currentInterfaceJson.isBlank())
        ? currentInterfaceJson
        : readInterfaces();

    boolean filled = currentCpu == null && cpu != null
        || currentMem == null && mem != null
        || (currentInterfaceJson == null || currentInterfaceJson.isBlank()) && interfaceJson != null;

    String message = filled ? "已通过本机 JVM 补采到 CPU/内存/接口状态" : null;
    return new Snapshot(cpu, mem, interfaceJson, filled, message);
  }

  private static Double readCpuPercent() {
    try {
      java.lang.management.OperatingSystemMXBean rawBean = ManagementFactory.getOperatingSystemMXBean();
      if (rawBean instanceof com.sun.management.OperatingSystemMXBean osBean) {
        double load = osBean.getCpuLoad();
        if (load >= 0.0) {
          return roundPercent(load * 100.0);
        }
        double processLoad = osBean.getProcessCpuLoad();
        if (processLoad >= 0.0) {
          return roundPercent(processLoad * 100.0);
        }
      }

      double systemLoadAverage = rawBean.getSystemLoadAverage();
      int processors = rawBean.getAvailableProcessors();
      if (systemLoadAverage >= 0.0 && processors > 0) {
        return roundPercent((systemLoadAverage / processors) * 100.0);
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private static Double readMemPercent() {
    try {
      java.lang.management.OperatingSystemMXBean rawBean = ManagementFactory.getOperatingSystemMXBean();
      if (rawBean instanceof com.sun.management.OperatingSystemMXBean osBean) {
        long total = osBean.getTotalMemorySize();
        long free = osBean.getFreeMemorySize();
        if (total > 0L && free >= 0L && free <= total) {
          return roundPercent(((double) (total - free) * 100.0) / total);
        }
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  private static String readInterfaces() {
    try {
      Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
      if (enumeration == null) return null;

      List<NetworkInterface> interfaces = new ArrayList<>();
      while (enumeration.hasMoreElements()) {
        interfaces.add(enumeration.nextElement());
      }
      interfaces.sort(Comparator.comparing(NetworkInterface::getName));

      List<Map<String, Object>> rows = new ArrayList<>();
      for (NetworkInterface nif : interfaces) {
        if (nif == null) continue;
        if (nif.getName() == null || nif.getName().isBlank()) continue;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", nif.getDisplayName() == null || nif.getDisplayName().isBlank() ? nif.getName() : nif.getDisplayName());
        row.put("status", nif.isUp() ? "UP" : "DOWN");
        row.put("speed", 0L);
        rows.add(row);
      }
      return rows.isEmpty() ? null : Json.toJson(rows);
    } catch (SocketException e) {
      return null;
    }
  }

  private static Double roundPercent(double value) {
    double normalized = Math.max(0.0, Math.min(100.0, value));
    return Math.round(normalized * 100.0) / 100.0;
  }

  record Snapshot(
      Double cpu,
      Double mem,
      String interfaceJson,
      boolean filled,
      String message
  ) {}
}
