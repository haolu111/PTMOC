@echo off
chcp 65001 >nul
echo ========================================
echo    PTMOC 项目环境配置脚本
echo ========================================
echo.

REM 检查是否已有可用的JDK
where javac >nul 2>&1
if %errorlevel% equ 0 (
    echo 检测到系统已安装 javac，验证版本...
    javac -version 2>&1
    if %errorlevel% equ 0 (
        echo JDK 可用，跳过安装步骤。
        goto :build
    )
)

echo 未检测到可用的JDK，开始安装...
echo.

REM 设置JDK安装目标目录
set JDK_DIR=C:\jdk17
if exist "%JDK_DIR%\bin\javac.exe" (
    echo 检测到 %JDK_DIR% 已存在JDK，跳过安装。
    goto :set_env
)

echo 正在下载 Eclipse Temurin JDK 17...
echo 请稍候，下载可能需要几分钟...
curl.exe -L -o "%TEMP%\jdk17.zip" "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse"
if %errorlevel% neq 0 (
    echo 下载失败！请手动下载JDK 17:
    echo https://adoptium.net/temurin/releases/?version=17^&os=windows^&arch=x64^&type=jdk
    echo 下载后解压到 C:\jdk17 目录
    pause
    exit /b 1
)

echo 下载完成，正在解压...
powershell -Command "Expand-Archive -Path '%TEMP%\jdk17.zip' -DestinationPath 'C:\' -Force"
if %errorlevel% neq 0 (
    echo 解压失败！
    pause
    exit /b 1
)

REM 将解压后的jdk-17.x.x.x目录重命名为jdk17
for /d %%i in (C:\jdk-17*) do (
    if not "%JDK_DIR%"=="%%i" (
        move "%%i" "%JDK_DIR%" >nul
    )
)

echo JDK 17 安装完成！
echo.

:set_env
set JAVA_HOME=%JDK_DIR%
set PATH=%JAVA_HOME%\bin;%PATH%

echo JAVA_HOME=%JAVA_HOME%
echo.

java -version
javac -version
echo.

:build
echo ========================================
echo    编译 PTMOC 项目
echo ========================================
echo.

REM 设置JAVA_HOME（如果没有通过上面的安装步骤设置）
if not defined JAVA_HOME (
    set JAVA_HOME=C:\jdk17
)
set PATH=%JAVA_HOME%\bin;%PATH%

REM 清理旧的编译文件
if exist bin\com del /s /q bin\com\* >nul 2>&1

REM 编译核心模块
echo 编译 model 模块...
javac -cp src -d bin src/com/ptmoc/model/Entity.java src/com/ptmoc/model/KeyPair.java src/com/ptmoc/model/Polynomial.java src/com/ptmoc/model/PublicParameters.java src/com/ptmoc/model/package-info.java
if %errorlevel% neq 0 (
    echo Model 模块编译失败！
    pause
    exit /b 1
)

echo 编译 util 模块...
javac -cp src -d bin src/com/ptmoc/util/CryptoUtil.java src/com/ptmoc/util/MathUtil.java src/com/ptmoc/util/package-info.java
if %errorlevel% neq 0 (
    echo Utility 模块编译失败！
    pause
    exit /b 1
)

echo 编译 core 模块...
javac -cp src -d bin src/com/ptmoc/core/BaseModule.java src/com/ptmoc/core/CryptoModule.java src/com/ptmoc/core/EvalModule.java src/com/ptmoc/core/package-info.java
if %errorlevel% neq 0 (
    echo Core 模块编译失败！
    pause
    exit /b 1
)

echo 编译 test 模块...
javac -cp src -d bin src/com/ptmoc/test/SimpleTest.java src/com/ptmoc/test/package-info.java
if %errorlevel% neq 0 (
    echo Test 模块编译失败！
    pause
    exit /b 1
)

echo.
echo 编译成功！
echo.

echo ========================================
echo    运行测试
echo ========================================
echo.
java -cp bin com.ptmoc.test.SimpleTest

echo.
echo ========================================
echo    完成！
echo ========================================
pause
