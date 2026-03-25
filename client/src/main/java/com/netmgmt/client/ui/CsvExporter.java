package com.netmgmt.client.ui;

import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public final class CsvExporter {
  private CsvExporter() {}

  public static void export(Window owner, String defaultName, String[] headers, List<String[]> rows) {
    FileChooser chooser = new FileChooser();
    chooser.setTitle("导出 CSV");
    chooser.setInitialFileName(defaultName);
    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV 文件", "*.csv"));
    File file = chooser.showSaveDialog(owner);
    if (file == null) return;

    try (PrintWriter pw = new PrintWriter(new FileWriter(file, java.nio.charset.StandardCharsets.UTF_8))) {
      pw.write('\ufeff');
      pw.println(String.join(",", headers));
      for (String[] row : rows) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.length; i++) {
          if (i > 0) sb.append(",");
          String val = row[i] == null ? "" : row[i];
          if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            sb.append("\"").append(val.replace("\"", "\"\"")).append("\"");
          } else {
            sb.append(val);
          }
        }
        pw.println(sb);
      }
      UiUtil.info("导出成功", "已保存到：" + file.getAbsolutePath());
    } catch (IOException e) {
      UiUtil.error("导出失败", e.getMessage());
    }
  }
}
