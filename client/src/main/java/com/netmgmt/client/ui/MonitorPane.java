package com.netmgmt.client.ui;

import com.netmgmt.client.model.Device;
import com.netmgmt.client.model.MonitorLog;
import com.netmgmt.client.net.ApiClient;
import com.netmgmt.client.net.ServerConnector;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.layout.Region;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MonitorPane extends VBox {
  private final ApiClient api;

  private final ComboBox<Device> deviceBox = new ComboBox<>();
  private final DatePicker startDate = new DatePicker(LocalDate.now().minusDays(1));
  private final DatePicker endDate = new DatePicker(LocalDate.now());
  private final ObservableList<MonitorLog> pageRows = FXCollections.observableArrayList();
  private final TableView<MonitorLog> table = new TableView<>(pageRows);

  private final CategoryAxis chartXAxis = new CategoryAxis();
  private final NumberAxis chartYAxis = new NumberAxis();
  private final LineChart<String, Number> lineChart = new LineChart<>(chartXAxis, chartYAxis);
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

  private List<MonitorLog> allData = new ArrayList<>();
  private static final int PAGE_SIZE = 20;
  private int currentPage = 0;
  private final Label pageInfo = new Label("第 1 页 / 共 1 页");
  private final Button prevBtn = new Button("上一页");
  private final Button nextBtn = new Button("下一页");

  public MonitorPane(ServerConnector connector) {
    this.api = new ApiClient(connector);
    setSpacing(10);
    setPadding(new Insets(12));

    Label tip = new Label("选择设备和日期范围，查询该设备的 Ping / CPU / 内存监控记录。");
    tip.getStyleClass().add("muted");

    buildTable();
    buildChart();
    GridPane filterGrid = buildFilterGrid();

    Label tableTitle = new Label("采集记录");
    tableTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #94a3b8;");

    HBox pager = buildPager();

    SplitPane split = new SplitPane(new VBox(table, pager), lineChart);
    split.setOrientation(javafx.geometry.Orientation.VERTICAL);
    split.setDividerPositions(0.55);

    getChildren().addAll(tip, filterGrid, tableTitle, split);
    VBox.setVgrow(split, Priority.ALWAYS);

    loadDevicesAsync();
  }

  private GridPane buildFilterGrid() {
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    grid.setAlignment(Pos.CENTER_LEFT);

    Label lbl1 = new Label("选择设备");
    lbl1.setStyle("-fx-font-weight: 700; -fx-text-fill: #cbd5e1;");
    lbl1.setMinWidth(70);

    deviceBox.setPrefWidth(260);
    deviceBox.setPromptText("请选择要查看的设备");
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

    Button latest = new Button("查看最新一条");
    latest.setOnAction(e -> loadLatestAsync());

    Label lbl2 = new Label("选择日期");
    lbl2.setStyle("-fx-font-weight: 700; -fx-text-fill: #cbd5e1;");
    lbl2.setMinWidth(70);

    setupDatePicker(startDate);
    setupDatePicker(endDate);
    startDate.setPrefWidth(140);
    endDate.setPrefWidth(140);

    Label fromLbl = new Label("从");
    Label toLbl = new Label("到");

    HBox dateFields = new HBox(8, fromLbl, startDate, toLbl, endDate);
    dateFields.setAlignment(Pos.CENTER_LEFT);

    Button query = new Button("查询历史记录");
    query.getStyleClass().add("primary");
    query.setOnAction(e -> queryAsync());

    Button export = new Button("导出 CSV");
    export.setOnAction(e -> exportCsv());

    grid.add(lbl1, 0, 0);
    grid.add(deviceBox, 1, 0);
    grid.add(new HBox(10, latest, export), 2, 0);

    grid.add(lbl2, 0, 1);
    grid.add(dateFields, 1, 1);
    grid.add(query, 2, 1);

    return grid;
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

  private void setData(List<MonitorLog> data) {
    allData = data != null ? data : new ArrayList<>();
    currentPage = 0;
    showPage();
    updateChart(allData);
  }

  private void buildTable() {
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

    TableColumn<MonitorLog, String> idx = new TableColumn<>("序号");
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

    TableColumn<MonitorLog, String> time = new TableColumn<>("采集时间");
    time.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
        c.getValue().collectTime() == null ? "" : c.getValue().collectTime().toString()));

    TableColumn<MonitorLog, String> ping = new TableColumn<>("Ping状态");
    ping.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().pingStatus() == 1 ? "OK" : "FAIL"));
    ping.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) { setText(null); setStyle(""); return; }
        setText(item);
        setStyle("OK".equals(item)
            ? "-fx-text-fill: #22c55e; -fx-font-weight: 700;"
            : "-fx-text-fill: #ef4444; -fx-font-weight: 700;");
      }
    });

    TableColumn<MonitorLog, String> cpu = new TableColumn<>("CPU(%)");
    cpu.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
        c.getValue().cpu() == null ? "-" : String.format("%.1f", c.getValue().cpu())));

    TableColumn<MonitorLog, String> mem = new TableColumn<>("内存(%)");
    mem.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
        c.getValue().mem() == null ? "-" : String.format("%.1f", c.getValue().mem())));

    TableColumn<MonitorLog, String> iface = new TableColumn<>("接口状态");
    iface.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
        parseInterfaceSummary(c.getValue().interfaceStatusJson())));
    iface.setCellFactory(col -> new TableCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null || item.equals("-")) { setText(item); setGraphic(null); return; }
        Label lbl = new Label(item);
        lbl.setStyle(item.contains("DOWN")
            ? "-fx-text-fill: #f59e0b; -fx-font-size: 11px;"
            : "-fx-text-fill: #22c55e; -fx-font-size: 11px;");
        setGraphic(lbl);
        setText(null);
      }
    });

    table.getColumns().addAll(idx, time, ping, cpu, mem, iface);
  }

  private void buildChart() {
    chartXAxis.setLabel("时间");
    chartYAxis.setLabel("使用率(%)");
    chartYAxis.setAutoRanging(false);
    chartYAxis.setLowerBound(0);
    chartYAxis.setUpperBound(100);
    chartYAxis.setTickUnit(20);
    lineChart.setTitle("CPU / 内存趋势");
    lineChart.setCreateSymbols(false);
    lineChart.setAnimated(false);
    lineChart.setPrefHeight(240);
  }

  private void updateChart(List<MonitorLog> data) {
    lineChart.getData().clear();
    XYChart.Series<String, Number> cpuSeries = new XYChart.Series<>();
    cpuSeries.setName("CPU(%)");
    XYChart.Series<String, Number> memSeries = new XYChart.Series<>();
    memSeries.setName("MEM(%)");

    int step = Math.max(1, data.size() / 60);
    for (int i = data.size() - 1; i >= 0; i -= step) {
      MonitorLog m = data.get(i);
      String label = m.collectTime() == null ? "" : m.collectTime().format(TIME_FMT);
      if (m.cpu() != null) cpuSeries.getData().add(new XYChart.Data<>(label, m.cpu()));
      if (m.mem() != null) memSeries.getData().add(new XYChart.Data<>(label, m.mem()));
    }

    lineChart.getData().addAll(cpuSeries, memSeries);

    Platform.runLater(() -> {
      for (Node legendItem : lineChart.lookupAll(".chart-legend-item")) {
        if (legendItem instanceof Label legend) {
          legend.setOnMouseClicked(ev -> {
            for (XYChart.Series<String, Number> s : lineChart.getData()) {
              if (s.getName().equals(legend.getText())) {
                Node line = s.getNode();
                if (line == null) break;
                boolean visible = line.isVisible();
                line.setVisible(!visible);
                for (XYChart.Data<String, Number> dp : s.getData()) {
                  if (dp.getNode() != null) dp.getNode().setVisible(!visible);
                }
                legend.setStyle(visible ? "-fx-opacity: 0.4;" : "-fx-opacity: 1.0;");
                break;
              }
            }
          });
          legend.setStyle("-fx-cursor: hand;");
        }
      }
    });
  }

  public void reloadDevices() {
    loadDevicesAsync();
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
    if (d == null) {
      UiUtil.error("未选择设备", "请先选择一个设备");
      return;
    }
    new Thread(() -> {
      try {
        MonitorLog m = api.latestMonitor(d.id());
        Platform.runLater(() -> {
          if (m != null) {
            setData(List.of(m));
          } else {
            allData = new ArrayList<>();
            currentPage = 0;
            showPage();
            lineChart.getData().clear();
            UiUtil.info("暂无数据", "该设备尚未产生监控日志，请先在\"设备管理\"页中点击\"立即采集一次\"。");
          }
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
    if (sd.isAfter(ed)) {
      UiUtil.error("日期范围错误", "开始日期不能晚于结束日期");
      return;
    }
    LocalDateTime start = LocalDateTime.of(sd, LocalTime.MIN);
    LocalDateTime end = LocalDateTime.of(ed, LocalTime.MAX);

    new Thread(() -> {
      try {
        List<MonitorLog> list = api.queryMonitor(d.id(), start, end, 5000);
        Platform.runLater(() -> setData(list));
      } catch (Exception e) {
        Platform.runLater(() -> UiUtil.error("查询失败", e.getMessage()));
      }
    }, "monitor-query").start();
  }

  private static void setupDatePicker(DatePicker dp) {
    dp.setConverter(new javafx.util.StringConverter<>() {
      private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
      @Override
      public String toString(LocalDate d) { return d == null ? "" : d.format(fmt); }
      @Override
      public LocalDate fromString(String s) {
        return (s == null || s.isBlank()) ? null : LocalDate.parse(s, fmt);
      }
    });
    dp.getProperties().put("chronology", java.time.chrono.IsoChronology.INSTANCE);

    dp.setDayCellFactory(picker -> new DateCell() {
      @Override
      public void updateItem(LocalDate item, boolean empty) {
        super.updateItem(item, empty);
        if (item != null && item.isAfter(LocalDate.now())) {
          setDisable(true);
          setStyle("-fx-opacity: 0.4;");
        }
      }
    });
  }

  private static String parseInterfaceSummary(String json) {
    if (json == null || json.isBlank()) return "-";
    try {
      int up = 0, down = 0;
      int idx = 0;
      while ((idx = json.indexOf("\"status\":", idx)) != -1) {
        int start = json.indexOf("\"", idx + 9) + 1;
        int end = json.indexOf("\"", start);
        String s = json.substring(start, end);
        if ("UP".equals(s)) up++;
        else down++;
        idx = end;
      }
      if (down == 0) return up + " UP";
      return up + " UP / " + down + " DOWN";
    } catch (Exception e) {
      return "-";
    }
  }

  private void exportCsv() {
    if (allData.isEmpty()) { UiUtil.error("无数据", "没有监控数据可导出，请先查询"); return; }
    String[] headers = {"序号", "采集时间", "Ping状态", "CPU(%)", "内存(%)", "接口状态"};
    java.util.List<String[]> rows = new java.util.ArrayList<>();
    for (int i = 0; i < allData.size(); i++) {
      MonitorLog m = allData.get(i);
      rows.add(new String[]{
          String.valueOf(i + 1),
          m.collectTime() == null ? "" : m.collectTime().toString(),
          m.pingStatus() == 1 ? "OK" : "FAIL",
          m.cpu() == null ? "" : String.format("%.1f", m.cpu()),
          m.mem() == null ? "" : String.format("%.1f", m.mem()),
          parseInterfaceSummary(m.interfaceStatusJson())
      });
    }
    javafx.stage.Window owner = getScene() != null ? getScene().getWindow() : null;
    CsvExporter.export(owner, "监控日志.csv", headers, rows);
  }
}
