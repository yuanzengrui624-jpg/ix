package com.netmgmt.client.ui;

import com.netmgmt.client.model.Device;
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

public final class DevicePane extends VBox {
  private final ApiClient api;
  private final ObservableList<Device> rows = FXCollections.observableArrayList();
  private final TableView<Device> table = new TableView<>(rows);

  public DevicePane(ServerConnector connector) {
    this.api = new ApiClient(connector);
    setSpacing(10);
    setPadding(new Insets(12));

    Label tip = new Label("维护设备清单（IP / 类型 / SNMP团体名 / SSH凭证），并查看在线状态。");
    tip.getStyleClass().add("muted");

    buildTable();
    HBox actions = buildActions();

    getChildren().addAll(tip, actions, table);
    VBox.setVgrow(table, Priority.ALWAYS);

    refreshAsync();
  }

  public void refreshAsync() {
    new Thread(() -> {
      try {
        List<Device> list = api.listDevices();
        Platform.runLater(() -> {
          rows.setAll(list);
        });
      } catch (Exception e) {
        Platform.runLater(() -> UiUtil.error("加载设备失败", e.getMessage()));
      }
    }, "device-refresh").start();
  }

  private HBox buildActions() {
    Button refresh = new Button("刷新");
    refresh.getStyleClass().add("primary");
    refresh.setOnAction(e -> refreshAsync());

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

    Button runOnce = new Button("立即采集一次(Ping)");
    runOnce.setOnAction(e -> new Thread(() -> {
      try {
        api.runMonitorOnce();
      } catch (Exception ex) {
        Platform.runLater(() -> UiUtil.error("触发采集失败", ex.getMessage()));
      }
    }, "monitor-run-once").start());

    HBox box = new HBox(10, refresh, add, edit, del, spacer, runOnce);
    return box;
  }

  private void buildTable() {
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

    TableColumn<Device, String> ip = new TableColumn<>("IP");
    ip.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().ip()));

    TableColumn<Device, String> type = new TableColumn<>("类型");
    type.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().type()));

    TableColumn<Device, String> community = new TableColumn<>("SNMP团体名");
    community.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(nvl(c.getValue().community())));

    TableColumn<Device, String> status = new TableColumn<>("状态");
    status.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(statusText(c.getValue().status())));

    TableColumn<Device, String> last = new TableColumn<>("最后更新时间");
    last.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
        c.getValue().lastUpdate() == null ? "" : c.getValue().lastUpdate().toString()));

    table.getColumns().addAll(ip, type, community, status, last);
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

