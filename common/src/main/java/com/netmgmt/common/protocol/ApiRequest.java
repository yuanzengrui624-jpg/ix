package com.netmgmt.common.protocol;

import java.util.Map;

public record ApiRequest(
    String id,
    String action,
    Map<String, Object> data
) {}

