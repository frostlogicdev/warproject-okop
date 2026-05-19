@echo off
REM NeoForge 1.21.1 requires Java 21. The "java" on PATH is Java 17 on this
REM machine, so we explicitly point at the JDK 21 install. Edit JAVA_HOME if
REM your Java 21 lives elsewhere.

if not defined JAVA_HOME_21 set "JAVA_HOME_21=C:\Program Files\Java\jdk-21.0.10"

set "JAVA_EXE=%JAVA_HOME_21%\bin\java.exe"
if not exist "%JAVA_EXE%" (
    echo [WarProject] Java 21 not found at "%JAVA_EXE%".
    echo Set JAVA_HOME_21 to a Java 21 install or edit run.bat.
    pause
    exit /b 1
)

"%JAVA_EXE%" @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.229/win_args.txt %*
pause
