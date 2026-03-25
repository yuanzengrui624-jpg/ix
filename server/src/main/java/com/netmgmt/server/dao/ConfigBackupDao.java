package com.netmgmt.server.dao;

import com.netmgmt.server.config.Props;
import com.netmgmt.server.model.ConfigBackup;
import com.netmgmt.server.model.Device;
import com.netmgmt.server.ssh.LocalCommandExecutor;
import com.netmgmt.server.ssh.SshCommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigBackupDao {
  private static final Logger log = LoggerFactory.getLogger(ConfigBackupDao.class);
  private static final Set<String> LOCAL_ADDRESSES = Set.of("127.0.0.1", "localhost", "::1");

  private final DataSource ds;
  private final DeviceDao deviceDao;
  private final SshCommandExecutor sshExecutor;
  private final LocalCommandExecutor localExecutor;
  private final String backupCommand;
  private final boolean restoreDemoMode;

  public ConfigBackupDao(DataSource ds, DeviceDao deviceDao, Props props) {
    this.ds = ds;
    this.deviceDao = deviceDao;
    this.sshExecutor = new SshCommandExecutor(props);
    this.localExecutor = new LocalCommandExecutor(props);
    this.backupCommand = props.getString("config.backup.command", "ipconfig /all");
    this.restoreDemoMode = Boolean.parseBoolean(props.getString("config.restore.demoMode", "true"));
  }

  public List<ConfigBackup> listRecent(int limit) throws SQLException {
    String sql = "SELECT id, device_id, content, backup_time FROM config_backup ORDER BY backup_time DESC LIMIT ?";
    List<ConfigBackup> list = new ArrayList<>();
    try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, limit);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) list.add(map(rs));
      }
    }
    return list;
  }

  public long backupDevice(long deviceId) throws SQLException {
    Device device = deviceDao.findById(deviceId).orElse(null);
    if (device == null) throw new IllegalArgumentException("Device not found: " + deviceId);

    String config = fetchConfig(device);
    if (config == null || config.isBlank()) {
      throw new IllegalStateException("备份结果为空，请检查命令或设备配置");
    }
    LocalDateTime now = LocalDateTime.now();
    String sql = "INSERT INTO config_backup (device_id, file_path, content, backup_time) VALUES (?, ?, ?, ?)";
    try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      ps.setLong(1, deviceId);
      ps.setString(2, "backup/" + device.ip() + "/" + now.toString().replace(":", "-") + ".cfg");
      ps.setString(3, config);
      ps.setTimestamp(4, Timestamp.valueOf(now));
      ps.executeUpdate();
      try (ResultSet rs = ps.getGeneratedKeys()) {
        if (rs.next()) return rs.getLong(1);
      }
      throw new SQLException("Failed to get generated id");
    }
  }

  public int backupAll() throws SQLException {
    List<Device> devices = deviceDao.listAll();
    int count = 0;
    for (Device d : devices) {
      if (d.status() == 1) {
        try {
          backupDevice(d.id());
          count++;
        } catch (Exception e) {
          log.warn("备份设备 {} 失败: {}", d.ip(), e.getMessage());
        }
      }
    }
    return count;
  }

  public ConfigBackup getById(long id) throws SQLException {
    String sql = "SELECT id, device_id, content, backup_time FROM config_backup WHERE id = ?";
    try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setLong(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? map(rs) : null;
      }
    }
  }

  public Map<String, Object> restoreBackup(long backupId) throws SQLException {
    ConfigBackup backup = getById(backupId);
    if (backup == null) throw new IllegalArgumentException("Backup not found: " + backupId);

    Device device = deviceDao.findById(backup.deviceId()).orElse(null);
    if (device == null) throw new IllegalArgumentException("Device not found for backup: " + backup.deviceId());

    if (!restoreDemoMode) {
      throw new UnsupportedOperationException("当前版本仅支持演示级恢复，请保持 config.restore.demoMode=true");
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("success", true);
    result.put("demoMode", true);
    result.put("deviceIp", device.ip());
    result.put("deviceId", device.id());
    result.put("backupId", backupId);
    result.put("lineCount", countLines(backup.content()));
    result.put("message", "已生成恢复演示流程，当前版本不会真正下发到设备。");
    return result;
  }

  private String fetchConfig(Device device) {
    boolean isLocal = isLocalAddress(device.ip());
    boolean hasSshCredentials = device.sshUser() != null && !device.sshUser().isBlank()
        && device.sshPwd() != null && !device.sshPwd().isBlank();

    if (hasSshCredentials && !isLocal) {
      try {
        String content = sshExecutor.execute(device, backupCommand).trim();
        if (!content.isBlank()) return content;
      } catch (Exception e) {
        log.info("SSH 备份失败，尝试本机执行: {}", e.getMessage());
      }
    }

    try {
      String content = localExecutor.execute(backupCommand).trim();
      if (!content.isBlank()) return content;
      throw new IllegalStateException("本机命令执行成功，但未返回内容");
    } catch (Exception e) {
      throw new IllegalStateException("备份失败: " + e.getMessage(), e);
    }
  }

  private static boolean isLocalAddress(String ip) {
    if (ip == null) return false;
    return LOCAL_ADDRESSES.contains(ip.trim().toLowerCase());
  }

  private static ConfigBackup map(ResultSet rs) throws SQLException {
    return new ConfigBackup(
        rs.getLong("id"),
        rs.getLong("device_id"),
        rs.getString("content"),
        rs.getTimestamp("backup_time").toLocalDateTime()
    );
  }

  private static int countLines(String content) {
    if (content == null || content.isBlank()) return 0;
    return content.split("\\R", -1).length;
  }
}
