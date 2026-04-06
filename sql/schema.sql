-- MySQL 8+ schema for Net Device Management (C/S)

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

ALTER TABLE alarm
  ADD COLUMN IF NOT EXISTS recovered TINYINT NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS recover_time DATETIME NULL;

-- 演示设备种子数据：使用 127.0.0.x 回环地址，方便本机演示时直接看到多台设备
-- INSERT IGNORE 不会覆盖用户已有的真实设备配置
INSERT IGNORE INTO device (ip, type, community, ssh_user, ssh_pwd, status, last_update) VALUES
  ('127.0.0.2', 'router',   'public', NULL, NULL, 1, NOW()),
  ('127.0.0.3', 'switch',   'public', NULL, NULL, 1, NOW()),
  ('127.0.0.4', 'firewall', 'public', NULL, NULL, 1, NOW()),
  ('127.0.0.5', 'ap',       'public', NULL, NULL, 1, NOW()),
  ('127.0.0.6', 'server',   'public', NULL, NULL, 1, NOW());

