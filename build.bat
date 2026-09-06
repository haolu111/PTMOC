@echo off
chcp 65001 >nul
echo ========================================
echo    PTMOC 项目编译脚本
echo ========================================
echo.

REM Auto-detect Java environment
set "FOUND_JDK=0"

if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot\bin\javac.exe" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
    set "FOUND_JDK=1"
)

if %FOUND_JDK%==0 (
    if exist "C:\jdk17\bin\javac.exe" (
        set "JAVA_HOME=C:\jdk17"
        set "FOUND_JDK=1"
    )
)

if %FOUND_JDK%==0 (
    if exist "D:\jdk17\bin\javac.exe" (
        set "JAVA_HOME=D:\jdk17"
        set "FOUND_JDK=1"
    )
)

if %FOUND_JDK%==0 (
    for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-*") do (
        if exist "%%i\bin\javac.exe" (
            set "JAVA_HOME=%%i"
            set "FOUND_JDK=1"
        )
    )
)

if %FOUND_JDK%==0 (
    echo 错误：未找到JDK！请先运行 setup.bat 安装JDK
    pause
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
echo 使用 JAVA_HOME=%JAVA_HOME%
echo.

REM Compile model modules (no dependencies)
echo 编译 model 模块...
javac -cp src -d bin src/com/ptmoc/model/Entity.java src/com/ptmoc/model/KeyPair.java src/com/ptmoc/model/Polynomial.java src/com/ptmoc/model/PublicParameters.java src/com/ptmoc/model/package-info.java
if %errorlevel% neq 0 (
    echo Model 模块编译失败！
    pause
    exit /b 1
)

REM Compile utility modules (no dependencies)
echo 编译 util 模块...
javac -cp src -d bin src/com/ptmoc/util/CryptoUtil.java src/com/ptmoc/util/MathUtil.java src/com/ptmoc/util/package-info.java
if %errorlevel% neq 0 (
    echo Utility 模块编译失败！
    pause
    exit /b 1
)

REM Compile core modules (depends on model and util)
echo 编译 core 模块...
javac -cp src -d bin src/com/ptmoc/core/BaseModule.java src/com/ptmoc/core/CryptoModule.java src/com/ptmoc/core/EvalModule.java src/com/ptmoc/core/package-info.java
if %errorlevel% neq 0 (
    echo Core 模块编译失败！
    pause
    exit /b 1
)

REM Compile test modules (depends on core)
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
echo 运行测试...
echo.
java -cp bin com.ptmoc.test.SimpleTest

echo.
echo ========================================
echo    编译和测试完成！
echo ========================================
pause
