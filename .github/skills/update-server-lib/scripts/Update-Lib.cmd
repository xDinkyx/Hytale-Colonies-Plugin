@echo off
setlocal enabledelayedexpansion

REM ============================================
REM Hytale Server Lib Updater
REM Updates lib folder (source sync + assets)
REM Usage: Update-Lib.cmd [version] [release|pre-release]
REM   version   - explicit version string (optional; auto-detected from downloads)
REM   patchline - used to prefer the matching LATEST_VERSION_<patchline>.txt file
REM ============================================

REM Use HYTALE_DOWNLOADER_PATH env var if set, otherwise default
if not defined HYTALE_DOWNLOADER_PATH set "HYTALE_DOWNLOADER_PATH=C:\hytale-downloader"
set "EXTRACT_DIR=%HYTALE_DOWNLOADER_PATH%\extracted"
set "DOWNLOAD_DIR=%HYTALE_DOWNLOADER_PATH%\downloads"

REM Get workspace root (4 levels up from script location)
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%\..\..\..\..\") do set "WORKSPACE_ROOT=%%~fI"
set "LIB_DIR=%WORKSPACE_ROOT%lib"

echo ============================================
echo   Hytale Server Lib Updater
echo ============================================
echo.
echo Workspace: %WORKSPACE_ROOT%
echo Lib Dir:   %LIB_DIR%
echo.

REM Get server version - either from argument or latest
set "SERVER_VERSION=%~1"
set "PATCHLINE=%~2"
if "%SERVER_VERSION%"=="" (
    REM Prefer per-patchline version file when a patchline is specified
    if not "%PATCHLINE%"=="" (
        if exist "%DOWNLOAD_DIR%\LATEST_VERSION_%PATCHLINE%.txt" (
            set /p SERVER_VERSION=<"%DOWNLOAD_DIR%\LATEST_VERSION_%PATCHLINE%.txt"
        )
    )
    if "%SERVER_VERSION%"=="" (
        if exist "%DOWNLOAD_DIR%\LATEST_VERSION.txt" (
            set /p SERVER_VERSION=<"%DOWNLOAD_DIR%\LATEST_VERSION.txt"
        )
    )
)

if "%SERVER_VERSION%"=="" (
    REM Find latest folder in extract dir
    for /f "tokens=*" %%d in ('dir /b /ad /o-n "%EXTRACT_DIR%" 2^>nul') do (
        set "SERVER_VERSION=%%d"
        goto :found_version
    )
)
:found_version

if "%SERVER_VERSION%"=="" (
    echo ERROR: No server version found. Run Download-Server.cmd first.
    exit /b 1
)

set "SERVER_EXTRACT_PATH=%EXTRACT_DIR%\%SERVER_VERSION%"
if not exist "%SERVER_EXTRACT_PATH%" (
    echo ERROR: Server version folder not found: %SERVER_EXTRACT_PATH%
    exit /b 1
)

echo Using version: %SERVER_VERSION%
echo.

REM Find HytaleServer.jar
set "HYTALE_JAR="
for /r "%SERVER_EXTRACT_PATH%" %%f in (HytaleServer.jar) do (
    if exist "%%f" set "HYTALE_JAR=%%f"
)

if not defined HYTALE_JAR (
    echo ERROR: HytaleServer.jar not found in: %SERVER_EXTRACT_PATH%
    exit /b 1
)

echo Found HytaleServer.jar: %HYTALE_JAR%

REM Find Assets folder
set "ASSETS_PATH="
for /d /r "%SERVER_EXTRACT_PATH%" %%d in (*) do (
    if /i "%%~nxd"=="Assets" (
        if exist "%%d\Server" set "ASSETS_PATH=%%d"
    )
)

if defined ASSETS_PATH (
    echo Found Assets: %ASSETS_PATH%
)

echo.
echo ============================================
echo   Checking Prerequisites
echo ============================================
echo.

set "PREREQ_FAIL="

REM Check Java
where java >nul 2>nul
if errorlevel 1 (
    echo [FAIL] Java not found
    set "PREREQ_FAIL=1"
) else (
    echo [OK] Java found
)

REM Check Git
where git >nul 2>nul
if errorlevel 1 (
    echo [FAIL] Git not found
    set "PREREQ_FAIL=1"
) else (
    echo [OK] Git found
)

if defined PREREQ_FAIL (
    echo.
    echo ERROR: Prerequisites check failed. Please install missing tools.
    exit /b 1
)

echo.
echo ============================================
echo   Syncing Official Source (hytale-shared-source)
echo ============================================
echo.

set "SHARED_SOURCE_DIR=%LIB_DIR%\hytale-shared-source"

