@echo off
cd /d "D:\ix-main\ix-main"

echo ========== 编译项目 ==========
call mvn clean compile
if %errorlevel% neq 0 (
    echo 编译失败！
    pause
    exit /b 1
)

echo.
echo ========== 启动服务端 ==========
start "Server" cmd /k "cd /d D:\ix-main\ix-main && mvn -pl server exec:java -Dexec.mainClass=com.netmgmt.server.ServerMain"

echo 等待服务端启动...
timeout /t 6 /nobreak >nul

echo.
echo ========== 启动客户端 ==========
start "Client" cmd /k "cd /d D:\ix-main\ix-main && mvn -pl client javafx:run"

echo.
echo 启动完成！
pause
