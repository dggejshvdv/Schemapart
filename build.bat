@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"
title Schemapart build - 1.21.11

set "MODULE=fabric-1.21.11"
set "MCVER=1.21.11"
set "MINJAVA=21"
set "PREFIX=schemapart-1.21.11-"
set "NOPAUSE="
if /i "%~1"=="--no-pause" set "NOPAUSE=1"

echo ============================================
echo  Schemapart build - Minecraft 1.21.11
echo ============================================
echo.

set "JDK="
call :try_jdk "%JAVA_HOME_25%"
if not defined JDK call :try_jdk "%JAVA_HOME_21%"
if not defined JDK call :try_jdk "%JAVA_HOME%"
if not defined JDK call :scan_root "%ProgramFiles%\Java"
if not defined JDK call :scan_root "%ProgramFiles%\Eclipse Adoptium"
if not defined JDK call :scan_root "%ProgramFiles%\Microsoft"
if not defined JDK call :scan_root "%USERPROFILE%\.jdks"
if not defined JDK call :try_jdk "%APPDATA%\.minecraft\runtime\java-runtime-epsilon"
if not defined JDK goto :no_jdk

echo Using JDK: %JDK%
set "JAVA_HOME=%JDK%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo.

call "..\gradlew.bat" :%MODULE%:build --console=plain
if errorlevel 1 goto :build_failed

set "JAR="
for /f "delims=" %%F in ('dir /b /o-d "build\libs\%PREFIX%*.jar" 2^>nul ^| findstr /v /i "sources"') do (
    if not defined JAR set "JAR=%%F"
)
if not defined JAR goto :jar_missing

copy /y "build\libs\%JAR%" "%~dp0%JAR%" >nul
if errorlevel 1 goto :copy_failed

rem Keep the version folder tidy; the final jar is already copied above.
if exist "build" rmdir /s /q "build" >nul 2>&1

echo.
echo [OK] Built:
echo      %~dp0%JAR%
echo.
echo Copy this jar into .minecraft\mods.
if not defined NOPAUSE pause
exit /b 0

:no_jdk
echo [ERROR] No suitable JDK found. Need Java %MINJAVA%+ with javac.
echo Set JAVA_HOME_25 or JAVA_HOME_21 to a JDK folder.
if not defined NOPAUSE pause
exit /b 1

:build_failed
echo.
echo [ERROR] Gradle build failed.
if not defined NOPAUSE pause
exit /b 1

:jar_missing
echo.
echo [ERROR] Build succeeded but the jar was not found in build\libs.
if not defined NOPAUSE pause
exit /b 1

:copy_failed
echo.
echo [ERROR] Could not copy the jar into this folder.
if not defined NOPAUSE pause
exit /b 1

:try_jdk
if "%~1"=="" exit /b 0
if not exist "%~1\bin\java.exe" exit /b 0
if not exist "%~1\bin\javac.exe" exit /b 0
call :check_version "%~1"
exit /b 0

:check_version
set "CAND=%~1"
set "VER="
if exist "%CAND%\release" (
    for /f "tokens=2 delims==" %%v in ('findstr /b "JAVA_VERSION=" "%CAND%\release"') do set "VER=%%~v"
)
if not defined VER exit /b 0
for /f "tokens=1 delims=." %%m in ("%VER%") do set "MAJOR=%%m"
if not defined MAJOR exit /b 0
if "%MAJOR%"=="1" set "MAJOR=8"
if %MAJOR% GEQ %MINJAVA% set "JDK=%CAND%"
exit /b 0

:scan_root
if not exist "%~1" exit /b 0
for /d %%D in ("%~1\*") do (
    if not defined JDK call :try_jdk "%%~fD"
)
exit /b 0
