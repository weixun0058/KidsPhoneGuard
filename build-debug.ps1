# 儿童手机守护应用 - 调试构建脚本
# 自动配置 Java 环境并构建 APK

Write-Host "=== KidsPhoneGuard Debug Build ===" -ForegroundColor Green
Write-Host ""

# Configure Java environment
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:PATH = $env:JAVA_HOME + '\bin;' + $env:PATH

Write-Host "Java Environment:" -ForegroundColor Yellow
Write-Host "  JAVA_HOME: $env:JAVA_HOME" -ForegroundColor Gray
Write-Host ""

# Verify Java
$javaVersion = java -version 2>&1 | Select-String -Pattern '"(\d+).*"' | ForEach-Object { $_.Matches.Groups[1].Value }
Write-Host "Java Version: $javaVersion" -ForegroundColor Cyan
Write-Host ""

# Execute build
Write-Host "Starting APK build..." -ForegroundColor Green
Write-Host ""

& .\gradle\wrapper\gradle-8.4\bin\gradle.bat assembleDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "=== Build Successful! ===" -ForegroundColor Green
    Write-Host ""
    Write-Host "APK Location:" -ForegroundColor Yellow
    Write-Host "  app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Cyan
    Write-Host ""
    
    # Show APK file info
    $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
    if (Test-Path $apkPath) {
        $apkSize = (Get-Item $apkPath).Length / 1MB
        Write-Host "APK Size: $($apkSize.ToString('F2')) MB" -ForegroundColor Gray
    }
} else {
    Write-Host ""
    Write-Host "=== Build Failed ===" -ForegroundColor Red
    Write-Host "Please check error messages" -ForegroundColor Red
}
