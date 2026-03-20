## 完整开发计划（按实现要求文档落地）

### 0. 项目基线（已完成）
- **目标**：搭建可运行的 Java 17 C/S 项目，落 MySQL 库表并跑通设备管理与基础监控闭环。
- **产物**：
  - Maven 多模块：`common` / `server` / `client`
  - MySQL 建表脚本：`sql/schema.sql`
  - Socket JSON 协议与接口：设备/告警/监控日志
  - JavaFX 客户端：专业蓝灰主题（无紫色）

### 1. 数据库层（已完成）
- **表结构**（对应实现要求文档“设备表/监控日志表/告警表/配置备份表”）：
  - `device`
  - `monitor_log`
  - `alarm`
  - `config_backup`
- **索引**：
  - `device.ip` 唯一
  - `monitor_log(device_id, collect_time)` 支持区间查询与最新查询
  - `alarm(device_id, create_time)`、`alarm(acknowledged, create_time)` 支持告警列表与筛选
- **30天留存策略（计划）**：
  - 应用层定时任务或 MySQL Event 每天清理 `monitor_log` 30天前数据（后续阶段实现）

### 2. 服务端（已完成第一版）
- **通信模块（Socket）**：
  - 协议：一行一个 JSON，请求 `ApiRequest`，响应 `ApiResponse`
  - 支持动作：
    - `DEVICE_LIST / DEVICE_ADD / DEVICE_UPDATE / DEVICE_DELETE`
    - `ALARM_LIST / ALARM_ACK`
    - `MONITOR_LATEST / MONITOR_QUERY`
    - `MONITOR_RUN_ONCE`
- **数据库交互**：
  - JDBC + HikariCP
  - DAO：`DeviceDao / MonitorLogDao / AlarmDao`
- **监控模块（第一阶段：Ping）**：
  - 调度器：每 `monitor.intervalSeconds` 秒轮询所有设备
  - Ping：`InetAddress.isReachable(timeout)`
  - 落库：写 `monitor_log`、更新 `device.status`
  - 告警：Ping失败插入 `alarm(type=OFFLINE)`

### 3. 客户端（已完成第一版）
- **UI框架**：JavaFX
- **主题**：蓝灰专业风格（`client/src/main/resources/styles/app.css`），避免紫色系
- **页面**：
  - 设备管理：列表、添加/修改/删除、触发一次采集
  - 告警中心：列表、确认告警
  - 监控日志：按设备+日期范围查询、查看最新一次

### 4. 第二阶段：SNMP/接口状态（计划）
- **目标**：按文档要求接入 SNMP4J 采集 CPU/内存/接口状态，填充 `monitor_log` 的 `cpu/mem/interface_status(JSON)`
- **步骤**：
  - 引入 `SNMP4J`（server 模块）
  - 定义 OID 配置（CPU、内存、接口表）
  - 超时/异常处理：设备无响应 → 视为离线 + 触发告警
  - 采集频率与线程池：为每设备分配任务，避免单线程阻塞

### 5. 第二阶段：告警完善（计划）
- **目标**：把“阈值判断、重复告警抑制、确认/恢复”做专业化
- **步骤**：
  - 引入规则表（可选）：`alarm_rule`（CPU/MEM阈值、接口down等）
  - 告警去重：相同设备+类型短时间内合并/抑制
  - 告警恢复：当指标恢复正常时标记恢复（可扩表或新增字段）

### 6. 第三阶段：配置管理（计划）
- **目标**：实现配置备份/恢复/比对（文档中的加分项）
- **步骤**：
  - 引入 JSch（SSH）
  - 备份：执行 show running-config → 保存文件 → 写 `config_backup`
  - 恢复：上传配置并执行应用（仅管理员）
  - 比对：引入 diff 工具库，对比两次备份内容

### 7. 工程化与交付（计划）
- **目标**：更稳定、更像产品
- **内容**：
  - 日志：落盘文件、分级、关键操作审计
  - 配置：数据库连接/端口/阈值/采集周期支持外部配置
  - 打包：可执行包、Windows 启动脚本
  - 测试：DAO 单测、协议集成测试

