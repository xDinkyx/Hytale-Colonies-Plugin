@echo off
setlocal enabledelayedexpansion

REM ============================================
REM Sync Official Source (hytale-shared-source)
REM Clones or pulls lib/hytale-shared-source
REM Usage: Sync-Source.cmd [release|pre-release]
REM   Default patchline: release
REM ============================================

REM Get workspace root (4 levels up from script location)
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%\..\..\..\..\") do set "WORKSPACE_ROOT=%%~fI"
set "LIB_DIR=%WORKSPACE_ROOT%lib"
set "SHARED_SOURCE_DIR=%LIB_DIR%\hytale-shared-source"

set "PATCHLINE=%~1"
if "%PATCHLINE%"=="" set "PATCHLINE=release"

set "GIT_BRANCH=release"
if /i "%PATCHLINE%"=="pre-release" set "GIT_BRANCH=pre-release"

echo ============================================
echo   Sync Official Source (hytale-shared-source)
echo ============================================
echo.
echo Patchline:  %PATCHLINE%
echo Branch:     %GIT_BRANCH%
echo Target dir: %SHARED_SOURCE_DIR%
echo.

where git >nul 2>nul
if errorlevel 1 (
    echo ERROR: Git not found. Please install Git.
    exit /b 1
)

if exist "%SHARED_SOURCE_DIR%\.git" (
    echo Pulling latest official source (branch: %GIT_BRANCH%)...
    git -C "%SHARED_SOURCE_DIR%" fetch origin >nul 2>nul
    git -C "%SHARED_SOURCE_DIR%" checkout %GIT_BRANCH% >nul 2>nul
    git -C "%SHARED_SOURCE_DIR%" pull --ff-only
    if errorlevel 1 (
        echo Warning: git pull failed. Source may be out of date.
        exit /b 1
    )
) else (
    echo Cloning hytale-shared-source (branch: %GIT_BRANCH%)...
    echo   Requires HypixelStudios org access. See: https://accounts.hytale.com/shared-source
    git clone -b %GIT_BRANCH% "https://github.com/HypixelStudios/hytale-shared-source" "%SHARED_SOURCE_DIR%"
    if errorlevel 1 (
        echo ERROR: Failed to clone hytale-shared-source
        echo        Ensure your GitHub account has access to HypixelStudios org
        exit /b 1
    )
)

echo.
echo Source synced to: %SHARED_SOURCE_DIR%
echo.

exit /b 0
