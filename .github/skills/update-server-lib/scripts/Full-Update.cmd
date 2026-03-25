@echo off
setlocal enabledelayedexpansion

REM ============================================
REM Hytale Server Full Update
REM Downloads, decompiles, and updates lib
REM ============================================

set "SCRIPT_DIR=%~dp0"

echo ============================================
echo   Hytale Server Full Update
echo ============================================
echo.

echo ^>^>^> Step 1/3: Downloading server...
echo.

call "%SCRIPT_DIR%Download-Server.cmd"
if errorlevel 1 (
    echo.
    echo ERROR: Download step failed
    exit /b 1
)

echo.
echo ^>^>^> Step 2/3: Updating lib folder...
echo.

call "%SCRIPT_DIR%Update-Lib.cmd"
if errorlevel 1 (
    echo.
    echo ERROR: Lib update step failed
    exit /b 1
)

echo.
echo ^>^>^> Step 3/3: Syncing server/ runtime files with lib/...
echo.

REM Derive workspace root (4 levels up from script location)
for %%I in ("%SCRIPT_DIR%\..\..\..\..\") do set "WORKSPACE_ROOT=%%~fI"
set "LIB_JAR=%WORKSPACE_ROOT%lib\HytaleServer.jar"
set "SERVER_JAR=%WORKSPACE_ROOT%server\HytaleServer.jar"
set "SERVER_DIR=%WORKSPACE_ROOT%server"

if not exist "%LIB_JAR%" (
    echo ERROR: lib\HytaleServer.jar not found - lib update may have failed
    exit /b 1
)

REM Copy HytaleServer.jar
copy /y "%LIB_JAR%" "%SERVER_JAR%"
if errorlevel 1 (
    echo ERROR: Failed to copy lib\HytaleServer.jar to server\HytaleServer.jar
    exit /b 1
)
echo Copied lib\HytaleServer.jar to server\HytaleServer.jar

REM Locate and copy Assets.zip from the extracted download directory
REM The HYTALE_DOWNLOADER_PATH env var (or default) points to the downloader folder
if not defined HYTALE_DOWNLOADER_PATH set "HYTALE_DOWNLOADER_PATH=C:\hytale-downloader"
set "EXTRACT_DIR=%HYTALE_DOWNLOADER_PATH%\extracted"
set "DOWNLOAD_DIR=%HYTALE_DOWNLOADER_PATH%\downloads"

REM Read the downloaded version (set by Download-Server.cmd)
set "SERVER_VERSION="
if exist "%DOWNLOAD_DIR%\LATEST_VERSION.txt" (
    set /p SERVER_VERSION=<"%DOWNLOAD_DIR%\LATEST_VERSION.txt"
)

if not "%SERVER_VERSION%"=="" (
    set "EXTRACTED_ASSETS_ZIP=%EXTRACT_DIR%\%SERVER_VERSION%\Assets.zip"
    set "EXTRACTED_AOT=%EXTRACT_DIR%\%SERVER_VERSION%\Server\HytaleServer.aot"

    if exist "!EXTRACTED_ASSETS_ZIP!" (
        echo Copying Assets.zip to server\...
        copy /y "!EXTRACTED_ASSETS_ZIP!" "%SERVER_DIR%\Assets.zip"
        if errorlevel 1 (
            echo ERROR: Failed to copy Assets.zip to server\Assets.zip
            exit /b 1
        )
        echo Copied Assets.zip to server\Assets.zip
    ) else (
        echo WARNING: Assets.zip not found at: !EXTRACTED_ASSETS_ZIP!
        echo          server\Assets.zip was NOT updated - version mismatch may occur.
    )

    if exist "!EXTRACTED_AOT!" (
        echo Copying HytaleServer.aot to server\...
        copy /y "!EXTRACTED_AOT!" "%SERVER_DIR%\HytaleServer.aot"
        if errorlevel 1 (
            echo ERROR: Failed to copy HytaleServer.aot to server\HytaleServer.aot
            exit /b 1
        )
        echo Copied HytaleServer.aot to server\HytaleServer.aot
    ) else (
        echo WARNING: HytaleServer.aot not found at: !EXTRACTED_AOT!
        echo          server\HytaleServer.aot was NOT updated.
    )
) else (
    echo WARNING: Could not determine server version - server\Assets.zip and server\HytaleServer.aot were NOT updated.
)

echo build.gradle will now automatically resolve this version.
echo.

echo.
echo ============================================
echo   Full Update Complete!
echo ============================================
echo.
echo Your lib folder and server/ directory are now updated with:
echo   - Latest HytaleServer.jar
echo   - Decompiled source code (for reference)
echo   - Server assets
echo   - UI assets
echo.
echo server/ is now fully synced: HytaleServer.jar + Assets.zip + HytaleServer.aot match the same version.
echo Run 'Build and Deploy Plugin' task to test your plugin!
echo.

endlocal
