package com.netmgmt.server.monitor;

import java.net.InetAddress;

public final class PingChecker {
  private PingChecker() {}

  public static boolean ping(String ip, int timeoutMs) {
    try {
      InetAddress addr = InetAddress.getByName(ip);
      return addr.isReachable(timeoutMs);
    } catch (Exception e) {
      return false;
    }
  }
}

