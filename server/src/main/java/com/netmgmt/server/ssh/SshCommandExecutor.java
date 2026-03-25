package com.netmgmt.server.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.netmgmt.server.config.Props;
import com.netmgmt.server.model.Device;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class SshCommandExecutor {
  private final int sshPort;
  private final int sshConnectTimeoutMs;
  private final int sshCommandTimeoutMs;
  private final Charset outputCharset;

  public SshCommandExecutor(Props props) {
    this.sshPort = props.getInt("ssh.port", 22);
    this.sshConnectTimeoutMs = props.getInt("ssh.connectTimeoutMs", 3000);
    this.sshCommandTimeoutMs = props.getInt("ssh.commandTimeoutMs", 5000);
    this.outputCharset = resolveCharset(props.getString("ssh.outputCharset", "GBK"));
  }

  public String execute(Device device, String command) {
    if (device == null) throw new IllegalArgumentException("设备不能为空");
    if (device.sshUser() == null || device.sshUser().isBlank()) {
      throw new IllegalStateException("设备未配置 SSH 用户名");
    }
    if (device.sshPwd() == null || device.sshPwd().isBlank()) {
      throw new IllegalStateException("设备未配置 SSH 密码");
    }
    return execute(device.ip(), device.sshUser(), device.sshPwd(), command);
  }

  public String execute(String host, String username, String password, String command) {
    Session session = null;
    ChannelExec channel = null;
    try {
      JSch jsch = new JSch();
      session = jsch.getSession(username, host, sshPort);
      session.setPassword(password);
      Properties config = new Properties();
      config.put("StrictHostKeyChecking", "no");
      session.setConfig(config);
      session.connect(sshConnectTimeoutMs);

      channel = (ChannelExec) session.openChannel("exec");
      channel.setCommand(command);
      channel.setInputStream(null);

      InputStream stdout = channel.getInputStream();
      InputStream stderr = channel.getExtInputStream();
      channel.connect(sshCommandTimeoutMs);

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ByteArrayOutputStream err = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      long deadline = System.currentTimeMillis() + sshCommandTimeoutMs;

      while (true) {
        readAvailable(stdout, out, buffer);
        readAvailable(stderr, err, buffer);
        if (channel.isClosed()) {
          readAvailable(stdout, out, buffer);
          readAvailable(stderr, err, buffer);
          break;
        }
        if (System.currentTimeMillis() > deadline) {
          throw new IllegalStateException("SSH 命令执行超时");
        }
        Thread.sleep(100);
      }

      String stdoutText = decode(out).trim();
      String stderrText = decode(err).trim();
      int exitCode = channel.getExitStatus();
      if (exitCode != 0) {
        String detail = !stderrText.isBlank() ? stderrText : stdoutText;
        if (detail.isBlank()) detail = "远程命令执行失败，退出码=" + exitCode;
        throw new IllegalStateException(detail);
      }
      return stdoutText;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("SSH 命令执行被中断", e);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("SSH 命令执行失败: " + e.getMessage(), e);
    } finally {
      if (channel != null) channel.disconnect();
      if (session != null) session.disconnect();
    }
  }

  private String decode(ByteArrayOutputStream out) {
    return new String(out.toByteArray(), outputCharset);
  }

  private static void readAvailable(InputStream in, ByteArrayOutputStream out, byte[] buffer) throws Exception {
    if (in == null) return;
    while (in.available() > 0) {
      int n = in.read(buffer, 0, buffer.length);
      if (n < 0) break;
      out.write(buffer, 0, n);
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
