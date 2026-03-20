package com.netmgmt.server.model;

import java.time.LocalDateTime;

public record Alarm(
    long id,
    long deviceId,
    String type,
    String content,
    LocalDateTime createTime,
    boolean acknowledged,
    LocalDateTime ackTime
) {}

