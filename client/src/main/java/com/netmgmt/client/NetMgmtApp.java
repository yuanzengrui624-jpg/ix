package com.netmgmt.client;

import com.netmgmt.client.net.ServerConnector;
import com.netmgmt.client.ui.MainView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class NetMgmtApp extends Application {
  @Override
  public void start(Stage stage) throws Exception {
    // 默认连接本机服务端；后续可做成设置项
    ServerConnector connector = new ServerConnector("127.0.0.1", 8888);

    MainView root = new MainView(connector);
    Scene scene = new Scene(root, 1100, 720);
    scene.getStylesheets().add(getClass().getClassLoader().getResource("styles/app.css").toExternalForm());

    stage.setTitle("网络设备管理系统");
    stage.setScene(scene);
    stage.show();

    stage.setOnCloseRequest(e -> {
      try { connector.close(); } catch (Exception ignored) {}
    });
  }
}

