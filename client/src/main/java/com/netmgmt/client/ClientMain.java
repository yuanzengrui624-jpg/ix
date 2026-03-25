package com.netmgmt.client;

import javafx.application.Application;
import java.util.Locale;

public final class ClientMain {
  public static void main(String[] args) {
    Locale.setDefault(Locale.CHINA);
    Application.launch(NetMgmtApp.class, args);
  }
}

