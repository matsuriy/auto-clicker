@echo off
rem ============================================================
rem  Build the standalone distribution with an embedded JRE.
rem  Log file: build.log
rem  [1/4] Maven build: shade fat jar, launch4j exe wrapper
rem  [2/4] jlink trimmed JRE, then jpackage self-contained app;
rem        falls back to copying a JRE if jpackage fails
rem  [3/4] Verify outputs
rem  [4/4] Zip for distribution, generate launch.bat fallback
rem ============================================================
setlocal EnableExtensions
cd /d "%~dp0"

set "LOG=%~dp0build.log"
echo ===== BUILD START %date% %time% =====> "%LOG%"

echo ========================================
echo  Auto Clicker - Standalone Build
echo ========================================
echo  Log file: build.log
echo.

rem If mvn is not on PATH, try the well-known local install location
where mvn 1>nul 2>nul
if errorlevel 1 if exist "F:\tool\apache-maven-3.6.3-bin\apache-maven-3.6.3\bin\mvn.cmd" set "PATH=F:\tool\apache-maven-3.6.3-bin\apache-maven-3.6.3\bin;%PATH%"

where mvn 1>nul 2>nul
if errorlevel 1 goto no_mvn

if not defined JAVA_HOME goto find_java
set "JAVA_ROOT=%JAVA_HOME%"
set "JLINK=%JAVA_HOME%\bin\jlink.exe"
set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
goto java_ok

:find_java
for /f "delims=" %%i in ('where java 2^>nul') do (
    set "JAVA_BIN=%%~dpi"
    goto java_from_bin
)
goto no_java

:java_from_bin
for %%j in ("%JAVA_BIN%..") do set "JAVA_ROOT=%%~fj"
set "JLINK=%JAVA_BIN%jlink.exe"
set "JPACKAGE=%JAVA_BIN%jpackage.exe"
goto java_ok

:java_ok
if not defined JAVA_ROOT goto no_java
echo JAVA_ROOT=%JAVA_ROOT%>> "%LOG%"

echo [1/4] Maven build...
call mvn clean package >> "%LOG%" 2>&1
if errorlevel 1 (
    echo [WARN] mvn clean failed ^(target locked? app still running^), retry without clean
    call mvn package >> "%LOG%" 2>&1
)
if errorlevel 1 goto mvn_fail

if not exist "target\auto-clicker.exe" goto no_exe

if exist "%JPACKAGE%" (
    echo [2/4] Trying jpackage...
    call :try_jpackage
    if not errorlevel 1 goto zip_done
    echo jpackage failed, fallback to runtime copy.>> "%LOG%"
)

echo [2/4] Copy embedded JRE to target\runtime ...
if exist "target\runtime" rmdir /s /q "target\runtime"
if exist "target\jre\bin\java.exe" (
    echo Using jlink runtime>> "%LOG%"
    xcopy /E /I /Q "target\jre" "target\runtime" >> "%LOG%" 2>&1
) else if exist "%JAVA_ROOT%\bin\java.exe" (
    echo Using JDK runtime: %JAVA_ROOT%>> "%LOG%"
    xcopy /E /I /Q "%JAVA_ROOT%" "target\runtime" >> "%LOG%" 2>&1
) else (
    goto no_runtime
)
if not exist "target\runtime\bin\java.exe" goto no_runtime
if not exist "target\runtime\bin\server\jvm.dll" (
    if not exist "target\runtime\bin\client\jvm.dll" goto no_runtime
)

:zip_done
echo [3/4] Verify output...
if not exist "target\auto-clicker.exe" goto no_exe
if not exist "target\auto-clicker.jar" goto no_exe
if exist "target\app" if not exist "target\app\auto-clicker.cfg" goto no_exe
if not exist "target\runtime\bin\java.exe" goto no_runtime

echo [4/4] Create zip...
if exist "target\auto-clicker.zip" del /f /q "target\auto-clicker.zip"
powershell -NoProfile -Command "$items = @('target\auto-clicker.exe','target\auto-clicker.jar','target\runtime'); if (Test-Path 'target\app') { $items += 'target\app' }; Compress-Archive -LiteralPath $items -DestinationPath 'target\auto-clicker.zip' -Force" >> "%LOG%" 2>&1

