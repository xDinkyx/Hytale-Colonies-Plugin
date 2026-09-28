---
name: update-server-lib
description: Updates the Hytale server reference files in lib/ by downloading the latest release server (or pre-release via Full-Update-Prerelease.cmd), syncing the official server source (hytale-shared-source), and updating server assets. Use when needing to update to a new Hytale server version, syncing the official source, or refreshing server assets. Triggers - update server, download server, update lib, new server version, sync server, refresh server, hytale-shared-source.
---

# Update Server Lib Skill

Updates the `lib/` folder with the latest Hytale server files (release by default, pre-release optional) including official server source and server assets.

## Prerequisites

Before running these scripts, ensure the following are installed and on PATH:

- **Hytale Downloader**: The `hytale-downloader-windows-amd64.exe` binary (already authenticated). Default location: `C:\hytale-downloader\` (configurable via `HYTALE_DOWNLOADER_PATH` env var)
- **Java 25+**: `java --version` should show 25.x
- **Git**: `git --version` should work (also used to clone/pull `lib/hytale-shared-source`)

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
2. Syncs `lib/hytale-shared-source` (release branch) and copies assets/JAR to `lib/`
3. Copies `lib/HytaleServer.jar` → `server/HytaleServer.jar` **and** copies `Assets.zip` → `server/Assets.zip`

`build.gradle` automatically resolves the Hytale dependency version from `server/HytaleServer.jar`'s manifest, so no manual version bump is needed after running this.

### Full Update — Pre-Release

```cmd
.\.github\skills\update-server-lib\scripts\Full-Update-Prerelease.cmd
```

Same as above but targets the **pre-release** patchline. Useful when a feature requires API changes only available in the latest pre-release. Equivalent to `Full-Update.cmd pre-release`.

### Sync Source Only (no JAR download)

Use this when you want to pull the latest official source comments/docs without re-downloading the server JAR. Hypixel Studios pushes source updates on a regular cadence independently of JAR releases.

```cmd
REM Release patchline (default):
.\.github\skills\update-server-lib\scripts\Sync-Source.cmd

REM Pre-release patchline:
.\.github\skills\update-server-lib\scripts\Sync-Source.cmd pre-release
```

### Update Skills (AI-driven, no script needed)

Skill updates are done via Copilot — just ask: **"update hytale skills"** or **"check for skill updates"**. This triggers the `update-hytale-skills` skill which:
- Fetches the latest MDX docs from HytaleModding/site
- Cross-references against `lib/hytale-shared-source/HytaleServer/`
- Updates the relevant `SKILL.md` files in `.github/skills/`

Run this after a source sync when you want to bring Copilot's knowledge in sync with the updated API.

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
- Clones or pulls `lib/hytale-shared-source` (official source with comments, matching the patchline branch)
- Copies Server assets to `lib/Server`
- Copies Common assets to `lib/Common`
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
| `PATCHLINE` | `release` | Patchline to download from (`release` or `pre-release`) |

## Troubleshooting

### Authentication Errors
If you get 401 or authentication errors, delete `.hytale-downloader-credentials.json` in your downloader directory (default: `C:\hytale-downloader\`) and run the downloader manually to re-authenticate.

### Source Access Denied
If `git clone` for `hytale-shared-source` fails with a 403 or 404, your GitHub account may not yet have org access. Visit https://accounts.hytale.com/shared-source to enroll.
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