REM Use patchline as the git branch (release by default, matching Full-Update.cmd)
set "GIT_BRANCH=release"
if /i "%PATCHLINE%"=="pre-release" set "GIT_BRANCH=pre-release"
if exist "%SHARED_SOURCE_DIR%\.git" goto :sync_pull
goto :sync_clone

:sync_pull
echo Pulling latest official source (branch: %GIT_BRANCH%)...
git -C "%SHARED_SOURCE_DIR%" fetch origin >nul 2>nul
git -C "%SHARED_SOURCE_DIR%" checkout %GIT_BRANCH% >nul 2>nul
git -C "%SHARED_SOURCE_DIR%" pull --ff-only
if errorlevel 1 echo Warning: git pull failed. Source may be out of date.
goto :sync_done

:sync_clone
echo Cloning hytale-shared-source (branch: %GIT_BRANCH%)...
echo   Requires HypixelStudios org access. See: https://accounts.hytale.com/shared-source
git clone -b %GIT_BRANCH% "https://github.com/HypixelStudios/hytale-shared-source" "%SHARED_SOURCE_DIR%"
if errorlevel 1 goto :clone_failed
goto :sync_done

:clone_failed
echo ERROR: Failed to clone hytale-shared-source
echo        Ensure your GitHub account has access to HypixelStudios org
exit /b 1

:sync_done
echo   Official source synced to: %SHARED_SOURCE_DIR%

echo.
echo ============================================
echo   Updating lib folder
echo ============================================
echo.

REM Copy HytaleServer.jar
echo.
echo Copying HytaleServer.jar...
copy /y "%HYTALE_JAR%" "%LIB_DIR%\HytaleServer.jar" >nul
echo   JAR copied to: %LIB_DIR%\HytaleServer.jar

REM Copy Server assets
if defined ASSETS_PATH (
    if exist "%ASSETS_PATH%\Server" (
        echo.
        echo Copying Server assets...
        
        if exist "%LIB_DIR%\Server" (
            echo   Removing existing Server assets...
            rmdir /s /q "%LIB_DIR%\Server"
        )
        
        xcopy /s /e /i /q "%ASSETS_PATH%\Server" "%LIB_DIR%\Server" >nul
        echo   Server assets copied to: %LIB_DIR%\Server
    )

    REM Copy Common assets (block textures, item models, NPC visuals, sounds, particles, VFX, UI, etc.)
    if exist "%ASSETS_PATH%\Common" (
        echo.
        echo Copying Common assets...
        
        if exist "%LIB_DIR%\Common" (
            echo   Removing existing Common assets...
            rmdir /s /q "%LIB_DIR%\Common"
        )
        
        xcopy /s /e /i /q "%ASSETS_PATH%\Common" "%LIB_DIR%\Common" >nul
        echo   Common assets copied to: %LIB_DIR%\Common
    )
)

REM Copy UI assets - prefer Hytale launcher installation (full client .ui files),
REM fall back to Assets\Common\UI from the extracted package.
set "UI_SOURCE=%APPDATA%\Hytale\install\pre-release\package\game\latest\Client\Data\Game\Interface"

if exist "%UI_SOURCE%" (
    echo.
    echo Copying UI assets from Hytale installation...
    echo   Source: %UI_SOURCE%
    
    if exist "%LIB_DIR%\UI" (
        echo   Removing existing UI assets...
        rmdir /s /q "%LIB_DIR%\UI"
    )
    
    xcopy /s /e /i /q "%UI_SOURCE%" "%LIB_DIR%\UI" >nul
    echo   UI assets copied to: %LIB_DIR%\UI
) else if defined ASSETS_PATH (
    if exist "%ASSETS_PATH%\Common\UI" (
        echo.
        echo Warning: Hytale launcher path not found. Falling back to extracted Assets\Common\UI...
        echo   Source: %ASSETS_PATH%\Common\UI
        
        if exist "%LIB_DIR%\UI" (
            echo   Removing existing UI assets...
            rmdir /s /q "%LIB_DIR%\UI"
        )
        
        xcopy /s /e /i /q "%ASSETS_PATH%\Common\UI" "%LIB_DIR%\UI" >nul
        echo   UI assets copied to: %LIB_DIR%\UI
    )
) else (
    echo Warning: UI folder not found at: %UI_SOURCE%
    echo   Make sure Hytale is installed via the launcher.
)

REM Save version info
echo %SERVER_VERSION%> "%SCRIPT_DIR%..\LAST_VERSION.txt"

echo.
echo ============================================
echo   Update Complete
echo ============================================
echo.
echo Updated to version: %SERVER_VERSION%
echo.
echo Lib folder structure:
echo   lib/
echo     HytaleServer.jar          (original JAR)
echo     hytale-shared-source/     (official server source with comments and docs)
echo     Server/                   (server assets)
echo     Common/                   (common assets - textures, models, sounds, VFX, UI)
echo     UI/                       (UI assets)
echo.

exit /b 0
