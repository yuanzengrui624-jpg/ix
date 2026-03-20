package com.netmgmt.server.dao;

import com.netmgmt.server.model.Alarm;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class AlarmDao {
  private final DataSource ds;

  public AlarmDao(DataSource ds) {
    this.ds = ds;
  }

  public long insert(long deviceId, String type, String content, LocalDateTime createTime) throws SQLException {
    String sql = """
        INSERT INTO alarm (device_id, type, content, create_time, acknowledged, ack_time)
        VALUES (?, ?, ?, ?, 0, NULL)
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
        SELECT id, device_id, type, content, create_time, acknowledged, ack_time
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

  private static Alarm map(ResultSet rs) throws SQLException {
    Timestamp ackTs = rs.getTimestamp("ack_time");
    return new Alarm(
        rs.getLong("id"),
        rs.getLong("device_id"),
        rs.getString("type"),
        rs.getString("content"),
        rs.getTimestamp("create_time").toLocalDateTime(),
        rs.getInt("acknowledged") == 1,
        ackTs == null ? null : ackTs.toLocalDateTime()
    );
  }
}

