@echo off
setlocal enabledelayedexpansion

REM ============================================
REM Hytale Server Downloader
REM Downloads and extracts the latest server
REM Usage: Download-Server.cmd [release|pre-release]
REM Default patchline: release
REM ============================================

REM Use HYTALE_DOWNLOADER_PATH env var if set, otherwise default
if not defined HYTALE_DOWNLOADER_PATH set "HYTALE_DOWNLOADER_PATH=C:\hytale-downloader"
set "DOWNLOADER_PATH=%HYTALE_DOWNLOADER_PATH%"
set "DOWNLOAD_DIR=%DOWNLOADER_PATH%\downloads"
set "EXTRACT_DIR=%DOWNLOADER_PATH%\extracted"
REM Accept patchline as first argument (default: release), validate input
if not "%~1"=="" (set "PATCHLINE=%~1") else (set "PATCHLINE=release")
if not "%PATCHLINE%"=="release" if not "%PATCHLINE%"=="pre-release" (
    echo ERROR: Invalid patchline "%PATCHLINE%". Must be "release" or "pre-release".
    exit /b 1
)

REM Create directories
if not exist "%DOWNLOAD_DIR%" mkdir "%DOWNLOAD_DIR%"
if not exist "%EXTRACT_DIR%" mkdir "%EXTRACT_DIR%"

set "DOWNLOADER_EXE=%DOWNLOADER_PATH%\hytale-downloader-windows-amd64.exe"

if not exist "%DOWNLOADER_EXE%" (
    echo ERROR: Hytale downloader not found at: %DOWNLOADER_EXE%
    exit /b 1
)

echo ============================================
echo   Hytale Server Downloader
echo ============================================
echo.
echo Patchline: %PATCHLINE%
echo.

REM Generate timestamp for unique filename
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "TIMESTAMP=%%i"
if "%TIMESTAMP%"=="" set "TIMESTAMP=download"
set "DOWNLOAD_ZIP=%DOWNLOAD_DIR%\server-%PATCHLINE%-%TIMESTAMP%.zip"

echo Downloading server package...
echo Download path: %DOWNLOAD_ZIP%
echo.

REM Change to downloader directory for credentials file
REM Stream output live (via Tee-Object) while also capturing it to parse version afterward
set "DL_OUTPUT=%TEMP%\hytale-dl-output-%TIMESTAMP%.txt"
pushd "%DOWNLOADER_PATH%"
powershell -NoProfile -Command "& '%DOWNLOADER_EXE%' -patchline %PATCHLINE% -download-path '%DOWNLOAD_ZIP%' -skip-update-check 2>&1 | Tee-Object -FilePath '%DL_OUTPUT%'; exit $LASTEXITCODE"
set "DL_RESULT=!ERRORLEVEL!"
popd

if not exist "%DOWNLOAD_ZIP%" (
    echo ERROR: Download failed - zip file not found at: %DOWNLOAD_ZIP%
    if exist "%DL_OUTPUT%" del "%DL_OUTPUT%"
    exit /b 1
)

echo.
echo Download complete!
echo.

REM Parse version from downloader output (e.g., "version 2026.01.29-301e13929")
REM Tee-Object writes UTF-16, so use PowerShell (not findstr) to read/match it reliably
set "SERVER_VERSION="
for /f "usebackq delims=" %%v in (`powershell -NoProfile -Command "$m = Select-String -Path '%DL_OUTPUT%' -Pattern 'version ([^)]+)\)' | Select-Object -First 1; if ($m) { $m.Matches[0].Groups[1].Value }"`) do set "SERVER_VERSION=%%v"
if exist "%DL_OUTPUT%" del "%DL_OUTPUT%"

if "%SERVER_VERSION%"=="" (
    set "SERVER_VERSION=%TIMESTAMP%"
    echo Could not parse version, using timestamp: %SERVER_VERSION%
) else (
    echo Server version: %SERVER_VERSION%
)

REM Rename zip to version
set "VERSIONED_ZIP=%DOWNLOAD_DIR%\%SERVER_VERSION%.zip"
if not "%DOWNLOAD_ZIP%"=="%VERSIONED_ZIP%" (
    if exist "%VERSIONED_ZIP%" del /f "%VERSIONED_ZIP%"
    move "%DOWNLOAD_ZIP%" "%VERSIONED_ZIP%" >nul 2>&1
    if exist "%VERSIONED_ZIP%" set "DOWNLOAD_ZIP=%VERSIONED_ZIP%"
)

REM Extract the main server zip
set "SERVER_EXTRACT_PATH=%EXTRACT_DIR%\%SERVER_VERSION%"
if exist "%SERVER_EXTRACT_PATH%" (
    echo Removing existing extracted folder...
    rmdir /s /q "%SERVER_EXTRACT_PATH%"
)

echo.
echo Extracting server package to: %SERVER_EXTRACT_PATH%
REM Expand-Archive is very slow on zips with many small entries; use .NET ZipFile instead
powershell -NoProfile -Command "Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('%DOWNLOAD_ZIP%', '%SERVER_EXTRACT_PATH%')"
if !ERRORLEVEL! neq 0 (
    echo ERROR: Failed to extract server package
    exit /b 1
)
echo Main package extracted!
echo.

REM Find and extract Assets.zip
set "ASSETS_ZIP="
for /r "%SERVER_EXTRACT_PATH%" %%f in (Assets.zip) do (
    if exist "%%f" set "ASSETS_ZIP=%%f"
)

if defined ASSETS_ZIP (
    echo Extracting Assets.zip...
    set "ASSETS_DIR=%SERVER_EXTRACT_PATH%\Assets"
    if exist "!ASSETS_DIR!" rmdir /s /q "!ASSETS_DIR!"
    powershell -NoProfile -Command "Add-Type -AssemblyName System.IO.Compression.FileSystem; [System.IO.Compression.ZipFile]::ExtractToDirectory('!ASSETS_ZIP!', '!ASSETS_DIR!')"
    if !ERRORLEVEL! neq 0 (
        echo WARNING: Failed to extract Assets.zip
    ) else (
        echo Assets extracted!
    )
) else (
    echo Warning: Assets.zip not found in extracted files
)

echo.
echo ============================================
echo   Download Complete
echo ============================================
echo.
echo Version:      %SERVER_VERSION%
echo Extracted to: %SERVER_EXTRACT_PATH%
echo.

REM Save version for Update-Lib.cmd — one file per patchline so alternating runs don't overwrite each other
echo %SERVER_VERSION%> "%DOWNLOAD_DIR%\LATEST_VERSION_%PATCHLINE%.txt"
REM Also write the generic file as a convenience (reflects the most recent download of any patchline)
echo %SERVER_VERSION%> "%DOWNLOAD_DIR%\LATEST_VERSION.txt"

exit /b 0
