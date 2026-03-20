package com.netmgmt.server.dao;

import com.netmgmt.server.model.MonitorLog;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class MonitorLogDao {
  private final DataSource ds;

  public MonitorLogDao(DataSource ds) {
    this.ds = ds;
  }

  public long insert(long deviceId, Double cpu, Double mem, int pingStatus, String interfaceJson, LocalDateTime collectTime) throws SQLException {
    String sql = """
        INSERT INTO monitor_log (device_id, cpu, mem, ping_status, interface_status, collect_time)
        VALUES (?, ?, ?, ?, ?, ?)
        """;
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      ps.setLong(1, deviceId);
      if (cpu == null) ps.setNull(2, Types.DECIMAL); else ps.setDouble(2, cpu);
      if (mem == null) ps.setNull(3, Types.DECIMAL); else ps.setDouble(3, mem);
      ps.setInt(4, pingStatus);
      if (interfaceJson == null) ps.setNull(5, Types.VARCHAR); else ps.setString(5, interfaceJson);
      ps.setTimestamp(6, Timestamp.valueOf(collectTime));
      ps.executeUpdate();
      try (ResultSet rs = ps.getGeneratedKeys()) {
        if (rs.next()) return rs.getLong(1);
      }
      throw new SQLException("Failed to get generated id");
    }
  }

  public List<MonitorLog> queryByDeviceAndTime(long deviceId, LocalDateTime start, LocalDateTime end, int limit) throws SQLException {
    String sql = """
        SELECT id, device_id, cpu, mem, ping_status, interface_status, collect_time
        FROM monitor_log
        WHERE device_id = ? AND collect_time BETWEEN ? AND ?
        ORDER BY collect_time DESC
        LIMIT ?
        """;
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setLong(1, deviceId);
      ps.setTimestamp(2, Timestamp.valueOf(start));
      ps.setTimestamp(3, Timestamp.valueOf(end));
      ps.setInt(4, limit);
      try (ResultSet rs = ps.executeQuery()) {
        List<MonitorLog> out = new ArrayList<>();
        while (rs.next()) out.add(map(rs));
        return out;
      }
    }
  }

  public MonitorLog latestByDevice(long deviceId) throws SQLException {
    String sql = """
        SELECT id, device_id, cpu, mem, ping_status, interface_status, collect_time
        FROM monitor_log
        WHERE device_id = ?
        ORDER BY collect_time DESC
        LIMIT 1
        """;
    try (Connection c = ds.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setLong(1, deviceId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) return null;
        return map(rs);
      }
    }
  }

  private static MonitorLog map(ResultSet rs) throws SQLException {
    return new MonitorLog(
        rs.getLong("id"),
        rs.getLong("device_id"),
        (Double) rs.getObject("cpu"),
        (Double) rs.getObject("mem"),
        rs.getInt("ping_status"),
        rs.getString("interface_status"),
        rs.getTimestamp("collect_time").toLocalDateTime()
    );
  }
}

