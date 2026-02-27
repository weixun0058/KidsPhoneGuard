@echo off
chcp 65001 >nul
echo === KidsPhoneGuard 调试构建 ===
echo.

REM 配置 Java 环境
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Java 环境:
echo   JAVA_HOME: %JAVA_HOME%
echo.

REM 验证 Java
java -version 2>&1 | findstr "version" >nul
if errorlevel 1 (
    echo [错误] Java 未找到，请检查 Android Studio 安装
    pause
    exit /b 1
)

echo Java 版本:
java -version 2>&1 | findstr "version"
echo.

REM 执行构建
echo 开始构建 APK...
echo.

call .\gradle\wrapper\gradle-8.4\bin\gradle.bat assembleDebug

if %ERRORLEVEL% == 0 (
    echo.
    echo === 构建成功! ===
    echo.
    echo APK 位置:
    echo   app\build\outputs\apk\debug\app-debug.apk
    echo.
    
    REM 显示文件大小
    for %%F in ("app\build\outputs\apk\debug\app-debug.apk") do (
        echo APK 大小: %%~zF 字节
    )
) else (
    echo.
    echo === 构建失败 ===
    echo 请检查错误信息
)

echo.
pause
