package com.netmgmt.client.ui;

import com.netmgmt.client.model.Device;
import com.netmgmt.client.net.ApiClient;
import com.netmgmt.client.net.ServerConnector;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

public final class DevicePane extends VBox {
  private final ApiClient api;
  private final ObservableList<Device> rows = FXCollections.observableArrayList();
  private final TableView<Device> table = new TableView<>(rows);
  private final Pane topologyPane = new Pane();

  public DevicePane(ServerConnector connector) {
    this.api = new ApiClient(connector);
    setSpacing(10);
    setPadding(new Insets(12));

    Label tip = new Label("维护设备清单（IP / 类型 / SNMP团体名 / SSH凭证），并查看在线状态。");
    tip.getStyleClass().add("muted");

    buildTable();
    HBox actions = buildActions();
    VBox topologyView = buildTopologyView();
    SplitPane split = new SplitPane(table, topologyView);
    split.setOrientation(javafx.geometry.Orientation.VERTICAL);
    split.setDividerPositions(0.68);

    getChildren().addAll(tip, actions, split);
    VBox.setVgrow(split, Priority.ALWAYS);

    refreshAsync();
  }

  public void refreshAsync() {
    new Thread(() -> {
      try {
        List<Device> list = api.listDevices();
        Platform.runLater(() -> {
          rows.setAll(list);
          redrawTopology();
        });
      } catch (Exception e) {
        Platform.runLater(() -> UiUtil.error("加载设备失败", e.getMessage()));
      }
    }, "device-refresh").start();
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

    Button add = new Button("添加设备");
    add.getStyleClass().add("primary");
    add.setOnAction(e -> openEditDialog(null));

    Button edit = new Button("修改");
    edit.setOnAction(e -> {
      Device d = table.getSelectionModel().getSelectedItem();
      if (d == null) return;
      openEditDialog(d);
    });

    Button del = new Button("删除");
    del.getStyleClass().add("danger");
    del.setOnAction(e -> {
      Device d = table.getSelectionModel().getSelectedItem();
      if (d == null) return;
      if (!UiUtil.confirm("确认删除", "删除设备 " + d.ip() + " ?")) return;

      new Thread(() -> {
        try {
          api.deleteDevice(d.id());
          refreshAsync();
        } catch (Exception ex) {
          Platform.runLater(() -> UiUtil.error("删除失败", ex.getMessage()));
        }
      }, "device-delete").start();
    });

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    Button runOnce = new Button("立即采集一次");
    runOnce.getStyleClass().add("success");
    runOnce.setOnAction(e -> {
      runOnce.setText("采集中...");
      runOnce.setDisable(true);
      new Thread(() -> {
        try {
          api.runMonitorOnce();
          Thread.sleep(500);
          Platform.runLater(() -> {
            runOnce.setText("立即采集一次");
            runOnce.setDisable(false);
            refreshAsync();
          });
        } catch (Exception ex) {
          Platform.runLater(() -> {
            UiUtil.error("触发采集失败", ex.getMessage());
            runOnce.setText("立即采集一次");
            runOnce.setDisable(false);
          });
        }
      }, "monitor-run-once").start();
    });

    HBox box = new HBox(10, refresh, add, edit, del, spacer, runOnce);
    return box;
  }

