package com.netmgmt.client.model;

import java.time.LocalDateTime;

public record Device(
    long id,
    String ip,
    String type,
    String community,
    String sshUser,
    String sshPwd,
    int status,
    LocalDateTime lastUpdate,
    LocalDateTime createTime
) {}

