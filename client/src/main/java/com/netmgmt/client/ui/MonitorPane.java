package com.netmgmt.client.ui;

import com.netmgmt.client.model.Device;
import com.netmgmt.client.model.MonitorLog;
import com.netmgmt.client.net.ApiClient;
import com.netmgmt.client.net.ServerConnector;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class MonitorPane extends VBox {
  private final ApiClient api;

  private final ComboBox<Device> deviceBox = new ComboBox<>();
  private final DatePicker startDate = new DatePicker(LocalDate.now().minusDays(1));
  private final DatePicker endDate = new DatePicker(LocalDate.now());
  private final ObservableList<MonitorLog> rows = FXCollections.observableArrayList();
  private final TableView<MonitorLog> table = new TableView<>(rows);

  public MonitorPane(ServerConnector connector) {
    this.api = new ApiClient(connector);
    setSpacing(10);
    setPadding(new Insets(12));

    Label tip = new Label("按设备与时间范围查询监控日志（当前版本先记录Ping结果；CPU/内存后续可接SNMP采集）。");
    tip.getStyleClass().add("muted");

    buildTable();
    HBox filters = buildFilters();

    getChildren().addAll(tip, filters, table);
    VBox.setVgrow(table, Priority.ALWAYS);

    loadDevicesAsync();
  }

  private HBox buildFilters() {
    deviceBox.setPrefWidth(360);
    deviceBox.setPromptText("选择设备");
    deviceBox.setCellFactory(cb -> new ListCell<>() {
      @Override
      protected void updateItem(Device item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? "" : (item.ip() + "  (" + item.type() + ")"));
      }
    });
    deviceBox.setButtonCell(new ListCell<>() {
      @Override
      protected void updateItem(Device item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? "" : (item.ip() + "  (" + item.type() + ")"));
      }
    });

    Button latest = new Button("查看最新一次");
    latest.setOnAction(e -> loadLatestAsync());

    Button query = new Button("查询");
    query.getStyleClass().add("primary");
    query.setOnAction(e -> queryAsync());

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    return new HBox(10,
        new Label("设备"), deviceBox,
        new Label("开始"), startDate,
        new Label("结束"), endDate,
        spacer,
        latest, query
    );
  }

  private void buildTable() {
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

    TableColumn<MonitorLog, String> time = new TableColumn<>("采集时间");
    time.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
        c.getValue().collectTime() == null ? "" : c.getValue().collectTime().toString()));

    TableColumn<MonitorLog, String> ping = new TableColumn<>("Ping");
    ping.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().pingStatus() == 1 ? "OK" : "FAIL"));

    TableColumn<MonitorLog, String> cpu = new TableColumn<>("CPU(%)");
    cpu.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().cpu() == null ? "" : String.valueOf(c.getValue().cpu())));

    TableColumn<MonitorLog, String> mem = new TableColumn<>("MEM(%)");
    mem.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().mem() == null ? "" : String.valueOf(c.getValue().mem())));

    table.getColumns().addAll(time, ping, cpu, mem);
  }

  private void loadDevicesAsync() {
    new Thread(() -> {
      try {
        List<Device> list = api.listDevices();
        Platform.runLater(() -> {
          deviceBox.getItems().setAll(list);
          if (!list.isEmpty()) deviceBox.getSelectionModel().select(0);
        });
      } catch (Exception e) {
        Platform.runLater(() -> UiUtil.error("加载设备失败", e.getMessage()));
      }
    }, "monitor-load-devices").start();
  }

  private void loadLatestAsync() {
    Device d = deviceBox.getSelectionModel().getSelectedItem();
    if (d == null) return;
    new Thread(() -> {
      try {
        MonitorLog m = api.latestMonitor(d.id());
        Platform.runLater(() -> {
          rows.clear();
          if (m != null) rows.add(m);
        });
      } catch (Exception e) {
        Platform.runLater(() -> UiUtil.error("加载最新监控失败", e.getMessage()));
      }
    }, "monitor-latest").start();
  }

  private void queryAsync() {
    Device d = deviceBox.getSelectionModel().getSelectedItem();
    if (d == null) return;
    LocalDate sd = startDate.getValue();
    LocalDate ed = endDate.getValue();
    if (sd == null || ed == null) {
      UiUtil.error("参数不完整", "请选择开始/结束日期");
      return;
    }
    LocalDateTime start = LocalDateTime.of(sd, LocalTime.MIN);
    LocalDateTime end = LocalDateTime.of(ed, LocalTime.MAX);

    new Thread(() -> {
      try {
        List<MonitorLog> list = api.queryMonitor(d.id(), start, end, 5000);
        Platform.runLater(() -> rows.setAll(list));
      } catch (Exception e) {
        Platform.runLater(() -> UiUtil.error("查询失败", e.getMessage()));
      }
    }, "monitor-query").start();
  }
}

