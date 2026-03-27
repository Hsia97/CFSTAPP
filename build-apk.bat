@echo off
chcp 65001 >nul
echo ========================================
echo   Cloudflare Speed Test APK 构建脚本
echo ========================================
echo.

REM 检查是否在CFSTAPP目录
if not exist "build.gradle" (
    echo [错误] 请在 CFSTAPP 目录下运行此脚本
    pause
    exit /b 1
)

echo [步骤1] 检查环境...
echo.

REM 检查Java
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Java，请先安装 JDK 17 或更高版本
    echo 下载地址: https://adoptium.net/
    pause
    exit /b 1
)
echo [√] Java 已安装

REM 检查ANDROID_HOME
if "%ANDROID_HOME%"=="" (
    if "%ANDROID_SDK_ROOT%"=="" (
        echo [警告] 未设置 ANDROID_HOME 环境变量
        echo.
        echo 请确保已安装 Android SDK，并设置环境变量:
        echo   ANDROID_HOME = Android SDK 路径
        echo.
        echo 例如: C:\Users\你的用户名\AppData\Local\Android\Sdk
        echo.
        pause
        exit /b 1
    )
)
echo [√] Android SDK 已配置

echo.
echo [步骤2] 下载 Gradle Wrapper (首次运行需要)...
echo.

REM 创建gradlew脚本
if not exist "gradlew.bat" (
    echo 正在生成 Gradle Wrapper...
    gradle wrapper --gradle-version 8.4 2>nul
    if errorlevel 1 (
        echo [错误] Gradle wrapper 生成失败
        echo 请确保已安装 Gradle 或手动下载
        pause
        exit /b 1
    )
)

echo.
echo [步骤3] 编译项目...
echo.

call gradlew.bat assembleDebug

if errorlevel 1 (
    echo.
    echo [错误] 构建失败，请检查错误信息
    pause
    exit /b 1
)

echo.
echo ========================================
echo   构建成功！
echo ========================================
echo.
echo APK 文件位置:
echo   app\build\outputs\apk\debug\app-debug.apk
echo.
echo 你可以将此 APK 文件传输到 Android 手机安装
echo.

REM 打开输出目录
explorer "app\build\outputs\apk\debug"

pause
