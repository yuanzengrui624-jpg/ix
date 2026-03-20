package com.netmgmt.client.ui;

import com.netmgmt.client.model.Alarm;
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

import java.util.List;

public final class AlarmPane extends VBox {
  private final ApiClient api;
  private final ObservableList<Alarm> rows = FXCollections.observableArrayList();
  private final TableView<Alarm> table = new TableView<>(rows);

  public AlarmPane(ServerConnector connector) {
    this.api = new ApiClient(connector);
    setSpacing(10);
    setPadding(new Insets(12));

    Label tip = new Label("查看告警并进行确认（acknowledged）。");
    tip.getStyleClass().add("muted");

    buildTable();
    HBox actions = buildActions();

    getChildren().addAll(tip, actions, table);
    VBox.setVgrow(table, Priority.ALWAYS);

    refreshAsync();
  }

  private HBox buildActions() {
    Button refresh = new Button("刷新");
    refresh.getStyleClass().add("primary");
    refresh.setOnAction(e -> refreshAsync());

    Button ack = new Button("确认选中告警");
    ack.setOnAction(e -> {
      Alarm a = table.getSelectionModel().getSelectedItem();
      if (a == null) return;
      if (a.acknowledged()) return;
      new Thread(() -> {
        try {
          api.ackAlarm(a.id());
          refreshAsync();
        } catch (Exception ex) {
          Platform.runLater(() -> UiUtil.error("确认失败", ex.getMessage()));
        }
      }, "alarm-ack").start();
    });

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    HBox box = new HBox(10, refresh, ack, spacer);
    return box;
  }

  private void buildTable() {
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

    TableColumn<Alarm, String> time = new TableColumn<>("时间");
    time.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
        c.getValue().createTime() == null ? "" : c.getValue().createTime().toString()));

    TableColumn<Alarm, String> deviceId = new TableColumn<>("设备ID");
    deviceId.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().deviceId())));

    TableColumn<Alarm, String> type = new TableColumn<>("类型");
    type.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().type()));

    TableColumn<Alarm, String> content = new TableColumn<>("内容");
    content.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().content()));

    TableColumn<Alarm, String> ack = new TableColumn<>("确认");
    ack.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().acknowledged() ? "已确认" : "未确认"));

    table.getColumns().addAll(time, deviceId, type, content, ack);
  }

  private void refreshAsync() {
    new Thread(() -> {
      try {
        List<Alarm> list = api.listAlarms(300);
        Platform.runLater(() -> rows.setAll(list));
      } catch (Exception e) {
        Platform.runLater(() -> UiUtil.error("加载告警失败", e.getMessage()));
      }
    }, "alarm-refresh").start();
  }
}

