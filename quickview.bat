@echo off
chcp 65001 >nul
echo ========================================
echo    PTMOC Quick View
echo ========================================
echo.

echo Project Location: F:\F Download\PTMOC
echo.
echo Main Components:
echo ├── src\com\ptmoc\core\     - Core modules (BaseModule, CryptoModule, EvalModule)
echo ├── src\com\ptmoc\model\    - Data models (Entity, KeyPair, Polynomial, etc.)
echo ├── src\com\ptmoc\util\     - Utility classes (CryptoUtil, MathUtil)
echo ├── src\com\ptmoc\test\     - Test files (SimpleTest, CryptoModuleTest)
echo └── bin\                    - Compiled .class files
echo.

echo Available Commands:
echo 1. .\viewer.bat    - Interactive project viewer
echo 2. .\build.bat     - Compile and run the project
echo 3. .\README.md     - Detailed documentation
echo.

echo Quick Start:
echo 1. Double-click build.bat to compile and run
echo 2. Double-click viewer.bat for interactive menu
echo 3. Open README.md for detailed instructions
echo.

echo Current Status:
if exist bin\com\ptmoc\core\BaseModule.class (
    echo ✓ Project is compiled and ready to run
) else (
    echo ✗ Project needs to be compiled first
    echo   Run: .\build.bat
)
echo.

pause