  private void buildTable() {
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

    TableColumn<Device, String> idx = new TableColumn<>("序号");
    idx.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty ? null : String.valueOf(getIndex() + 1));
      }
    });
    idx.setPrefWidth(50);
    idx.setMaxWidth(60);
    idx.setSortable(false);

    TableColumn<Device, String> ip = new TableColumn<>("IP");
    ip.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().ip()));

    TableColumn<Device, String> type = new TableColumn<>("类型");
    type.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().type()));

    TableColumn<Device, String> community = new TableColumn<>("SNMP团体名");
    community.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(nvl(c.getValue().community())));

    TableColumn<Device, String> status = new TableColumn<>("状态");
    status.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(statusText(c.getValue().status())));
    status.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) { setText(null); setGraphic(null); return; }
        Label badge = new Label(item);
        badge.getStyleClass().add("在线".equals(item) ? "chip-ok" : "chip-bad");
        setGraphic(badge);
        setText(null);
      }
    });

    TableColumn<Device, String> last = new TableColumn<>("最后更新时间");
    last.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
        c.getValue().lastUpdate() == null ? "" : c.getValue().lastUpdate().toString()));

    table.getColumns().setAll(java.util.List.of(idx, ip, type, community, status, last));
  }

  private VBox buildTopologyView() {
    Label topoTitle = new Label("拓扑简图");
    topoTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #94a3b8;");

    topologyPane.setPrefHeight(240);
    topologyPane.setMinHeight(220);
    topologyPane.setStyle("-fx-background-color: #1e293b; -fx-border-color: #334155; "
        + "-fx-border-radius: 10; -fx-background-radius: 10;");
    topologyPane.widthProperty().addListener((obs, oldVal, newVal) -> redrawTopology());
    topologyPane.heightProperty().addListener((obs, oldVal, newVal) -> redrawTopology());

    VBox box = new VBox(6, topoTitle, topologyPane);
    VBox.setVgrow(topologyPane, Priority.ALWAYS);
    return box;
  }

  private void redrawTopology() {
    topologyPane.getChildren().clear();

    double width = topologyPane.getWidth() > 100 ? topologyPane.getWidth() : 900;
    double height = topologyPane.getHeight() > 100 ? topologyPane.getHeight() : 240;
    if (rows.isEmpty()) {
      Label empty = new Label("暂无设备，添加设备后这里会显示简单拓扑。");
      empty.getStyleClass().add("muted");
      empty.setLayoutX(width / 2 - 120);
      empty.setLayoutY(height / 2 - 10);
      topologyPane.getChildren().add(empty);
      return;
    }

    double centerX = width / 2;
    double coreWidth = 140;
    double coreHeight = 46;
    VBox coreNode = createTopologyNode("监控中心", "Socket / SNMP", "#2d4a6f", "#3b82f6", "#dbeafe");
    coreNode.setPrefSize(coreWidth, coreHeight);
    coreNode.setLayoutX(centerX - coreWidth / 2);
    coreNode.setLayoutY(18);
    topologyPane.getChildren().add(coreNode);

    int shown = Math.min(rows.size(), 8);
    int columns = Math.min(4, Math.max(1, shown));
    int rowCount = (int) Math.ceil((double) shown / columns);
    double startY = 102;
    double rowGap = rowCount > 1 ? 74 : 0;
    double usableWidth = width - 120;
    double xGap = columns == 1 ? 0 : usableWidth / (columns - 1);

    for (int i = 0; i < shown; i++) {
      Device device = rows.get(i);
      int row = i / columns;
      int col = i % columns;

      double nodeWidth = 120;
      double nodeHeight = 48;
      double x = 60 + col * xGap - nodeWidth / 2;
      double y = startY + row * rowGap;

      String border = switch (device.status()) {
        case 1 -> "#22c55e";
        case 2 -> "#ef4444";
        default -> "#64748b";
      };
      String bg = switch (device.status()) {
        case 1 -> "#123a29";
        case 2 -> "#4b1d1d";
        default -> "#334155";
      };
      String subtitle = device.type() + " · " + statusText(device.status());

      Line line = new Line(centerX, 64, x + nodeWidth / 2, y);
      line.setStroke(Color.web("#475569"));
      line.setStrokeWidth(1.4);
      topologyPane.getChildren().add(line);

      VBox node = createTopologyNode(device.ip(), subtitle, bg, border, "#e2e8f0");
      node.setPrefSize(nodeWidth, nodeHeight);
      node.setLayoutX(x);
      node.setLayoutY(y);
      topologyPane.getChildren().add(node);
    }

    if (rows.size() > shown) {
      Label more = new Label("其余 " + (rows.size() - shown) + " 台设备已省略显示");
      more.getStyleClass().add("muted");
      more.setLayoutX(16);
      more.setLayoutY(height - 24);
      topologyPane.getChildren().add(more);
    }
  }

  private static VBox createTopologyNode(String title, String subtitle, String bg, String border, String titleColor) {
    Label titleLabel = new Label(title);
    titleLabel.setStyle("-fx-text-fill: " + titleColor + "; -fx-font-size: 12px; -fx-font-weight: 700;");

    Label subLabel = new Label(subtitle);
    subLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px;");

    VBox box = new VBox(2, titleLabel, subLabel);
    box.setAlignment(Pos.CENTER);
    box.setStyle("-fx-background-color: " + bg + "; -fx-border-color: " + border + "; "
        + "-fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 6 10;");
    return box;
  }

  private void openEditDialog(Device existing) {
    Dialog<ButtonType> dlg = new Dialog<>();
    dlg.setTitle(existing == null ? "添加设备" : "修改设备");
    dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

    TextField ip = new TextField(existing == null ? "" : existing.ip());
    TextField type = new TextField(existing == null ? "switch" : existing.type());
    TextField community = new TextField(existing == null ? "public" : nvl(existing.community()));
    TextField sshUser = new TextField(existing == null ? "" : nvl(existing.sshUser()));
    PasswordField sshPwd = new PasswordField();
    if (existing != null && existing.sshPwd() != null) sshPwd.setText(existing.sshPwd());

    VBox form = new VBox(10,
        labeled("IP", ip),
        labeled("类型", type),
        labeled("SNMP团体名", community),
        labeled("SSH用户名(可选)", sshUser),
        labeled("SSH密码(可选)", sshPwd)
    );
    form.setPadding(new Insets(10));
    dlg.getDialogPane().setContent(form);

    dlg.showAndWait().ifPresent(bt -> {
      if (bt != ButtonType.OK) return;
      String ipV = ip.getText().trim();
      String typeV = type.getText().trim();
      if (ipV.isEmpty() || typeV.isEmpty()) {
        UiUtil.error("参数不完整", "IP和类型不能为空");
        return;
      }

      new Thread(() -> {
        try {
          if (existing == null) {
            api.addDevice(ipV, typeV, blankToNull(community.getText()),
                blankToNull(sshUser.getText()), blankToNull(sshPwd.getText()));
          } else {
            api.updateDevice(existing.id(), ipV, typeV, blankToNull(community.getText()),
                blankToNull(sshUser.getText()), blankToNull(sshPwd.getText()));
          }
          refreshAsync();
        } catch (Exception ex) {
          Platform.runLater(() -> UiUtil.error("保存失败", ex.getMessage()));
        }
      }, "device-save").start();
    });
  }

  private static HBox labeled(String label, Control control) {
    Label l = new Label(label);
    l.setMinWidth(120);
    HBox box = new HBox(10, l, control);
    HBox.setHgrow(control, Priority.ALWAYS);
    return box;
  }

  private static String statusText(int s) {
    return switch (s) {
      case 1 -> "在线";
      case 2 -> "离线";
      default -> "未知";
    };
  }

  private static String nvl(String s) {
    return s == null ? "" : s;
  }

  private static String blankToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }
}

