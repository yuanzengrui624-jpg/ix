package com.netmgmt.client.ui;

import com.netmgmt.client.model.Alarm;
import com.netmgmt.client.model.Device;
import com.netmgmt.client.net.ApiClient;
import com.netmgmt.client.net.ServerConnector;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AlarmPane extends VBox {
  private final ApiClient api;
  private final ObservableList<Alarm> pageRows = FXCollections.observableArrayList();
  private final TableView<Alarm> table = new TableView<>(pageRows);
  private volatile Map<Long, String> deviceIpMap = new HashMap<>();

  private List<Alarm> allAlarms = new ArrayList<>();
  private static final int PAGE_SIZE = 15;
  private int currentPage = 0;

  private final Label pageInfo = new Label("第 1 页 / 共 1 页");
  private final Button prevBtn = new Button("上一页");
  private final Button nextBtn = new Button("下一页");
  private final Timeline autoRefresh = new Timeline();
  private volatile boolean refreshing;

  public AlarmPane(ServerConnector connector) {
    this.api = new ApiClient(connector);
    setSpacing(10);
    setPadding(new Insets(12));

    Label tip = new Label("查看系统告警并进行确认处理，页面每 30 秒自动刷新一次。");
    tip.getStyleClass().add("muted");

    buildTable();
    HBox actions = buildActions();
    HBox pager = buildPager();

    getChildren().addAll(tip, actions, table, pager);
    VBox.setVgrow(table, Priority.ALWAYS);

    autoRefresh.getKeyFrames().setAll(new KeyFrame(Duration.seconds(30), e -> refreshAsync()));
    autoRefresh.setCycleCount(Timeline.INDEFINITE);
    autoRefresh.play();
    refreshAsync();
  }

  private HBox buildActions() {
    Button refresh = new Button("刷新");
    refresh.getStyleClass().add("primary");
    refresh.setOnAction(e -> {
      refresh.setText("刷新中...");
      refresh.setDisable(true);
      refreshAsync();
      new Thread(() -> { try { Thread.sleep(500); } catch (Exception ignored) {}
        Platform.runLater(() -> { refresh.setText("刷新"); refresh.setDisable(false); });
      }).start();
    });

    Button ack = new Button("确认选中告警");
    ack.setOnAction(e -> {
      Alarm a = table.getSelectionModel().getSelectedItem();
      if (a == null) return;
      if (a.acknowledged() || a.recovered()) return;
      new Thread(() -> {
        try {
          api.ackAlarm(a.id());
          refreshAsync();
        } catch (Exception ex) {
          Platform.runLater(() -> UiUtil.error("确认失败", ex.getMessage()));
        }
      }, "alarm-ack").start();
    });

    Button export = new Button("导出 CSV");
    export.setOnAction(e -> exportCsv());

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    return new HBox(10, refresh, ack, spacer, export);
  }

  private void exportCsv() {
    if (allAlarms.isEmpty()) { UiUtil.error("无数据", "没有告警数据可导出"); return; }
    String[] headers = {"序号", "时间", "设备IP", "类型", "内容", "状态", "处理时间"};
    java.util.List<String[]> rows = new java.util.ArrayList<>();
    for (int i = 0; i < allAlarms.size(); i++) {
      Alarm a = allAlarms.get(i);
      rows.add(new String[]{
          String.valueOf(i + 1),
          a.createTime() == null ? "" : a.createTime().toString(),
          deviceIpMap.getOrDefault(a.deviceId(), String.valueOf(a.deviceId())),
          alarmTypeLabel(a.type()),
          a.content(),
          alarmStatusText(a),
          handledTimeText(a)
      });
    }
    javafx.stage.Window owner = getScene() != null ? getScene().getWindow() : null;
    CsvExporter.export(owner, "告警记录.csv", headers, rows);
  }

  private HBox buildPager() {
    prevBtn.setOnAction(e -> { if (currentPage > 0) { currentPage--; showPage(); } });
    nextBtn.setOnAction(e -> { if ((currentPage + 1) * PAGE_SIZE < allAlarms.size()) { currentPage++; showPage(); } });
    pageInfo.getStyleClass().add("muted");

    HBox pager = new HBox(12, prevBtn, pageInfo, nextBtn);
    pager.setAlignment(Pos.CENTER);
    pager.setPadding(new Insets(4, 0, 4, 0));
    return pager;
  }

  private void showPage() {
    int totalPages = Math.max(1, (int) Math.ceil((double) allAlarms.size() / PAGE_SIZE));
    int from = currentPage * PAGE_SIZE;
    int to = Math.min(from + PAGE_SIZE, allAlarms.size());
    pageRows.setAll(allAlarms.subList(from, to));
    table.scrollTo(0);
    pageInfo.setText("第 " + (currentPage + 1) + " 页 / 共 " + totalPages + " 页（共 " + allAlarms.size() + " 条）");
    prevBtn.setDisable(currentPage == 0);
    nextBtn.setDisable(currentPage >= totalPages - 1);
  }

  private void buildTable() {
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

    TableColumn<Alarm, String> idx = new TableColumn<>("序号");
    idx.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty ? null : String.valueOf(currentPage * PAGE_SIZE + getIndex() + 1));
      }
    });
    idx.setPrefWidth(50);
    idx.setMaxWidth(60);
    idx.setSortable(false);

    TableColumn<Alarm, String> time = new TableColumn<>("时间");
    time.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
        c.getValue().createTime() == null ? "" : c.getValue().createTime().toString()));

    TableColumn<Alarm, String> deviceId = new TableColumn<>("设备IP");
    deviceId.setCellValueFactory(c -> {
      long did = c.getValue().deviceId();
      String ip = deviceIpMap.getOrDefault(did, String.valueOf(did));
      return new javafx.beans.property.SimpleStringProperty(ip);
    });

    TableColumn<Alarm, String> type = new TableColumn<>("类型");
    type.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(alarmTypeLabel(c.getValue().type())));
    type.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) { setText(null); setGraphic(null); return; }
        Label lbl = new Label(item);
        lbl.setStyle("-fx-text-fill: #f59e0b; -fx-font-weight: 600;");
        setGraphic(lbl);
        setText(null);
      }
    });

    TableColumn<Alarm, String> content = new TableColumn<>("内容");
    content.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().content()));

    TableColumn<Alarm, String> ack = new TableColumn<>("状态");
    ack.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(alarmStatusText(c.getValue())));
    ack.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) { setText(null); setGraphic(null); return; }
        Label badge = new Label(item);
        if ("已恢复".equals(item)) badge.getStyleClass().add("chip-info");
        else badge.getStyleClass().add("已确认".equals(item) ? "chip-ok" : "chip-muted");
        setGraphic(badge);
        setText(null);
      }
    });

    TableColumn<Alarm, String> handledTime = new TableColumn<>("处理时间");
    handledTime.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(handledTimeText(c.getValue())));

    table.getColumns().setAll(java.util.List.of(idx, time, deviceId, type, content, ack, handledTime));
  }

  public void refresh() {
    refreshAsync();
  }

  private static String alarmTypeLabel(String raw) {
    if (raw == null) return "";
    return switch (raw) {
      case "OFFLINE" -> "设备离线";
      case "CPU_HIGH" -> "CPU过高";
      case "MEM_HIGH" -> "内存过高";
      default -> raw;
    };
  }

  private static String alarmStatusText(Alarm alarm) {
    if (alarm == null) return "";
    if (alarm.recovered()) return "已恢复";
    return alarm.acknowledged() ? "已确认" : "未确认";
  }

  private static String handledTimeText(Alarm alarm) {
    if (alarm == null) return "";
    if (alarm.recovered()) {
      return alarm.recoverTime() == null ? "" : alarm.recoverTime().toString();
    }
    return alarm.ackTime() == null ? "" : alarm.ackTime().toString();
  }

  private void refreshAsync() {
    if (refreshing) return;
    refreshing = true;
    new Thread(() -> {
      try {
        List<Device> devices = api.listDevices();
        Map<Long, String> ipMap = new HashMap<>();
        for (Device d : devices) ipMap.put(d.id(), d.ip());
        deviceIpMap = ipMap;

        List<Alarm> list = api.listAlarms(1000);
        Platform.runLater(() -> {
          allAlarms = list;
          currentPage = 0;
          showPage();
        });
      } catch (Exception e) {
        Platform.runLater(() -> UiUtil.error("加载告警失败", e.getMessage()));
      } finally {
        refreshing = false;
      }
    }, "alarm-refresh").start();
  }
}
