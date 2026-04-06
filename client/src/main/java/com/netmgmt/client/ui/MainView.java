package com.netmgmt.client.ui;

import com.netmgmt.client.net.ServerConnector;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class MainView extends BorderPane {
  public MainView(ServerConnector connector) {
    getStyleClass().add("app-shell");

    setTop(buildHeader());

    DashboardPane dashboard = new DashboardPane(connector);
    DevicePane devices = new DevicePane(connector);
    AlarmPane alarms = new AlarmPane(connector);
    MonitorPane monitor = new MonitorPane(connector);
    ConfigBackupPane configBackup = new ConfigBackupPane(connector);

    TabPane tabs = new TabPane();
    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    tabs.getTabs().add(new Tab("系统概览", dashboard));
    tabs.getTabs().add(new Tab("设备管理", devices));
    tabs.getTabs().add(new Tab("告警中心", alarms));
    tabs.getTabs().add(new Tab("监控日志", monitor));
    tabs.getTabs().add(new Tab("配置备份", configBackup));

    tabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
      if (newTab == null) return;
      var content = newTab.getContent();
      if (content instanceof DashboardPane p) p.refresh();
      else if (content instanceof DevicePane p) p.refreshAsync();
      else if (content instanceof AlarmPane p) p.refresh();
      else if (content instanceof MonitorPane p) p.reloadDevices();
      else if (content instanceof ConfigBackupPane p) p.refresh();
    });

    applyInitialTabSelection(tabs);

    setCenter(tabs);

    BorderPane.setMargin(tabs, new Insets(12, 12, 12, 12));
  }

  private static VBox buildHeader() {
    Label title = new Label("网络设备管理系统");
    title.getStyleClass().add("title");

    Label subtitle = new Label("Java 17 · MySQL · C/S Socket · 高对比浅色界面");
    subtitle.getStyleClass().addAll("subtitle", "muted");

    VBox box = new VBox(2, title, subtitle);
    HBox wrap = new HBox(box);
    wrap.getStyleClass().add("header");
    return new VBox(wrap);
  }

  private static void applyInitialTabSelection(TabPane tabs) {
    String raw = System.getProperty("netmgmt.startTab", "").trim().toLowerCase();
    if (raw.isEmpty()) return;

    int index = switch (raw) {
      case "0", "dashboard", "overview", "系统概览" -> 0;
      case "1", "devices", "device", "设备管理" -> 1;
      case "2", "alarms", "alarm", "告警中心" -> 2;
      case "3", "monitor", "监控日志" -> 3;
      case "4", "config", "backup", "配置备份" -> 4;
      default -> -1;
    };
    if (index >= 0 && index < tabs.getTabs().size()) {
      Platform.runLater(() -> tabs.getSelectionModel().select(index));
    }
  }
}

