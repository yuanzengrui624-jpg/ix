package com.netmgmt.client.net;

import com.netmgmt.common.json.Json;
import com.netmgmt.common.protocol.ApiRequest;
import com.netmgmt.common.protocol.ApiResponse;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

public final class ServerConnector implements Closeable {
  private final Socket socket;
  private final BufferedReader in;
  private final BufferedWriter out;

  public ServerConnector(String host, int port) throws IOException {
    this.socket = new Socket(host, port);
    this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
  }

  public synchronized ApiResponse call(String action, Map<String, Object> data) throws IOException {
    String id = UUID.randomUUID().toString();
    ApiRequest req = new ApiRequest(id, action, data);
    out.write(Json.toJson(req));
    out.write("\n");
    out.flush();

    String line = in.readLine();
    if (line == null) throw new EOFException("Server closed connection");
    try {
      ApiResponse resp = Json.mapper().readValue(line, ApiResponse.class);
      if (resp.id() != null && !id.equals(resp.id())) {
        return ApiResponse.fail(id, "Mismatched response id");
      }
      return resp;
    } catch (Exception e) {
      return ApiResponse.fail(id, "Bad response JSON: " + e.getMessage());
    }
  }

  @Override
  public void close() throws IOException {
    try {
      out.close();
    } finally {
      try {
        in.close();
      } finally {
        socket.close();
      }
    }
  }
}

