# 基于Java的网络设备管理系统（C/S）

## 技术栈
- Java: **JDK 17**
- 构建: Maven（多模块 `common` / `server` / `client`）
- 数据库: **MySQL 8+**
- 通信: Socket + 一行一条 JSON（请求/响应）
- 客户端: JavaFX（专业蓝灰主题，**无紫色配色**）

## 目录结构
- `sql/schema.sql`: 建库建表脚本
- `common/`: 协议与JSON工具
- `server/`: 服务端（Socket API + MySQL + 定时Ping采集）
- `client/`: 客户端（设备管理/告警中心/监控日志）

## 运行前准备
1. 安装并启动 MySQL 8+
2. 执行建表脚本 `sql/schema.sql`
3. 修改服务端数据库连接配置
   - 文件：`server/src/main/resources/application.properties`
   - 默认值：
     - `db.username=root`
     - `db.password=123456`
     - `db.jdbcUrl=jdbc:mysql://127.0.0.1:3306/net_manage...`

## 构建
在项目根目录运行：

```bash
mvn -DskipTests package
```

## 启动服务端
方式1（推荐，直接跑 main）：

```bash
mvn -pl server exec:java -Dexec.mainClass=com.netmgmt.server.ServerMain
```

方式2（先打包再运行 jar）：

```bash
java -jar server/target/server-1.0.0.jar
```

默认端口：`8888`

## 启动客户端（JavaFX）

```bash
mvn -pl client javafx:run
```

默认连接：`127.0.0.1:8888`（写在 `client/src/main/java/com/netmgmt/client/NetMgmtApp.java`）

## 使用说明（最小闭环）
- 在“设备管理”中添加设备 IP（局域网主机/网关等）
- 点击“立即采集一次(Ping)”或等待后台周期采集（默认30秒）
- “告警中心”可看到离线告警，并可确认
- “监控日志”可按设备/日期范围查询 Ping 记录

