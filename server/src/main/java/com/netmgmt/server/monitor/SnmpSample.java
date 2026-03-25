package com.netmgmt.server.monitor;

public record SnmpSample(
    Double cpu,
    Double mem,
    String interfaceStatusJson,
    boolean success,
    String errorMessage
) {}
