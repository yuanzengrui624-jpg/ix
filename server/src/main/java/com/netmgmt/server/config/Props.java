package com.netmgmt.server.config;

import java.io.InputStream;
import java.util.Properties;

public final class Props {
  private final Properties props;

  private Props(Properties props) {
    this.props = props;
  }

  public static Props load() {
    Properties p = new Properties();
    try (InputStream in = Props.class.getClassLoader().getResourceAsStream("application.properties")) {
      if (in == null) {
        throw new IllegalStateException("application.properties not found");
      }
      p.load(in);
      return new Props(p);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load application.properties", e);
    }
  }

  public String getString(String key) {
    String v = props.getProperty(key);
    if (v == null) throw new IllegalStateException("Missing property: " + key);
    return v;
  }

  public String getString(String key, String def) {
    String v = props.getProperty(key);
    return v == null ? def : v;
  }

  public int getInt(String key) {
    return Integer.parseInt(getString(key));
  }

  public int getInt(String key, int def) {
    String v = props.getProperty(key);
    return v == null ? def : Integer.parseInt(v);
  }

  public long getLong(String key, long def) {
    String v = props.getProperty(key);
    return v == null ? def : Long.parseLong(v);
  }

  public double getDouble(String key, double def) {
    String v = props.getProperty(key);
    return v == null ? def : Double.parseDouble(v);
  }
}

