@echo off
chcp 65001 >nul
rem 加载本机私有密钥配置（模型 key、支付宝密钥），该文件不入库，不存在则跳过（AI 自动降级为规则引擎）
if exist "%~dp0env.local.bat" call "%~dp0env.local.bat"
cd /d %~dp0canteen-backend
echo ============================================
echo  正在启动后端 SpringBoot，端口 8080
echo  首次启动需要下载 Maven 依赖，请耐心等待
echo  启动成功后访问 http://localhost:8080/api
echo ============================================
call mvn spring-boot:run
pause
