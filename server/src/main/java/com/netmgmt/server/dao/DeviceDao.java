package com.netmgmt.server.dao;

import com.netmgmt.server.model.Device;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DeviceDao {
  private final DataSource ds;

  public DeviceDao(DataSource ds) {
    this.ds = ds;
  }

  public List<Device> listAll() throws SQLException {
    String sql = """
        SELECT id, ip, type, community, ssh_user, ssh_pwd, status, last_update, create_time
        FROM device
        ORDER BY id DESC
        """;
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      List<Device> out = new ArrayList<>();
      while (rs.next()) out.add(map(rs));
      return out;
    }
  }

  public Optional<Device> findById(long id) throws SQLException {
    String sql = """
        SELECT id, ip, type, community, ssh_user, ssh_pwd, status, last_update, create_time
        FROM device WHERE id = ?
        """;
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setLong(1, id);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return Optional.empty();
        return Optional.of(map(rs));
      }
    }
  }

  public long insert(String ip, String type, String community, String sshUser, String sshPwd) throws SQLException {
    String sql = """
        INSERT INTO device (ip, type, community, ssh_user, ssh_pwd, status, last_update)
        VALUES (?, ?, ?, ?, ?, 0, NOW())
        """;
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      ps.setString(1, ip);
      ps.setString(2, type);
      ps.setString(3, community);
      ps.setString(4, sshUser);
      ps.setString(5, sshPwd);
      ps.executeUpdate();
      try (ResultSet rs = ps.getGeneratedKeys()) {
        if (rs.next()) return rs.getLong(1);
      }
      throw new SQLException("Failed to get generated id");
    }
  }

  public boolean update(long id, String ip, String type, String community, String sshUser, String sshPwd) throws SQLException {
    String sql = """
        UPDATE device
        SET ip = ?, type = ?, community = ?, ssh_user = ?, ssh_pwd = ?, last_update = NOW()
        WHERE id = ?
        """;
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setString(1, ip);
      ps.setString(2, type);
      ps.setString(3, community);
      ps.setString(4, sshUser);
      ps.setString(5, sshPwd);
      ps.setLong(6, id);
      return ps.executeUpdate() > 0;
    }
  }

  public boolean delete(long id) throws SQLException {
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement("DELETE FROM device WHERE id = ?")) {
      ps.setLong(1, id);
      return ps.executeUpdate() > 0;
    }
  }

  public void updateStatus(long id, int status, LocalDateTime lastUpdate) throws SQLException {
    String sql = "UPDATE device SET status = ?, last_update = ? WHERE id = ?";
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, status);
      ps.setTimestamp(2, Timestamp.valueOf(lastUpdate));
      ps.setLong(3, id);
      ps.executeUpdate();
    }
  }

  private static Device map(ResultSet rs) throws SQLException {
    return new Device(
        rs.getLong("id"),
        rs.getString("ip"),
        rs.getString("type"),
        rs.getString("community"),
        rs.getString("ssh_user"),
        rs.getString("ssh_pwd"),
        rs.getInt("status"),
        toLdt(rs.getTimestamp("last_update")),
        toLdt(rs.getTimestamp("create_time"))
    );
  }

  private static LocalDateTime toLdt(Timestamp ts) {
    return ts == null ? null : ts.toLocalDateTime();
  }
}

