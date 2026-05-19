@echo off
setlocal

REM WarProject local Windows server launcher.
REM NeoForge creates libraries/net/neoforged/neoforge/<version>/win_args.txt only after
REM running the NeoForge installer with --install-server in this folder.

set "NEOFORGE_VERSION=21.1.229"
set "NEOFORGE_DIR=libraries\net\neoforged\neoforge\%NEOFORGE_VERSION%"
set "WIN_ARGS=%NEOFORGE_DIR%\win_args.txt"
set "INSTALLER=neoforge-%NEOFORGE_VERSION%-installer.jar"
set "INSTALLER_URL=https://maven.neoforged.net/releases/net/neoforged/neoforge/%NEOFORGE_VERSION%/%INSTALLER%"

REM Prefer JAVA_HOME_21, then JAVA_HOME, then java from PATH.
if defined JAVA_HOME_21 (
    set "JAVA_EXE=%JAVA_HOME_21%\bin\java.exe"
) else if defined JAVA_HOME (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_EXE=java"
)

if not "%JAVA_EXE%"=="java" if not exist "%JAVA_EXE%" (
    echo [WarProject] Java executable not found: "%JAVA_EXE%"
    echo Install Java 21 and set JAVA_HOME_21 or JAVA_HOME.
    pause
    exit /b 1
)

echo [WarProject] Using Java: %JAVA_EXE%
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

    echo [WarProject] Running NeoForge installer...
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

echo [WarProject] Starting server...
"%JAVA_EXE%" @user_jvm_args.txt @"%WIN_ARGS%" %*
pause
