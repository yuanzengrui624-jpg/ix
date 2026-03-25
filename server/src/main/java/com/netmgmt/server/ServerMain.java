package com.netmgmt.server;

import com.netmgmt.server.config.Props;
import com.netmgmt.server.db.DataSourceFactory;
import com.netmgmt.server.monitor.MonitorScheduler;
import com.netmgmt.server.socket.DeviceManagementServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

public final class ServerMain {
  private static final Logger log = LoggerFactory.getLogger(ServerMain.class);

  public static void main(String[] args) throws Exception {
    Props props = Props.load();
    DataSource ds = DataSourceFactory.create(props);

    var monitorScheduler = new MonitorScheduler(ds, props);
    monitorScheduler.start();

    int port = props.getInt("server.port");
    log.info("Starting server on port {}", port);
    new DeviceManagementServer(ds, monitorScheduler, props).start(port);
  }
}

