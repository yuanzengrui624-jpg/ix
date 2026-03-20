package com.netmgmt.client.ui;

import com.netmgmt.client.net.ServerConnector;
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

    TabPane tabs = new TabPane();
    tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
    tabs.getTabs().add(new Tab("设备管理", new DevicePane(connector)));
    tabs.getTabs().add(new Tab("告警中心", new AlarmPane(connector)));
    tabs.getTabs().add(new Tab("监控日志", new MonitorPane(connector)));
    setCenter(tabs);

    BorderPane.setMargin(tabs, new Insets(12, 12, 12, 12));
  }

  private static VBox buildHeader() {
    Label title = new Label("网络设备管理系统");
    title.getStyleClass().add("title");

    Label subtitle = new Label("Java 17 · MySQL · C/S Socket · 专业蓝灰主题");
    subtitle.getStyleClass().addAll("subtitle", "muted");

    VBox box = new VBox(2, title, subtitle);
    HBox wrap = new HBox(box);
    wrap.getStyleClass().add("header");
    return new VBox(wrap);
  }
}

