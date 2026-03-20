package com.netmgmt.server.db;

import com.netmgmt.server.config.Props;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

public final class DataSourceFactory {
  private DataSourceFactory() {}

  public static DataSource create(Props props) {
    HikariConfig cfg = new HikariConfig();
    cfg.setJdbcUrl(props.getString("db.jdbcUrl"));
    cfg.setUsername(props.getString("db.username"));
    cfg.setPassword(props.getString("db.password"));
    cfg.setMaximumPoolSize(props.getInt("db.maximumPoolSize", 10));
    cfg.setPoolName("netmgmt-hikari");
    cfg.setAutoCommit(true);
    cfg.addDataSourceProperty("cachePrepStmts", "true");
    cfg.addDataSourceProperty("prepStmtCacheSize", "250");
    cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    return new HikariDataSource(cfg);
  }
}

