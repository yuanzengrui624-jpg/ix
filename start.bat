@echo off
setlocal EnableExtensions DisableDelayedExpansion
cd /d "%~dp0"

set "PROJECT_ROOT=%~dp0"
set "APP_PROPS=%PROJECT_ROOT%server\src\main\resources\application.properties"
set "INIT_SQL=%PROJECT_ROOT%sql\windows_init.sql"
set "HELPER_PS=%PROJECT_ROOT%windows-db-helper.ps1"
set "BACKUP_DIR=%PROJECT_ROOT%db-backups"

echo.
echo ==============================================
echo   网络设备管理系统 Windows 一键启动工具
echo ==============================================
echo.
echo 这个脚本会自动判断数据库状态：
echo 没有库就自动初始化，旧库就自动升级，正常库就直接启动。
echo 旧库升级前会自动备份到 db-backups 文件夹。
echo.

call :checkJava
if errorlevel 1 goto end
call :checkMaven
if errorlevel 1 goto end
call :checkMySql
if errorlevel 1 goto end
call :checkPowerShell
if errorlevel 1 goto end
echo.
call :loadCurrentDbConfig
set "NETMGMT_DB_USER=%CURRENT_DB_USER%"
set "NETMGMT_DB_PASS=%CURRENT_DB_PASS%"

echo 默认使用项目里的 MySQL 账号密码。
echo 当前账号：%NETMGMT_DB_USER%

echo.
echo 正在写入数据库配置...
call :runHelper update-config silent
if errorlevel 1 goto askDbCredentials

echo.
echo 正在自动检查数据库状态...
call :runHelper prepare-db
if errorlevel 1 goto fail

echo.
echo ========== 编译项目 ==========
call mvn clean compile -q
if %errorlevel% neq 0 (
    echo 编译失败！
    pause
    goto end
)

echo.
echo ========== 启动服务端 ==========
start "NetMgmt-Server" cmd /k "cd /d ""%PROJECT_ROOT%"" && mvn -pl server exec:java -Dexec.mainClass=com.netmgmt.server.ServerMain"

echo 等待服务端启动...
timeout /t 5 /nobreak >nul

echo.
echo ========== 启动客户端 ==========
start "NetMgmt-Client" cmd /k "cd /d ""%PROJECT_ROOT%"" && mvn -pl client exec:java -Dexec.mainClass=com.netmgmt.client.ClientMain"

echo.
echo 启动完成！
echo 客户端连接地址：127.0.0.1
echo 客户端连接端口：8888
pause
goto end

:askDbCredentials
echo.
echo 项目默认 MySQL 账号密码连接失败，请手动输入一次。
set /p NETMGMT_DB_USER=请输入 MySQL 用户名（直接回车默认 root）：
if "%NETMGMT_DB_USER%"=="" set "NETMGMT_DB_USER=root"
set /p NETMGMT_DB_PASS=请输入 MySQL 密码（直接回车默认 123456）：
if "%NETMGMT_DB_PASS%"=="" set "NETMGMT_DB_PASS=123456"

echo.
echo 正在写入数据库配置...
call :runHelper update-config
if errorlevel 1 goto fail

echo.
echo 正在自动检查数据库状态...
call :runHelper prepare-db
if errorlevel 1 goto fail

echo.
echo ========== 编译项目 ==========
call mvn clean compile -q
if %errorlevel% neq 0 (
    echo 编译失败！
    pause
    goto end
)

echo.
echo ========== 启动服务端 ==========
start "NetMgmt-Server" cmd /k "cd /d ""%PROJECT_ROOT%"" && mvn -pl server exec:java -Dexec.mainClass=com.netmgmt.server.ServerMain"

echo 等待服务端启动...
timeout /t 5 /nobreak >nul

echo.
echo ========== 启动客户端 ==========
start "NetMgmt-Client" cmd /k "cd /d ""%PROJECT_ROOT%"" && mvn -pl client exec:java -Dexec.mainClass=com.netmgmt.client.ClientMain"

echo.
echo 启动完成！
echo 客户端连接地址：127.0.0.1
echo 客户端连接端口：8888
pause
goto end

:checkJava
java -version >nul 2>&1
if errorlevel 1 (
    echo 未检测到 Java，请先安装 JDK 17 并配置环境变量。
    pause
    exit /b 1
)
exit /b 0

:checkMaven
mvn -version >nul 2>&1
if errorlevel 1 (
    echo 未检测到 Maven，请先安装 Maven 并配置环境变量。
    pause
    exit /b 1
)
exit /b 0

:checkMySql
mysql --version >nul 2>&1
if errorlevel 1 (
    echo 未检测到 MySQL 命令行工具，请先安装 MySQL 并配置环境变量。
    pause
    exit /b 1
)
exit /b 0

:checkPowerShell
powershell -NoProfile -Command "exit 0" >nul 2>&1
if errorlevel 1 (
    echo 未检测到 PowerShell，无法继续执行自动化配置。
    pause
    exit /b 1
)
exit /b 0

:loadCurrentDbConfig
set "CURRENT_DB_USER="
set "CURRENT_DB_PASS="
for /f "usebackq delims=" %%A in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$p=$env:APP_PROPS; $line=Get-Content -Path $p | Where-Object { $_ -like 'db.username=*' } | Select-Object -First 1; if($line){$line.Split('=',2)[1]}"`) do set "CURRENT_DB_USER=%%A"
for /f "usebackq delims=" %%A in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$p=$env:APP_PROPS; $line=Get-Content -Path $p | Where-Object { $_ -like 'db.password=*' } | Select-Object -First 1; if($line){$line.Split('=',2)[1]}"`) do set "CURRENT_DB_PASS=%%A"
if "%CURRENT_DB_USER%"=="" set "CURRENT_DB_USER=root"
if "%CURRENT_DB_PASS%"=="" set "CURRENT_DB_PASS=123456"
exit /b 0

:runHelper
powershell -NoProfile -ExecutionPolicy Bypass -File "%HELPER_PS%" -Action %1 -AppPropsPath "%APP_PROPS%" -InitSqlPath "%INIT_SQL%" -BackupDir "%BACKUP_DIR%"
if errorlevel 1 (
    if /i not "%2"=="silent" (
        echo.
        echo 自动处理失败，请检查 MySQL 服务、账号密码是否正确。
    )
    exit /b 1
)
exit /b 0

:fail
echo.
echo 处理失败，脚本已停止。
pause

:end
endlocal