@echo off
REM SoHook 测试运行脚本

echo ========================================
echo SoHook Android Instrumentation Tests
echo ========================================
echo.

REM 检查是否有设备连接
echo [1/5] 检查设备连接...
adb devices | findstr "device$" >nul
if errorlevel 1 (
    echo [错误] 没有检测到 Android 设备或模拟器
    echo 请连接设备或启动模拟器后重试
    pause
    exit /b 1
)
echo [✓] 设备已连接
echo.

REM 编译项目
echo [2/5] 编译 SoHook...
call gradlew :sohook:assembleDebug :sohook:assembleDebugAndroidTest
if errorlevel 1 (
    echo [错误] 编译失败
    pause
    exit /b 1
)
echo [✓] 编译成功
echo.

REM 安装 APK
echo [3/5] 安装测试 APK...
call gradlew :sohook:installDebug :sohook:installDebugAndroidTest
if errorlevel 1 (
    echo [错误] 安装失败
    pause
    exit /b 1
)
echo [✓] 安装成功
echo.

REM 授予权限
echo [4/5] 授予必要权限...
adb shell pm grant com.sohook android.permission.WRITE_EXTERNAL_STORAGE 2>nul
adb shell pm grant com.sohook android.permission.READ_EXTERNAL_STORAGE 2>nul
echo [✓] 权限已授予
echo.

REM 运行测试
echo [5/5] 运行测试...
echo.
echo ========================================
echo 开始测试...
echo ========================================
echo.

if "%1"=="" (
    REM 运行所有测试
    call gradlew :sohook:connectedAndroidTest
) else if "%1"=="basic" (
    REM 运行基础测试
    echo 运行基础功能测试...
    call gradlew :sohook:connectedAndroidTest --tests "com.sohook.SoHookBasicTest"
) else if "%1"=="leak" (
    REM 运行泄漏检测测试
    echo 运行内存泄漏检测测试...
    call gradlew :sohook:connectedAndroidTest --tests "com.sohook.SoHookMemoryLeakTest"
) else if "%1"=="stress" (
    REM 运行压力测试
    echo 运行压力测试...
    call gradlew :sohook:connectedAndroidTest --tests "com.sohook.SoHookStressTest"
) else (
    REM 运行指定的测试
    echo 运行测试: %1
    call gradlew :sohook:connectedAndroidTest --tests "%1"
)

if errorlevel 1 (
    echo.
    echo ========================================
    echo [失败] 测试未通过
    echo ========================================
    pause
    exit /b 1
)

echo.
echo ========================================
echo [成功] 所有测试通过！
echo ========================================
echo.

REM 显示测试报告位置
echo 测试报告位置:
echo   HTML: sohook\build\reports\androidTests\connected\index.html
echo   XML:  sohook\build\outputs\androidTest-results\connected\
echo.

pause
