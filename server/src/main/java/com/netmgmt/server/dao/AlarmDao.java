package com.netmgmt.server.dao;

import com.netmgmt.server.model.Alarm;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AlarmDao {
  private final DataSource ds;

  public AlarmDao(DataSource ds) {
    this.ds = ds;
  }

  public boolean hasRecentUnacked(long deviceId, String type, int withinMinutes) throws SQLException {
    String sql = """
        SELECT COUNT(*)
        FROM alarm
        WHERE device_id = ?
          AND type = ?
          AND acknowledged = 0
          AND recovered = 0
          AND create_time > DATE_SUB(?, INTERVAL ? MINUTE)
        """;
    try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setLong(1, deviceId);
      ps.setString(2, type);
      ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
      ps.setInt(4, withinMinutes);
      try (ResultSet rs = ps.executeQuery()) {
        rs.next();
        return rs.getInt(1) > 0;
      }
    }
  }

  public long insert(long deviceId, String type, String content, LocalDateTime createTime) throws SQLException {
    String sql = """
        INSERT INTO alarm (device_id, type, content, create_time, acknowledged, ack_time, recovered, recover_time)
        VALUES (?, ?, ?, ?, 0, NULL, 0, NULL)
        """;
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      ps.setLong(1, deviceId);
      ps.setString(2, type);
      ps.setString(3, content);
      ps.setTimestamp(4, Timestamp.valueOf(createTime));
      ps.executeUpdate();
      try (ResultSet rs = ps.getGeneratedKeys()) {
        if (rs.next()) return rs.getLong(1);
      }
      throw new SQLException("Failed to get generated id");
    }
  }

  public boolean acknowledge(long alarmId, LocalDateTime ackTime) throws SQLException {
    String sql = "UPDATE alarm SET acknowledged = 1, ack_time = ? WHERE id = ?";
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setTimestamp(1, Timestamp.valueOf(ackTime));
      ps.setLong(2, alarmId);
      return ps.executeUpdate() > 0;
    }
  }

  public List<Alarm> listRecent(int limit) throws SQLException {
    String sql = """
        SELECT id, device_id, type, content, create_time, acknowledged, ack_time, recovered, recover_time
        FROM alarm
        ORDER BY create_time DESC
        LIMIT ?
        """;
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setInt(1, limit);
      try (ResultSet rs = ps.executeQuery()) {
        List<Alarm> out = new ArrayList<>();
        while (rs.next()) out.add(map(rs));
        return out;
      }
    }
  }

  public int recoverByDevice(long deviceId, String type) throws SQLException {
    String sql = """
        UPDATE alarm
        SET acknowledged = 1,
            ack_time = COALESCE(ack_time, NOW()),
            recovered = 1,
            recover_time = NOW()
        WHERE device_id = ?
          AND type = ?
          AND recovered = 0
        """;
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setLong(1, deviceId);
      ps.setString(2, type);
      return ps.executeUpdate();
    }
  }

  public int countUnacked() throws SQLException {
    String sql = "SELECT COUNT(*) FROM alarm WHERE acknowledged = 0 AND recovered = 0";
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      rs.next();
      return rs.getInt(1);
    }
  }

  public List<Map<String, Object>> alarmTrend7Days() throws SQLException {
    String sql = """
        SELECT DATE(create_time) AS day, COUNT(*) AS cnt
        FROM alarm
        WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
        GROUP BY DATE(create_time)
        ORDER BY day
        """;
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      List<Map<String, Object>> out = new ArrayList<>();
      while (rs.next()) {
        out.add(Map.of("day", rs.getString("day"), "count", rs.getInt("cnt")));
      }
      return out;
    }
  }

  private static Alarm map(ResultSet rs) throws SQLException {
    Timestamp ackTs = rs.getTimestamp("ack_time");
    Timestamp recoverTs = rs.getTimestamp("recover_time");
    return new Alarm(
        rs.getLong("id"),
        rs.getLong("device_id"),
        rs.getString("type"),
        rs.getString("content"),
        rs.getTimestamp("create_time").toLocalDateTime(),
        rs.getInt("acknowledged") == 1,
        ackTs == null ? null : ackTs.toLocalDateTime(),
        rs.getInt("recovered") == 1,
        recoverTs == null ? null : recoverTs.toLocalDateTime()
    );
  }
}

