package com.netmgmt.server.ssh;

import com.netmgmt.server.config.Props;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class LocalCommandExecutor {
  private final int commandTimeoutMs;
  private final Charset outputCharset;

  public LocalCommandExecutor(Props props) {
    this.commandTimeoutMs = props.getInt("ssh.commandTimeoutMs", 5000);
    this.outputCharset = resolveCharset(props.getString("ssh.outputCharset", "GBK"));
  }

  public String execute(String command) {
    try {
      boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
      String[] cmd = isWindows
          ? new String[]{"cmd.exe", "/c", command}
          : new String[]{"/bin/sh", "-c", command};

      Process process = new ProcessBuilder(cmd)
          .redirectErrorStream(true)
          .start();

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      InputStream is = process.getInputStream();

      long deadline = System.currentTimeMillis() + commandTimeoutMs;
      while (process.isAlive() || is.available() > 0) {
        while (is.available() > 0) {
          int n = is.read(buffer, 0, buffer.length);
          if (n < 0) break;
          baos.write(buffer, 0, n);
        }
        if (System.currentTimeMillis() > deadline) {
          process.destroyForcibly();
          throw new IllegalStateException("本地命令执行超时");
        }
        if (process.isAlive()) {
          Thread.sleep(50);
        }
      }
      while (is.available() > 0) {
        int n = is.read(buffer, 0, buffer.length);
        if (n < 0) break;
        baos.write(buffer, 0, n);
      }
      process.waitFor(1, TimeUnit.SECONDS);

      return new String(baos.toByteArray(), outputCharset).trim();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("本地命令执行被中断", e);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("本地命令执行失败: " + e.getMessage(), e);
    }
  }

  private static Charset resolveCharset(String charsetName) {
    if (charsetName == null || charsetName.isBlank()) return StandardCharsets.UTF_8;
    try {
      return Charset.forName(charsetName.trim());
    } catch (Exception ignored) {
      return StandardCharsets.UTF_8;
    }
  }
}
