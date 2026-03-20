package com.netmgmt.common.protocol;

public record ApiResponse(
    String id,
    boolean ok,
    Object data,
    String error
) {
  public static ApiResponse ok(String id, Object data) {
    return new ApiResponse(id, true, data, null);
  }

  public static ApiResponse fail(String id, String error) {
    return new ApiResponse(id, false, null, error);
  }
}

