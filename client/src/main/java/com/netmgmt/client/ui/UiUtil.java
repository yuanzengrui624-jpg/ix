package com.netmgmt.client.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public final class UiUtil {
  private UiUtil() {}

  public static void error(String header, String content) {
    Alert a = new Alert(Alert.AlertType.ERROR, content, ButtonType.OK);
    a.setHeaderText(header);
    a.showAndWait();
  }

  public static boolean confirm(String header, String content) {
    Alert a = new Alert(Alert.AlertType.CONFIRMATION, content, ButtonType.OK, ButtonType.CANCEL);
    a.setHeaderText(header);
    return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
  }
}

