CREATE DATABASE IF NOT EXISTS net_manage
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE net_manage;

CREATE TABLE IF NOT EXISTS device (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  ip           VARCHAR(45)      NOT NULL,
  type         VARCHAR(32)      NOT NULL,
  community    VARCHAR(128)     NULL,
  ssh_user     VARCHAR(64)      NULL,
  ssh_pwd      VARCHAR(128)     NULL,
  status       TINYINT          NOT NULL DEFAULT 0,
  last_update  DATETIME         NULL,
  create_time  DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_device_ip (ip),
  KEY idx_device_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS monitor_log (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  device_id        BIGINT UNSIGNED NOT NULL,
  cpu              DECIMAL(5,2)     NULL,
  mem              DECIMAL(5,2)     NULL,
  ping_status      TINYINT          NOT NULL,
  interface_status JSON             NULL,
  collect_time     DATETIME         NOT NULL,
  PRIMARY KEY (id),
  KEY idx_ml_device_time (device_id, collect_time),
  KEY idx_ml_time (collect_time),
  CONSTRAINT fk_ml_device FOREIGN KEY (device_id) REFERENCES device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS alarm (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  device_id     BIGINT UNSIGNED NOT NULL,
  type          VARCHAR(32)      NOT NULL,
  content       VARCHAR(512)     NOT NULL,
  create_time   DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  acknowledged  TINYINT          NOT NULL DEFAULT 0,
  ack_time      DATETIME         NULL,
  recovered     TINYINT          NOT NULL DEFAULT 0,
  recover_time  DATETIME         NULL,
  PRIMARY KEY (id),
  KEY idx_alarm_device_time (device_id, create_time),
  KEY idx_alarm_ack (acknowledged, create_time),
  CONSTRAINT fk_alarm_device FOREIGN KEY (device_id) REFERENCES device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS config_backup (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  device_id   BIGINT UNSIGNED NOT NULL,
  file_path   VARCHAR(512)     NULL,
  content     TEXT             NOT NULL,
  backup_time DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_cb_device_time (device_id, backup_time),
  CONSTRAINT fk_cb_device FOREIGN KEY (device_id) REFERENCES device(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
