@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM WarProject local Windows server launcher.
REM NeoForge 1.21.1 is compiled for Java 21 (class file major 65).
REM If this script uses Java 17/18/etc. the server fails with:
REM Unsupported major.minor version 65.0

set "NEOFORGE_VERSION=21.1.229"
set "NEOFORGE_DIR=libraries\net\neoforged\neoforge\%NEOFORGE_VERSION%"
set "WIN_ARGS=%NEOFORGE_DIR%\win_args.txt"
set "INSTALLER=neoforge-%NEOFORGE_VERSION%-installer.jar"
set "INSTALLER_URL=https://maven.neoforged.net/releases/net/neoforged/neoforge/%NEOFORGE_VERSION%/%INSTALLER%"

call :resolve_java21
if errorlevel 1 (
    echo.
    echo [WarProject] Java 21 is required.
    echo Install Temurin/Microsoft/Oracle JDK 21 and set JAVA_HOME_21 to its folder.
    echo Example:
    echo   setx JAVA_HOME_21 "C:\Program Files\Eclipse Adoptium\jdk-21"
    echo.
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
exit /b %ERRORLEVEL%

:resolve_java21
set "JAVA_EXE="

REM 1) Explicit override wins.
if defined JAVA_HOME_21 (
    if exist "%JAVA_HOME_21%\bin\java.exe" (
        call :check_java "%JAVA_HOME_21%\bin\java.exe"
        if not errorlevel 1 exit /b 0
    )
)

REM 2) JAVA_HOME only if it is actually Java 21.
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        call :check_java "%JAVA_HOME%\bin\java.exe"
        if not errorlevel 1 exit /b 0
    )
)

REM 3) Common Windows install locations.
for %%D in (
    "C:\Program Files\Eclipse Adoptium\jdk-21*"
    "C:\Program Files\Microsoft\jdk-21*"
    "C:\Program Files\Java\jdk-21*"
    "C:\Program Files\BellSoft\LibericaJDK-21*"
    "C:\Program Files\Zulu\zulu-21*"
) do (
    for /d %%J in (%%~D) do (
        if exist "%%~J\bin\java.exe" (
            call :check_java "%%~J\bin\java.exe"
            if not errorlevel 1 exit /b 0
        )
    )
)

REM 4) PATH only if java is Java 21.
call :check_java java
if not errorlevel 1 exit /b 0

exit /b 1

:check_java
set "CANDIDATE=%~1"
set "JAVA_MAJOR="
for /f "tokens=2 delims=\"" %%V in ('"%CANDIDATE%" -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VERSION=%%V"
)
for /f "tokens=1 delims=." %%M in ("!JAVA_VERSION!") do set "JAVA_MAJOR=%%M"
if "!JAVA_MAJOR!"=="21" (
    set "JAVA_EXE=%CANDIDATE%"
    exit /b 0
)
exit /b 1
