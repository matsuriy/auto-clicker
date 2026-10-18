@echo off
rem ============================================================
rem  Auto Clicker run script (for development)
rem  Launch priority: embedded JRE + jar first, then system Java
rem  + jar, exe last (a launch4j exe without a sibling runtime
rem  pops up a Java download prompt)
rem ============================================================
setlocal EnableExtensions
cd /d "%~dp0"

rem 1. Embedded JRE present: run the jar with it, no system Java needed
if exist "target\runtime\bin\javaw.exe" if exist "target\auto-clicker.jar" (
    start "" "target\runtime\bin\javaw.exe" -jar "target\auto-clicker.jar"
    exit /b 0
)

rem 2. Only the jar was built: fall back to system Java
if exist "target\auto-clicker.jar" (
    where javaw >nul 2>&1
    if errorlevel 1 (
        echo [ERROR] Java not found. Run the build script first.
        pause
        exit /b 1
    )
    start "" javaw -jar "target\auto-clicker.jar"
    exit /b 0
)

rem 3. Last resort: standalone layout (exe beside runtime)
if exist "target\auto-clicker.exe" if exist "target\runtime\bin\java.exe" (
    start "" "target\auto-clicker.exe"
    exit /b 0
)

echo [ERROR] Build output not found. Run the build script first.
pause
exit /b 1
