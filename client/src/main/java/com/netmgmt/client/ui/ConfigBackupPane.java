package com.netmgmt.client.ui;

import com.netmgmt.client.model.ConfigBackup;
import com.netmgmt.client.model.Device;
import com.netmgmt.client.net.ApiClient;
import com.netmgmt.client.net.ServerConnector;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ConfigBackupPane extends VBox {
  private final ApiClient api;
  private final ObservableList<ConfigBackup> pageRows = FXCollections.observableArrayList();
  private final TableView<ConfigBackup> table = new TableView<>(pageRows);
  private final TextArea configArea = new TextArea();
  private volatile Map<Long, String> deviceIpMap = new HashMap<>();

  private List<ConfigBackup> allData = new ArrayList<>();
  private static final int PAGE_SIZE = 15;
  private int currentPage = 0;
  private final Label pageInfo = new Label("第 1 页 / 共 1 页");
  private final Button prevBtn = new Button("上一页");
  private final Button nextBtn = new Button("下一页");
  private final boolean autoOpenDiff = Boolean.getBoolean("netmgmt.demoDiff");
  private boolean diffShown;

  private final ComboBox<Device> deviceBox = new ComboBox<>();

  public ConfigBackupPane(ServerConnector connector) {
    this.api = new ApiClient(connector);
    setSpacing(10);
    setPadding(new Insets(12));

    Label tip = new Label("通过 SSH 备份在线设备配置，点击记录可查看内容；恢复功能为演示模式，不会真正下发到设备。");
    tip.getStyleClass().add("muted");

    buildTable();
    HBox actions = buildActions();
    HBox pager = buildPager();

    configArea.setEditable(false);
    configArea.setWrapText(true);
    configArea.setPrefHeight(200);
    configArea.setPromptText("点击左侧表格中的备份记录，此处显示配置内容...");
    configArea.setStyle("-fx-font-family: 'Consolas', 'Courier New', monospace; -fx-font-size: 14px; "
        + "-fx-control-inner-background: #ffffff; -fx-text-fill: #000000; -fx-border-color: #111111;");

    Label configTitle = new Label("配置内容");
    configTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: 700; -fx-text-fill: #111111;");

    SplitPane split = new SplitPane(new VBox(table, pager), new VBox(6, configTitle, configArea));
    split.setOrientation(javafx.geometry.Orientation.VERTICAL);
    split.setDividerPositions(0.5);
    VBox.setVgrow(configArea, Priority.ALWAYS);

    getChildren().addAll(tip, actions, split);
    VBox.setVgrow(split, Priority.ALWAYS);

    table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
      if (selected == null) { configArea.clear(); return; }
      loadConfigContent(selected.id());
    });

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

    deviceBox.setPrefWidth(220);
    deviceBox.setPromptText("选择设备");
    deviceBox.setCellFactory(cb -> new ListCell<>() {
      @Override
      protected void updateItem(Device item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? "" : (item.ip() + " (" + item.type() + ")"));
      }
    });
    deviceBox.setButtonCell(new ListCell<>() {
      @Override
      protected void updateItem(Device item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty || item == null ? "" : (item.ip() + " (" + item.type() + ")"));
      }
    });

    Button backupOne = new Button("备份选中设备");
    backupOne.setOnAction(e -> {
      Device d = deviceBox.getSelectionModel().getSelectedItem();
      if (d == null) { UiUtil.error("未选择设备", "请先选择要备份的设备"); return; }
      backupOne.setText("备份中...");
      backupOne.setDisable(true);
      new Thread(() -> {
        try {
          api.backupDevice(d.id());
          Platform.runLater(() -> {
            backupOne.setText("备份选中设备");
            backupOne.setDisable(false);
            refreshAsync();
          });
        } catch (Exception ex) {
          Platform.runLater(() -> {
            UiUtil.error("备份失败", ex.getMessage());
            backupOne.setText("备份选中设备");
            backupOne.setDisable(false);
          });
        }
      }, "config-backup-one").start();
    });

    Button backupAll = new Button("一键备份全部在线设备");
    backupAll.getStyleClass().add("success");
    backupAll.setOnAction(e -> {
      backupAll.setText("备份中...");
      backupAll.setDisable(true);
      new Thread(() -> {
        try {
          int count = api.backupAll();
          Platform.runLater(() -> {
            backupAll.setText("一键备份全部在线设备");
            backupAll.setDisable(false);
            UiUtil.info("备份完成", "成功备份 " + count + " 台在线设备的配置。");
            refreshAsync();
          });
        } catch (Exception ex) {
          Platform.runLater(() -> {
            UiUtil.error("备份失败", ex.getMessage());
            backupAll.setText("一键备份全部在线设备");
            backupAll.setDisable(false);
          });
        }
      }, "config-backup-all").start();
    });

    Button diffBtn = new Button("对比选中两条");
    diffBtn.setOnAction(e -> diffSelected());

    Button restoreBtn = new Button("演示恢复选中配置");
    restoreBtn.getStyleClass().add("primary");
    restoreBtn.setOnAction(e -> restoreSelected());

    return new HBox(10, refresh, deviceBox, backupOne, backupAll, diffBtn, restoreBtn);
  }

  private void diffSelected() {
    var selected = table.getSelectionModel().getSelectedItems();
    if (selected.size() != 2) {
      UiUtil.error("请选择两条记录", "按住 Ctrl/Cmd 点击表格中的两条备份记录，再点对比。");
      return;
    }
    long id1 = selected.get(0).id(), id2 = selected.get(1).id();
    String ip1 = deviceIpMap.getOrDefault(selected.get(0).deviceId(), "?");
    String ip2 = deviceIpMap.getOrDefault(selected.get(1).deviceId(), "?");
    String time1 = selected.get(0).backupTime() == null ? "" : selected.get(0).backupTime().toString();
    String time2 = selected.get(1).backupTime() == null ? "" : selected.get(1).backupTime().toString();

    new Thread(() -> {
      try {
        ConfigBackup cb1 = api.getConfigBackup(id1);
        ConfigBackup cb2 = api.getConfigBackup(id2);
        String t1 = cb1 != null && cb1.content() != null ? cb1.content() : "";
        String t2 = cb2 != null && cb2.content() != null ? cb2.content() : "";
        Platform.runLater(() -> showDiffWindow(ip1 + " @ " + time1, t1, ip2 + " @ " + time2, t2));
      } catch (Exception ex) {
        Platform.runLater(() -> UiUtil.error("加载失败", ex.getMessage()));
      }
    }, "config-diff-load").start();
  }

  private void restoreSelected() {
    ConfigBackup selected = table.getSelectionModel().getSelectedItem();
    if (selected == null) {
      UiUtil.error("未选择记录", "请先在表格中选中一条备份记录。");
      return;
    }
    String ip = deviceIpMap.getOrDefault(selected.deviceId(), String.valueOf(selected.deviceId()));
    if (!UiUtil.confirm("确认演示恢复", "将对设备 " + ip + " 演示恢复这条配置，是否继续？")) return;

    new Thread(() -> {
      try {
        Map<String, Object> result = api.restoreConfigBackup(selected.id());
        String deviceIp = String.valueOf(result.getOrDefault("deviceIp", ip));
        String lineCount = String.valueOf(result.getOrDefault("lineCount", 0));
        String message = String.valueOf(result.getOrDefault("message", "恢复演示已完成"));
        Platform.runLater(() -> UiUtil.info("恢复演示完成",
            "目标设备: " + deviceIp + "\n配置行数: " + lineCount + "\n" + message));
      } catch (Exception ex) {
        Platform.runLater(() -> UiUtil.error("恢复失败", ex.getMessage()));
      }
    }, "config-restore").start();
  }

  private void showDiffWindow(String titleLeft, String contentLeft, String titleRight, String contentRight) {
    javafx.stage.Stage stage = new javafx.stage.Stage();
    stage.setTitle("配置对比");
    stage.setWidth(900);
    stage.setHeight(600);

    String[] linesL = contentLeft.split("\n", -1);
    String[] linesR = contentRight.split("\n", -1);
    int maxLines = Math.max(linesL.length, linesR.length);

    VBox leftCol = new VBox();
    VBox rightCol = new VBox();

    for (int i = 0; i < maxLines; i++) {
      String l = i < linesL.length ? linesL[i] : "";
      String r = i < linesR.length ? linesR[i] : "";
      boolean same = l.equals(r);

      Label ll = new Label(l.isEmpty() ? " " : l);
      ll.setMaxWidth(Double.MAX_VALUE);
      ll.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 13px; -fx-padding: 2 6; "
          + (same ? "-fx-text-fill: #111111; -fx-background-color: #ffffff;"
                  : "-fx-text-fill: #000000; -fx-background-color: #fee2e2;"));

      Label rl = new Label(r.isEmpty() ? " " : r);
      rl.setMaxWidth(Double.MAX_VALUE);
      rl.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 13px; -fx-padding: 2 6; "
          + (same ? "-fx-text-fill: #111111; -fx-background-color: #ffffff;"
                  : "-fx-text-fill: #000000; -fx-background-color: #dcfce7;"));

      leftCol.getChildren().add(ll);
      rightCol.getChildren().add(rl);
    }

    Label lTitle = new Label(titleLeft);
    lTitle.setStyle("-fx-font-weight: 700; -fx-font-size: 14px; -fx-text-fill: #111111; -fx-padding: 6;");
    Label rTitle = new Label(titleRight);
    rTitle.setStyle("-fx-font-weight: 700; -fx-font-size: 14px; -fx-text-fill: #111111; -fx-padding: 6;");

    ScrollPane lScroll = new ScrollPane(leftCol);
    lScroll.setFitToWidth(true);
    lScroll.setStyle("-fx-background: #ffffff; -fx-background-color: #ffffff; -fx-border-color: #d1d5db;");
    ScrollPane rScroll = new ScrollPane(rightCol);
    rScroll.setFitToWidth(true);
    rScroll.setStyle("-fx-background: #ffffff; -fx-background-color: #ffffff; -fx-border-color: #d1d5db;");

    lScroll.vvalueProperty().bindBidirectional(rScroll.vvalueProperty());

    VBox left = new VBox(4, lTitle, lScroll);
    VBox right = new VBox(4, rTitle, rScroll);
    VBox.setVgrow(lScroll, Priority.ALWAYS);
    VBox.setVgrow(rScroll, Priority.ALWAYS);

    HBox.setHgrow(left, Priority.ALWAYS);
    HBox.setHgrow(right, Priority.ALWAYS);

    Label legend = new Label("红色 = 左侧内容（有差异）    绿色 = 右侧内容（有差异）    无底色 = 两侧相同");
    legend.setStyle("-fx-text-fill: #374151; -fx-font-size: 13px; -fx-padding: 6 8;");

    HBox diffPane = new HBox(4, left, right);
    VBox root = new VBox(diffPane, legend);
    VBox.setVgrow(diffPane, Priority.ALWAYS);
    root.setStyle("-fx-background-color: #ffffff; -fx-padding: 8;");

    stage.setScene(new javafx.scene.Scene(root));
    stage.show();
  }

  private HBox buildPager() {
    prevBtn.setOnAction(e -> { if (currentPage > 0) { currentPage--; showPage(); } });
    nextBtn.setOnAction(e -> { if ((currentPage + 1) * PAGE_SIZE < allData.size()) { currentPage++; showPage(); } });
    pageInfo.getStyleClass().add("muted");
    HBox pager = new HBox(12, prevBtn, pageInfo, nextBtn);
    pager.setAlignment(Pos.CENTER);
    pager.setPadding(new Insets(4, 0, 4, 0));
    return pager;
  }

  private void showPage() {
    int totalPages = Math.max(1, (int) Math.ceil((double) allData.size() / PAGE_SIZE));
    int from = currentPage * PAGE_SIZE;
    int to = Math.min(from + PAGE_SIZE, allData.size());
    pageRows.setAll(allData.subList(from, to));
    table.scrollTo(0);
    pageInfo.setText("第 " + (currentPage + 1) + " 页 / 共 " + totalPages + " 页（共 " + allData.size() + " 条）");
    prevBtn.setDisable(currentPage == 0);
    nextBtn.setDisable(currentPage >= totalPages - 1);
  }

  private void buildTable() {
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

    TableColumn<ConfigBackup, String> idx = new TableColumn<>("序号");
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

    TableColumn<ConfigBackup, String> device = new TableColumn<>("设备IP");
    device.setCellValueFactory(c -> {
      long did = c.getValue().deviceId();
      String ip = deviceIpMap.getOrDefault(did, String.valueOf(did));
      return new javafx.beans.property.SimpleStringProperty(ip);
    });

    TableColumn<ConfigBackup, String> time = new TableColumn<>("备份时间");
    time.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
        c.getValue().backupTime() == null ? "" : c.getValue().backupTime().toString()));

    table.getColumns().setAll(java.util.List.of(idx, device, time));
  }

  public void refresh() {
    refreshAsync();
  }

  private void refreshAsync() {
    new Thread(() -> {
      try {
        List<Device> devices = api.listDevices();
        Map<Long, String> ipMap = new HashMap<>();
        for (Device d : devices) ipMap.put(d.id(), d.ip());
        deviceIpMap = ipMap;

        List<ConfigBackup> list = api.listConfigBackups(1000);
        Platform.runLater(() -> {
          deviceBox.getItems().setAll(devices);
          if (!devices.isEmpty() && deviceBox.getSelectionModel().getSelectedItem() == null) {
            deviceBox.getSelectionModel().select(0);
          }
          allData = list;
          currentPage = 0;
          showPage();
          if (!pageRows.isEmpty() && table.getSelectionModel().getSelectedItem() == null) {
            table.getSelectionModel().select(0);
          }
          if (autoOpenDiff && !diffShown && list.size() >= 2) {
            diffShown = true;
            openDiffSnapshot(list.get(0), list.get(1));
          }
        });
      } catch (Exception e) {
        Platform.runLater(() -> UiUtil.error("加载备份记录失败", e.getMessage()));
      }
    }, "config-backup-refresh").start();
  }

  private void openDiffSnapshot(ConfigBackup left, ConfigBackup right) {
    String ip1 = deviceIpMap.getOrDefault(left.deviceId(), String.valueOf(left.deviceId()));
    String ip2 = deviceIpMap.getOrDefault(right.deviceId(), String.valueOf(right.deviceId()));
    String time1 = left.backupTime() == null ? "" : left.backupTime().toString();
    String time2 = right.backupTime() == null ? "" : right.backupTime().toString();
    String t1 = left.content() == null ? "" : left.content();
    String t2 = right.content() == null ? "" : right.content();
    showDiffWindow(ip1 + " @ " + time1, t1, ip2 + " @ " + time2, t2);
  }

  private void loadConfigContent(long backupId) {
    new Thread(() -> {
      try {
        ConfigBackup cb = api.getConfigBackup(backupId);
        Platform.runLater(() -> {
          if (cb != null && cb.content() != null) {
            configArea.setText(cb.content());
          } else {
            configArea.setText("（无配置内容）");
          }
        });
      } catch (Exception e) {
        Platform.runLater(() -> configArea.setText("加载失败: " + e.getMessage()));
      }
    }, "config-content-load").start();
  }
}
