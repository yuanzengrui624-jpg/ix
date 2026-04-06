package com.netmgmt.client.ui;

import com.netmgmt.client.net.ApiClient;
import com.netmgmt.client.net.ServerConnector;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Map;

public final class DashboardPane extends VBox {
  private final ApiClient api;

  private final Label totalLabel = new Label("0");
  private final Label onlineLabel = new Label("0");
  private final Label offlineLabel = new Label("0");
  private final Label alarmLabel = new Label("0");

  private final PieChart pieChart = new PieChart();
  private final CategoryAxis xAxis = new CategoryAxis();
  private PieChart.Data pieOnline;
  private PieChart.Data pieOffline;
  private final XYChart.Series<String, Number> barSeries = new XYChart.Series<>();
  private final NumberAxis yAxis = new NumberAxis();
  private final BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);


  public DashboardPane(ServerConnector connector) {
    this.api = new ApiClient(connector);
    setSpacing(14);
    setPadding(new Insets(12));

    Label tip = new Label("系统运行状态一览");
    tip.getStyleClass().add("muted");

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

    HBox topBar = new HBox(10, tip, new Region(), refresh);
    HBox.setHgrow(topBar.getChildren().get(1), Priority.ALWAYS);

    HBox cards = buildCards();

    pieChart.setTitle("设备状态分布");
    pieChart.setLabelsVisible(true);
    pieOnline = new PieChart.Data("在线 (0)", 0);
    pieOffline = new PieChart.Data("离线 (0)", 0);
    pieChart.getData().addAll(pieOnline, pieOffline);
    pieChart.setPrefHeight(280);
    pieChart.setLegendVisible(true);
    pieChart.setAnimated(true);

    xAxis.setLabel("日期");
    barChart.getData().add(barSeries);
    yAxis.setLabel("告警数");
    xAxis.setAnimated(false);
    barChart.setTitle("近7天告警趋势");
    barChart.setPrefHeight(280);
    barChart.setLegendVisible(false);
    barChart.setAnimated(true);

    HBox charts = new HBox(20, pieChart, barChart);
    HBox.setHgrow(pieChart, Priority.ALWAYS);
    HBox.setHgrow(barChart, Priority.ALWAYS);

    getChildren().addAll(topBar, cards, charts);
    VBox.setVgrow(charts, Priority.ALWAYS);

    refreshAsync();
  }

  private HBox buildCards() {
    HBox cards = new HBox(16,
        buildCard("设备总数", totalLabel, "#2563eb"),
        buildCard("在线", onlineLabel, "#16a34a"),
        buildCard("离线", offlineLabel, "#dc2626"),
        buildCard("未确认告警", alarmLabel, "#ea580c")
    );
    return cards;
  }

  private VBox buildCard(String title, Label valueLabel, String color) {
    Label titleLbl = new Label(title);
    titleLbl.setStyle("-fx-text-fill: #374151; -fx-font-size: 14px; -fx-font-weight: 700;");

    valueLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 28px; -fx-font-weight: 700;");

    VBox card = new VBox(6, titleLbl, valueLabel);
    card.setAlignment(Pos.CENTER);
    card.setPadding(new Insets(16, 32, 16, 32));
    card.setStyle("-fx-background-color: #ffffff; -fx-border-color: #d1d5db; "
        + "-fx-background-radius: 12; -fx-border-radius: 12;");
    HBox.setHgrow(card, Priority.ALWAYS);
    return card;
  }

  public void refresh() {
    refreshAsync();
  }

  private void refreshAsync() {
    new Thread(() -> {
      try {
        Map<String, Object> stats = api.getStats();
        int total = toInt(stats.get("deviceTotal"));
        int online = toInt(stats.get("deviceOnline"));
        int offline = toInt(stats.get("deviceOffline"));
        int unacked = toInt(stats.get("unackedAlarms"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> trend = (List<Map<String, Object>>) stats.get("alarmTrend");

        Platform.runLater(() -> {
          totalLabel.setText(String.valueOf(total));
          onlineLabel.setText(String.valueOf(online));
          offlineLabel.setText(String.valueOf(offline));
          alarmLabel.setText(String.valueOf(unacked));

          pieChart.getData().clear();
          if (online > 0 || offline > 0) {
            pieChart.getData().add(new PieChart.Data("在线 (" + online + ")", Math.max(online, 0)));
            pieChart.getData().add(new PieChart.Data("离线 (" + offline + ")", Math.max(offline, 0)));
          } else {
            pieChart.getData().add(new PieChart.Data("暂无设备", 1));
          }

          XYChart.Series<String, Number> series = new XYChart.Series<>();
          if (trend != null) {
            for (Map<String, Object> item : trend) {
              String day = String.valueOf(item.get("day"));
              int count = toInt(item.get("count"));
              series.getData().add(new XYChart.Data<>(day, count));
            }
          }
          barChart.getData().clear();
          barChart.getData().add(series);
        });
      } catch (Exception e) {
        Platform.runLater(() -> UiUtil.error("加载统计失败", e.getMessage()));
      }
    }, "dashboard-refresh").start();
  }

  private static int toInt(Object v) {
    if (v instanceof Number n) return n.intValue();
    if (v != null) {
      try { return Integer.parseInt(String.valueOf(v)); }
      catch (NumberFormatException e) { return 0; }
    }
    return 0;
  }
}