call :write_launcher

echo.
echo ========================================
echo  BUILD OK
echo ========================================
echo  Run:  target\auto-clicker.exe
echo  Alt:  target\launch.bat
echo  Zip:  target\auto-clicker.zip
echo.
pause
exit /b 0

:write_launcher
> "target\launch.bat" echo @echo off
>> "target\launch.bat" echo cd /d "%%~dp0"
>> "target\launch.bat" echo start "" "%%~dp0runtime\bin\javaw.exe" -jar "%%~dp0auto-clicker.jar"
exit /b 0

:try_jpackage
if not exist "%JLINK%" exit /b 1
if exist "target\jre" rmdir /s /q "target\jre"
rem --compress=2 works on JDK 17-23, try it first so JDK 17 succeeds on
rem the first attempt; on JDK 24+ it was removed, fall back to zip-6 (21+)
"%JLINK%" --add-modules java.base,java.datatransfer,java.desktop,java.logging,java.prefs,java.xml --strip-debug --compress=2 --no-header-files --no-man-pages --output "target\jre" >> "%LOG%" 2>&1
if errorlevel 1 "%JLINK%" --add-modules java.base,java.datatransfer,java.desktop,java.logging,java.prefs,java.xml --strip-debug --compress=zip-6 --no-header-files --no-man-pages --output "target\jre" >> "%LOG%" 2>&1
if errorlevel 1 exit /b 1
if exist "target\jpackage-input" rmdir /s /q "target\jpackage-input"
mkdir "target\jpackage-input"
copy /Y "target\auto-clicker.jar" "target\jpackage-input\" >> "%LOG%" 2>&1
if exist "target\app-image" rmdir /s /q "target\app-image"
"%JPACKAGE%" --name auto-clicker --app-version 1.0.0 --input "target\jpackage-input" --main-jar auto-clicker.jar --main-class com.matsuri.auto.clicker.AutoClickerMain --runtime-image "target\jre" --dest "target\app-image" --type app-image --java-options "-Dfile.encoding=UTF-8" >> "%LOG%" 2>&1
if errorlevel 1 exit /b 1
if not exist "target\app-image\auto-clicker\auto-clicker.exe" exit /b 1
copy /Y "target\app-image\auto-clicker\auto-clicker.exe" "target\" >> "%LOG%" 2>&1
rem The jpackage launcher needs the sibling app dir (cfg + jar)
if exist "target\app" rmdir /s /q "target\app"
xcopy /E /I /Q "target\app-image\auto-clicker\app" "target\app" >> "%LOG%" 2>&1
if exist "target\runtime" rmdir /s /q "target\runtime"
xcopy /E /I /Q "target\app-image\auto-clicker\runtime" "target\runtime" >> "%LOG%" 2>&1
rem jpackage succeeded: the intermediate jlink output target\jre is no
rem longer needed, remove it to keep target small
if exist "target\jre" rmdir /s /q "target\jre"
exit /b 0

:no_mvn
echo [ERROR] Maven not found. Install Maven and add mvn to PATH.
echo [ERROR] Maven not found>> "%LOG%"
goto fail

:no_java
echo [ERROR] JDK 17+ not found. Install Temurin JDK and set JAVA_HOME.
echo [ERROR] JDK not found>> "%LOG%"
goto fail

:mvn_fail
echo [ERROR] Maven build failed. See build.log
goto fail

:no_exe
echo [ERROR] Output verification failed: exe/jar/app-config missing. See build.log
echo [ERROR] exe or jpackage app missing>> "%LOG%"
goto fail

:no_runtime
echo [ERROR] target\runtime\bin\java.exe was not created.
echo [ERROR] runtime missing>> "%LOG%"
goto fail

:fail
echo.
echo ========================================
echo  BUILD FAILED
echo ========================================
echo  See build.log for details:
echo.
if exist "%LOG%" type "%LOG%"
echo.
pause
exit /b 1
