package com.netmgmt.client;

import com.netmgmt.client.net.ServerConnector;
import com.netmgmt.client.ui.MainView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public final class NetMgmtApp extends Application {
  @Override
  public void start(Stage stage) throws Exception {
    ServerConnector resolvedConnector = tryAutoConnect();
    if (resolvedConnector == null) {
      resolvedConnector = showConnectDialog(stage);
    }
    final ServerConnector connector = resolvedConnector;
    if (connector == null) {
      Platform.exit();
      return;
    }

    MainView root = new MainView(connector);
    double width = readDoubleProperty("netmgmt.width", 1100);
    double height = readDoubleProperty("netmgmt.height", 720);
    Scene scene = new Scene(root, width, height);
    scene.getStylesheets().add(getClass().getClassLoader().getResource("styles/app.css").toExternalForm());

    stage.setTitle("网络设备管理系统");
    stage.setScene(scene);
    stage.show();

    stage.setOnCloseRequest(e -> {
      try { connector.close(); } catch (Exception ignored) {}
    });
  }

  private ServerConnector tryAutoConnect() {
    String host = firstNonBlank(System.getProperty("netmgmt.host"), System.getenv("NETMGMT_HOST"));
    String portText = firstNonBlank(System.getProperty("netmgmt.port"), System.getenv("NETMGMT_PORT"));
    if (host == null || portText == null) return null;
    try {
      int port = Integer.parseInt(portText.trim());
      return new ServerConnector(host.trim(), port);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) return a;
    if (b != null && !b.isBlank()) return b;
    return null;
  }

  private static double readDoubleProperty(String key, double defaultValue) {
    String raw = System.getProperty(key);
    if (raw == null || raw.isBlank()) return defaultValue;
    try {
      return Double.parseDouble(raw.trim());
    } catch (NumberFormatException ignored) {
      return defaultValue;
    }
  }

  private ServerConnector showConnectDialog(Stage owner) {
    while (true) {
      Stage dlgStage = new Stage();
      dlgStage.initStyle(StageStyle.UNDECORATED);
      dlgStage.setTitle("连接服务器");

      Label title = new Label("网络设备管理系统");
      title.setStyle("-fx-text-fill: #1e293b; -fx-font-size: 20px; -fx-font-weight: 700;");

      Label subtitle = new Label("Network Device Management System");
      subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-letter-spacing: 1px;");

      Label desc = new Label("请输入服务端地址进行连接，默认连接本机 (127.0.0.1:8888)");
      desc.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
      desc.setWrapText(true);

      VBox header = new VBox(4, title, subtitle);
      header.setAlignment(Pos.CENTER);
      header.setPadding(new Insets(28, 0, 8, 0));

      VBox descBox = new VBox(desc);
      descBox.setAlignment(Pos.CENTER);
      descBox.setPadding(new Insets(0, 20, 12, 20));

      Label hostLabel = new Label("服务器地址");
      hostLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px; -fx-font-weight: 600;");
      TextField hostField = new TextField("127.0.0.1");
      hostField.setPromptText("例如 192.168.1.100");
      hostField.setStyle("-fx-background-color: #f8fafc; -fx-text-fill: #1e293b; "
          + "-fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-background-radius: 8; "
          + "-fx-padding: 8 12; -fx-font-size: 13px;");

      Label portLabel = new Label("端口号");
      portLabel.setStyle("-fx-text-fill: #334155; -fx-font-size: 12px; -fx-font-weight: 600;");
      TextField portField = new TextField("8888");
      portField.setPromptText("默认 8888");
      portField.setStyle(hostField.getStyle());

      Label statusLabel = new Label("");
      statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 11px;");
      statusLabel.setWrapText(true);
      statusLabel.setVisible(false);
      statusLabel.setManaged(false);

      VBox form = new VBox(6, hostLabel, hostField, portLabel, portField, statusLabel);
      form.setPadding(new Insets(0, 28, 0, 28));

      Button connectBtn = new Button("连  接");
      connectBtn.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; "
          + "-fx-font-size: 14px; -fx-font-weight: 700; -fx-padding: 10 0; "
          + "-fx-background-radius: 10; -fx-cursor: hand;");
      connectBtn.setMaxWidth(Double.MAX_VALUE);

      Button cancelBtn = new Button("退  出");
      cancelBtn.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #64748b; "
          + "-fx-font-size: 13px; -fx-font-weight: 600; -fx-padding: 8 0; "
          + "-fx-background-radius: 10; -fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-cursor: hand;");
      cancelBtn.setMaxWidth(Double.MAX_VALUE);

      VBox buttons = new VBox(8, connectBtn, cancelBtn);
      buttons.setPadding(new Insets(16, 28, 24, 28));

      VBox root = new VBox(header, descBox, form, buttons);
      root.setStyle("-fx-background-color: white; -fx-border-color: #e2e8f0; "
          + "-fx-border-radius: 16; -fx-background-radius: 16; -fx-border-width: 1; "
          + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 20, 0, 0, 4);");
      root.setPrefWidth(380);

      Scene scene = new Scene(root);
      scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
      dlgStage.initStyle(StageStyle.TRANSPARENT);
      dlgStage.setScene(scene);

      final ServerConnector[] result = {null};
      final boolean[] cancelled = {false};

      connectBtn.setOnAction(e -> {
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();

        if (host.isEmpty()) {
          statusLabel.setText("请输入服务器地址");
          statusLabel.setVisible(true);
          statusLabel.setManaged(true);
          return;
        }

        int port;
        try {
          port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
          statusLabel.setText("端口必须是数字，例如 8888");
          statusLabel.setVisible(true);
          statusLabel.setManaged(true);
          return;
        }

        connectBtn.setText("连接中...");
        connectBtn.setDisable(true);
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);

        new Thread(() -> {
          ServerConnector conn = null;
          try {
            conn = new ServerConnector(host, port);
            ServerConnector finalConn = conn;
            Platform.runLater(() -> {
              result[0] = finalConn;
              dlgStage.close();
            });
            conn = null;
          } catch (Exception ex) {
            Platform.runLater(() -> {
              statusLabel.setText("连接失败: " + ex.getMessage());
              statusLabel.setVisible(true);
              statusLabel.setManaged(true);
              connectBtn.setText("重新连接");
              connectBtn.setDisable(false);
            });
          } finally {
            if (conn != null) {
              try { conn.close(); } catch (Exception ignored) {}
            }
          }
        }, "connect-thread").start();
      });

      cancelBtn.setOnAction(e -> {
        cancelled[0] = true;
        dlgStage.close();
      });

      hostField.setOnAction(e -> connectBtn.fire());
      portField.setOnAction(e -> connectBtn.fire());

      dlgStage.showAndWait();

      if (cancelled[0]) return null;
      if (result[0] != null) return result[0];
    }
  }
}

