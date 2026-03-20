package com.netmgmt.server.model;

import java.time.LocalDateTime;

public record MonitorLog(
    long id,
    long deviceId,
    Double cpu,
    Double mem,
    int pingStatus,
    String interfaceStatusJson,
    LocalDateTime collectTime
) {}

