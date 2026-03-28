---
name: update-server-lib
description: Updates the Hytale server reference files in lib/ by downloading the latest release server (or pre-release via Full-Update-Prerelease.cmd), decompiling the JAR using Vineflower, and updating server assets. Use when needing to update to a new Hytale server version, refreshing decompiled source code, or syncing with the latest server. Triggers - update server, download server, decompile jar, vineflower, update lib, new server version, sync server, refresh server.
---

# Update Server Lib Skill

Updates the `lib/` folder with the latest Hytale server files (release by default, pre-release optional) including decompiled source code and server assets.

## Prerequisites

Before running these scripts, ensure the following are installed and on PATH:

- **Hytale Downloader**: The `hytale-downloader-windows-amd64.exe` binary (already authenticated). Default location: `C:\hytale-downloader\` (configurable via `HYTALE_DOWNLOADER_PATH` env var)
- **Python 3+**: For running the patcher tool (`py --version` or `python --version`)
- **Java 25+**: `java --version` should show 25.x
- **Maven**: `mvn --version` should work
- **Git**: `git --version` should work

## Directory Structure

```
<HYTALE_DOWNLOADER_PATH>\              # Default: C:\hytale-downloader\
├── hytale-downloader-windows-amd64.exe
├── .hytale-downloader-credentials.json
├── downloads\                              # Created by script
│   └── <version>.zip                       # Downloaded server package
└── extracted\                              # Created by script
    └── <version>\                          # Extracted server files
        ├── Server\
        │   └── HytaleServer.jar
        └── Assets\
            └── Server\

%APPDATA%\Hytale\install\pre-release\package\game\
└── latest\                                 # Symlink to current build
    └── Client\Data\Game\Interface\         # UI source (.ui files)
```

## Usage

Run the CMD scripts from anywhere (they use absolute paths):

### Full Update — Release (Recommended)

```cmd
.\.github\skills\update-server-lib\scripts\Full-Update.cmd
```

Downloads the **release** patchline by default. This runs all three steps in sequence:
1. Downloads the latest release server
2. Decompiles it and updates `lib/`
3. Copies `lib/HytaleServer.jar` → `server/HytaleServer.jar` **and** copies `Assets.zip` → `server/Assets.zip`

`build.gradle` automatically resolves the Hytale dependency version from `server/HytaleServer.jar`'s manifest, so no manual version bump is needed after running this.

### Full Update — Pre-Release

```cmd
.\.github\skills\update-server-lib\scripts\Full-Update-Prerelease.cmd
```

Same as above but targets the **pre-release** patchline. Useful when a feature requires API changes only available in the latest pre-release. Equivalent to `Full-Update.cmd pre-release`.

### Step 1: Download and Extract Latest Server

```cmd
.\.github\skills\update-server-lib\scripts\Download-Server.cmd
REM Or explicitly for pre-release:
.\.github\skills\update-server-lib\scripts\Download-Server.cmd pre-release
```

This script:
- Downloads the latest server (default: `release`; pass `pre-release` as first argument to override)
- Extracts the server zip file
- Extracts the Assets.zip within it
- Saves the version for the next step

### Step 2: Decompile and Update Lib

```cmd
.\.github\skills\update-server-lib\scripts\Update-Lib.cmd
```

Or specify a version:

```cmd
.\.github\skills\update-server-lib\scripts\Update-Lib.cmd 2026.01.29-301e13929
```

This script:
- Clones/updates the HytaleModding/patcher tool
- Sets up Python virtual environment
- Runs Vineflower decompilation on HytaleServer.jar
- Copies decompiled source to `lib/hytale-server/src/main/java`
- Copies Server assets to `lib/Server`
- Copies UI assets to `lib/UI`
- Updates HytaleServer.jar in lib root

## Script Configuration

Set the `HYTALE_DOWNLOADER_PATH` environment variable to override the default downloader location. All sub-paths are derived from it automatically.

```cmd
REM Example: set before running scripts, or add to your system environment variables
set HYTALE_DOWNLOADER_PATH=D:\my-hytale-tools
```

| Setting | Default | Description |
|---------|---------|-------------|
| `HYTALE_DOWNLOADER_PATH` | `C:\hytale-downloader` | Path to hytale-downloader folder (env var) |
| `DOWNLOAD_DIR` | `<HYTALE_DOWNLOADER_PATH>\downloads` | Where to save downloaded zips |
| `EXTRACT_DIR` | `<HYTALE_DOWNLOADER_PATH>\extracted` | Where to extract server files |
| `PATCHER_DIR` | `<HYTALE_DOWNLOADER_PATH>\patcher` | Where to clone/use patcher tool |
| `PATCHLINE` | `release` | Patchline to download from (`release` or `pre-release`) |

## Troubleshooting

### Authentication Errors
If you get 401 or authentication errors, delete `.hytale-downloader-credentials.json` in your downloader directory (default: `C:\hytale-downloader\`) and run the downloader manually to re-authenticate.

### Decompilation Fails
- Ensure Python 3.13+ is installed: `py -3.13 --version`
- Ensure Java 25 is on PATH: `java --version`
- Ensure Maven is on PATH: `mvn --version`
- Check the patcher output for specific errors

### Incomplete Extraction
If extraction fails, delete the partially extracted folder and run again.

## Version Tracking

After a successful update:
- `.github/skills/update-server-lib/LAST_VERSION.txt` contains the downloaded version
- `lib/HytaleServer.jar`, `server/HytaleServer.jar`, and `server/Assets.zip` are all updated to the new version
- `build.gradle` reads its compile version from `server/HytaleServer.jar`'s manifest automatically

To check current versions at any time:
```powershell
# lib/ version
Add-Type -AssemblyName System.IO.Compression.FileSystem; $z = [System.IO.Compression.ZipFile]::OpenRead("lib\HytaleServer.jar"); $e = $z.GetEntry("META-INF/MANIFEST.MF"); $r = New-Object System.IO.StreamReader($e.Open()); Write-Host $r.ReadToEnd(); $r.Dispose(); $z.Dispose()
# server/ version (same command with server\HytaleServer.jar)
```
Look for `Implementation-Version` and `Implementation-Patchline` in the output.

## Notes

- The decompiled code may have compilation errors - this is expected. It’s for reference/exploration only.
- Server assets in `lib/Server` are read-only references; don’t modify them directly.
- UI assets in `lib/UI` are for reference when building custom UIs.
- After updating, test the dev server — new versions may tighten validation of NPC configs, drop lists, or other assets. Fix any asset errors before continuing feature work.
