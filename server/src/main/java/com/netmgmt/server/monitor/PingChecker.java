package com.netmgmt.server.monitor;

import java.net.InetAddress;

public final class PingChecker {
  private PingChecker() {}

  public static boolean ping(String ip, int timeoutMs) {
    if (isLoopbackDemoAddress(ip)) {
      return true;
    }
    try {
      InetAddress addr = InetAddress.getByName(ip);
      return addr.isReachable(timeoutMs);
    } catch (Exception e) {
      return false;
    }
  }

  private static boolean isLoopbackDemoAddress(String ip) {
    if (ip == null) return false;
    String normalized = ip.trim().toLowerCase();
    return normalized.equals("localhost") || normalized.equals("::1") || normalized.startsWith("127.");
  }
}

