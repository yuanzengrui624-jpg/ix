package com.netmgmt.server.socket;

import com.netmgmt.server.config.Props;
import com.netmgmt.server.monitor.MonitorScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DeviceManagementServer {
  private static final Logger log = LoggerFactory.getLogger(DeviceManagementServer.class);

  private final DataSource ds;
  private final MonitorScheduler monitorScheduler;
  private final Props props;
  private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "client-handler");
    t.setDaemon(true);
    return t;
  });

  public DeviceManagementServer(DataSource ds, MonitorScheduler monitorScheduler, Props props) {
    this.ds = ds;
    this.monitorScheduler = monitorScheduler;
    this.props = props;
  }

  public void start(int port) throws IOException {
    try (ServerSocket serverSocket = new ServerSocket(port)) {
      log.info("Listening on {}", port);
      while (true) {
        Socket socket = serverSocket.accept();
        log.info("Client connected: {}", socket.getRemoteSocketAddress());
        pool.submit(new ClientHandler(socket, ds, monitorScheduler, props));
      }
    }
  }
}

