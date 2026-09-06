@echo off
chcp 65001 >nul
echo ========================================
echo    PTMOC Project Viewer
echo ========================================
echo.

:menu
echo Please select an option:
echo 1. View project structure
echo 2. View source code files
echo 3. Run the project
echo 4. View compiled files
echo 5. Exit
echo.
set /p choice="Enter your choice (1-5): "

if "%choice%"=="1" goto structure
if "%choice%"=="2" goto source
if "%choice%"=="3" goto run
if "%choice%"=="4" goto compiled
if "%choice%"=="5" goto exit
echo Invalid choice. Please try again.
goto menu

:structure
echo.
echo ========================================
echo    Project Structure
echo ========================================
tree /f
echo.
pause
goto menu

:source
echo.
echo ========================================
echo    Source Code Files
echo ========================================
echo.
echo Core Modules:
echo - BaseModule.java (System initialization and key management)
echo - CryptoModule.java (Encryption/Decryption functions)
echo - EvalModule.java (Homomorphic evaluation)
echo.
echo Model Classes:
echo - Entity.java (Entity type enumeration)
echo - KeyPair.java (Key pair container)
echo - Polynomial.java (Polynomial representation)
echo - PublicParameters.java (System public parameters)
echo.
echo Utility Classes:
echo - CryptoUtil.java (Cryptographic utilities)
echo - MathUtil.java (Mathematical utilities)
echo.
echo Test Files:
echo - SimpleTest.java (Simple test without JUnit)
echo - CryptoModuleTest.java (JUnit tests - requires JUnit dependency)
echo.
pause
goto menu

:run
echo.
echo ========================================
echo    Running PTMOC Project
echo ========================================
echo.
echo Compiling and running the project...
call build.bat
echo.
pause
goto menu

:compiled
echo.
echo ========================================
echo    Compiled Files (bin directory)
echo ========================================
dir bin\com\ptmoc /s
echo.
pause
goto menu

:exit
echo.
echo Thank you for using PTMOC Project Viewer!
echo.
pause








