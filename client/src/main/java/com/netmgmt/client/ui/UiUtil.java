package com.netmgmt.client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class UiUtil {
  private UiUtil() {}

  public static void error(String header, String content) {
    showDialog("\u26A0", "#ef4444", header, content != null ? content : "", false);
  }

  public static void info(String header, String content) {
    showDialog("\u2714", "#22c55e", header, content != null ? content : "", false);
  }

  public static boolean confirm(String header, String content) {
    return showDialog("?", "#3b82f6", header, content != null ? content : "", true);
  }

  private static boolean showDialog(String icon, String accentColor,
                                     String header, String content, boolean showCancel) {
    Stage stage = new Stage();
    stage.initModality(Modality.APPLICATION_MODAL);
    stage.initStyle(StageStyle.TRANSPARENT);

    Label iconLabel = new Label(icon);
    iconLabel.setStyle("-fx-font-size: 28px; -fx-text-fill: " + accentColor + ";");
    iconLabel.setMinWidth(44);
    iconLabel.setAlignment(Pos.CENTER);

    Label headerLabel = new Label(header);
    headerLabel.setStyle("-fx-text-fill: #000000; -fx-font-size: 16px; -fx-font-weight: 700;");
    headerLabel.setWrapText(true);

    Label contentLabel = new Label(content);
    contentLabel.setStyle("-fx-text-fill: #111111; -fx-font-size: 14px;");
    contentLabel.setWrapText(true);
    contentLabel.setMaxWidth(280);

    VBox textBox = new VBox(4, headerLabel, contentLabel);
    HBox.setHgrow(textBox, Priority.ALWAYS);

    HBox top = new HBox(12, iconLabel, textBox);
    top.setAlignment(Pos.TOP_LEFT);
    top.setPadding(new Insets(22, 22, 14, 22));

    final boolean[] result = {false};

    Button okBtn = new Button("确  定");
    okBtn.setStyle("-fx-background-color: #ffffff; -fx-text-fill: #000000; "
        + "-fx-font-size: 14px; -fx-font-weight: 700; -fx-padding: 8 26; "
        + "-fx-border-color: " + accentColor + "; -fx-border-width: 1.5; "
        + "-fx-background-radius: 8; -fx-cursor: hand;");
    okBtn.setOnAction(e -> { result[0] = true; stage.close(); });

    HBox btnBox;
    if (showCancel) {
      Button cancelBtn = new Button("取  消");
      cancelBtn.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #000000; "
          + "-fx-font-size: 14px; -fx-font-weight: 700; -fx-padding: 8 26; "
          + "-fx-background-radius: 8; -fx-border-color: #9ca3af; -fx-border-radius: 8; -fx-cursor: hand;");
      cancelBtn.setOnAction(e -> { result[0] = false; stage.close(); });
      btnBox = new HBox(10, cancelBtn, okBtn);
    } else {
      btnBox = new HBox(10, okBtn);
    }
    btnBox.setAlignment(Pos.CENTER_RIGHT);
    btnBox.setPadding(new Insets(0, 22, 18, 22));

    Label topBar = new Label();
    topBar.setMaxWidth(Double.MAX_VALUE);
    topBar.setMinHeight(6);

    VBox root = new VBox(topBar, top, btnBox);
    root.setStyle("-fx-background-color: #ffffff; -fx-border-color: #d1d5db; "
        + "-fx-border-radius: 12; -fx-background-radius: 12; -fx-border-width: 1; "
        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 20, 0, 0, 4);");
    root.setPrefWidth(380);

    final double[] dragOffset = {0, 0};
    root.setOnMousePressed(e -> {
      dragOffset[0] = e.getScreenX() - stage.getX();
      dragOffset[1] = e.getScreenY() - stage.getY();
    });
    root.setOnMouseDragged(e -> {
      stage.setX(e.getScreenX() - dragOffset[0]);
      stage.setY(e.getScreenY() - dragOffset[1]);
    });

    Scene scene = new Scene(root);
    scene.setFill(Color.TRANSPARENT);
    stage.setScene(scene);

    Window owner = Stage.getWindows().stream()
        .filter(Window::isFocused).findFirst()
        .orElse(Stage.getWindows().isEmpty() ? null : Stage.getWindows().get(0));
    if (owner != null) {
      stage.initOwner(owner);
      stage.setOnShown(e -> {
        stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
        stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
      });
    }

    stage.showAndWait();

    return result[0];
  }
}
