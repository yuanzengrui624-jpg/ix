package com.netmgmt.server.model;

import java.time.LocalDateTime;

public record ConfigBackup(
    long id,
    long deviceId,
    String content,
    LocalDateTime backupTime
) {}
