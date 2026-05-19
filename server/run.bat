@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM WarProject local Windows server launcher.
REM NeoForge 1.21.1 requires Java 21. Class file major 65 = Java 21.
REM Do NOT launch with Java 17/18/19/20, otherwise bootstraplauncher fails with:
REM Unsupported major.minor version 65.0

set "NEOFORGE_VERSION=21.1.229"
set "NEOFORGE_DIR=libraries\net\neoforged\neoforge\%NEOFORGE_VERSION%"
set "WIN_ARGS=%NEOFORGE_DIR%\win_args.txt"
set "INSTALLER=neoforge-%NEOFORGE_VERSION%-installer.jar"
set "INSTALLER_URL=https://maven.neoforged.net/releases/net/neoforged/neoforge/%NEOFORGE_VERSION%/%INSTALLER%"

call :find_java21
if errorlevel 1 (
    echo.
    echo [WarProject] ERROR: Java 21 was not found.
    echo [WarProject] The server cannot run on Java 17 or older.
    echo.
    echo Install Java 21, then set JAVA_HOME_21 to the Java 21 folder.
    echo Example for Eclipse Temurin:
    echo   setx JAVA_HOME_21 "C:\Program Files\Eclipse Adoptium\jdk-21"
    echo.
    echo After setx, close this console/window and open it again.
    pause
    exit /b 1
)

echo [WarProject] Using Java 21: %JAVA_EXE%
"%JAVA_EXE%" -version

if not exist "%WIN_ARGS%" (
    echo.
    echo [WarProject] Missing %WIN_ARGS%
    echo [WarProject] NeoForge server files are not installed in this folder yet.
    echo [WarProject] Downloading %INSTALLER% ...

    powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Invoke-WebRequest -Uri '%INSTALLER_URL%' -OutFile '%INSTALLER%' -UseBasicParsing } catch { Write-Error $_; exit 1 }"
    if errorlevel 1 (
        echo [WarProject] Failed to download NeoForge installer.
        echo URL: %INSTALLER_URL%
        pause
        exit /b 1
    )

    echo [WarProject] Running NeoForge installer with Java 21...
    "%JAVA_EXE%" -jar "%INSTALLER%" --install-server .
    if errorlevel 1 (
        echo [WarProject] NeoForge installer failed.
        pause
        exit /b 1
    )
)

if not exist "%WIN_ARGS%" (
    echo [WarProject] Still missing %WIN_ARGS% after install.
    echo Check installer output above.
    pause
    exit /b 1
)

echo [WarProject] Starting server with Java 21...
"%JAVA_EXE%" @user_jvm_args.txt @"%WIN_ARGS%" %*
pause
exit /b %ERRORLEVEL%

:find_java21
set "JAVA_EXE="

REM 1) Explicit override. This is the recommended setup.
if defined JAVA_HOME_21 (
    if exist "%JAVA_HOME_21%\bin\java.exe" (
        call :accept_if_java21 "%JAVA_HOME_21%\bin\java.exe"
        if not errorlevel 1 exit /b 0
    )
)

REM 2) JAVA_HOME, but only if it is really Java 21.
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        call :accept_if_java21 "%JAVA_HOME%\bin\java.exe"
        if not errorlevel 1 exit /b 0
    )
)

REM 3) Common Windows install locations for Java 21.
for %%P in (
    "C:\Program Files\Eclipse Adoptium\jdk-21*\bin\java.exe"
    "C:\Program Files\Microsoft\jdk-21*\bin\java.exe"
    "C:\Program Files\Java\jdk-21*\bin\java.exe"
    "C:\Program Files\BellSoft\LibericaJDK-21*\bin\java.exe"
    "C:\Program Files\Zulu\zulu-21*\bin\java.exe"
) do (
    for %%J in (%%~P) do (
        if exist "%%~J" (
            call :accept_if_java21 "%%~J"
            if not errorlevel 1 exit /b 0
        )
    )
)

REM 4) PATH is used only if it is really Java 21. Old Java on PATH is rejected.
where java >nul 2>nul
if not errorlevel 1 (
    for /f "delims=" %%J in ('where java') do (
        call :accept_if_java21 "%%~J"
        if not errorlevel 1 exit /b 0
    )
)

exit /b 1

:accept_if_java21
set "CANDIDATE=%~1"
set "DETECTED_VERSION="
set "DETECTED_MAJOR="

for /f "tokens=2 delims=\"" %%V in ('"%CANDIDATE%" -version 2^>^&1 ^| findstr /i "version"') do set "DETECTED_VERSION=%%V"
for /f "tokens=1 delims=." %%M in ("!DETECTED_VERSION!") do set "DETECTED_MAJOR=%%M"

if "!DETECTED_MAJOR!"=="21" (
    set "JAVA_EXE=%CANDIDATE%"
    exit /b 0
)

echo [WarProject] Skipping non-Java-21 runtime: %CANDIDATE% version !DETECTED_VERSION!
exit /b 1
