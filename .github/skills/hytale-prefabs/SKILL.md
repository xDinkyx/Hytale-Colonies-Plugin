---
name: hytale-prefabs
description: Documents Hytale's prefab system for creating, saving, loading, and managing reusable structures — both via in-game commands and programmatically via PrefabStore. Use when creating prefabs, saving structures, loading prefabs, editing prefab worlds, iterating prefab blocks in code, or integrating with BuilderTools. Triggers - prefab, prefab system, /prefab, /editprefab, reusable structure, structure, save structure, load structure, prefab world, prefab editing, prefab commands, paste brush, selection brush, PrefabStore, BlockSelection, getServerPrefab, getPrefab, forEachBlock, BlockHolder, getAnchorX, BuilderToolsPlugin, BuilderState, getSelection, programmatic prefab, prefab API, construction order, asset pack prefab.
---

# Hytale Prefabs

Reference for Hytale's prefab system — creating, saving, loading, and managing reusable structures via in-game commands.

> **Source:** <https://hytalemodding.dev/en/docs/guides/prefabs>
> **Related skills:** For spawning entities within prefabs, see `hytale-spawning-entities`. For world generation with prefabs, see `hytale-world-gen`.

---

## Quick Reference

| Task | Approach |
|------|----------|
| Create a new prefab world | `/editprefab new <world_name>` |
| Save a prefab | `/prefab save` (use selection brush first) |
| Load a prefab | `/prefab load` |
| List all prefabs | `/prefab list` |
| Delete a prefab | `/prefab delete` |
| Exit prefab editing world | `/editprefab exit` |
| Select a prefab area | Use the selection brush in the editing world |
| Paste a prefab | Use the Paste brush, press `E` to select from menu |
| See command options | Append `--help` to any command (e.g., `/prefab save --help`) |

---

## Key Concepts

- **Prefab editing world** — A dedicated world created for building and editing prefabs. Created with `/editprefab new`.
- **Prefabs** — Physical structures saved as JSON files. A single editing world can contain multiple prefabs.
- **Selection brush** — In-game tool used to select the area to save as a prefab.
- **Paste brush** — In-game tool used to place saved prefabs. Press `E` to open the selection menu.

---

## Basic Workflow

1. `/editprefab new my_prefab_world` — Create a new prefab editing world
2. Build the structure you want in the editing world
3. Use the **selection brush** to select the area
4. `/prefab save` — Save the selected area as a prefab
5. `/editprefab exit` — Exit the editing world
6. Use the **Paste brush**, press `E` to select the prefab from the "server" dropdown (top-right of menu)

---

## Commands

### `/prefab`

Manages prefab files on disk.

| Subcommand | Description |
|------------|-------------|
| `save` | Saves the prefab to the file system |
| `load` | Loads a prefab into the game |
| `delete` | Deletes the prefab from the file system |
| `list` | Lists all prefabs in the file system |

### `/editprefab`

Manages the physical structure editing workflow.

| Subcommand | Description |
|------------|-------------|
| `new` | Creates a new prefab editing world from scratch |
| `load` | Creates a new editing world with an existing prefab pasted in |
| `exit` | Exits the current prefab editing world |
| `select` | Selects the prefab area the user is looking at (within 200 blocks) |
| `save` | Saves the current prefab using the existing or selected area |
| `saveui` | Opens the save UI for managing all prefabs in the current world |
| `saveas` | Saves the selected prefab into a new file |
| `kill` | Despawns all entities in the currently selected prefab |
| `setbox` | Sets the bounding box of the currently selected prefab |
| `info` | Shows information about the currently selected prefab |
| `tp` | Opens teleport UI to jump to a prefab in the current editing world |
| `modified` | Lists all modified prefabs with unsaved changes |

> Use `--help` at the end of any command to see all available options (e.g., `/editprefab save --help`).

---

## Known Issues

- After saving edits to an existing prefab, the prefab may not reflect changes immediately. **Workaround:** exit and re-enter the world.
- When pasting a prefab, it may not always display accurately. **Workaround:** press `T` to toggle the material view; usually at least one view is correct.
- `/prefab delete` may error with `Assert not in thread`.

---

## Edge Cases & Gotchas

- The world name in `/editprefab new <world_name>` is the *world* name, not the prefab name. Worlds can contain multiple prefabs.
- Saved prefabs appear in the "server" dropdown of the Paste brush menu (top-right).
- Prefabs are saved as JSON files to the server file system.

---

## Programmatic Prefab API

Prefabs can also be loaded and inspected from plugin code via `PrefabStore`.

> **Source:** `CoreServer/.../prefab/PrefabStore.java`, `CoreServer/.../prefab/selection/standard/BlockSelection.java`

### PrefabStore

`PrefabStore` is a singleton; access it with `PrefabStore.get()`.

```java
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;

// Load from the server prefabs directory (prefabs/<key>.prefab.json).
// Returns BlockSelection (throws PrefabLoadException if not found).
BlockSelection prefab = PrefabStore.get().getServerPrefab("MyPrefab.prefab.json");

// Load from an explicit Path (results are cached).
BlockSelection prefab = PrefabStore.get().getPrefab(path);

// Search all loaded asset packs for a prefab key — returns null if not found.
BlockSelection prefab = PrefabStore.get().getAssetPrefabFromAnyPack("MyPrefab.prefab.json");

// Load all prefabs inside a server-side directory.
Map<Path, BlockSelection> prefabMap = PrefabStore.get().getServerPrefabDir("MyFolder");
```

**Loading from an asset-pack ZipFS** (required when the prefab ships inside the plugin's asset pack):

```java
import com.hypixel.hytale.server.core.asset.AssetModule;

Path packRoot   = AssetModule.get().getBaseAssetPack().getRoot();
Path prefabPath = packRoot.getFileSystem().getPath("/Server/Prefabs/MyPrefab.prefab.json");
BlockSelection prefab = PrefabStore.get().getPrefab(prefabPath);
```

> `getPrefab(Path)` caches by absolute normalized path; repeated calls return the same instance.
> `getServerPrefab(String)` resolves the key inside `prefabs/` with path-traversal protection.

### BlockSelection — the prefab data type

`BlockSelection` is the prefab representation. There is no separate `Prefab` or `ServerPrefab` class.

**Iterate all blocks** (callback, not a list — no allocation overhead):

```java
prefab.forEachBlock((int lx, int ly, int lz, BlockSelection.BlockHolder block) -> {
    if (block.filler() != 0) return; // slave filler cell — skip; auto-managed by engine
    int blockId   = block.blockId();  // numeric asset ID
    int rotation  = block.rotation(); // RotationTuple index
    // lx/ly/lz are local (prefab-space) coords
});
```

**`BlockHolder` record fields:**

| Field | Type | Description |
|-------|------|-------------|
| `blockId()` | `int` | Numeric block asset ID (use `BlockType.getAssetMap().getAsset(id)` to resolve) |
| `rotation()` | `int` | `RotationTuple` index |
| `filler()` | `int` | Non-zero = slave filler cell auto-managed by the base block; skip these |
| `supportValue()` | `int` | Physics support value |
| `holder()` | `Holder<ChunkStore>` | Block-component state, or `null` for stateless blocks |

**Anchor and world-space position:**

The anchor offsets align the prefab's local origin to the desired world position:

```java
int wx = lx + buildOrigin.x - prefab.getAnchorX();
int wy = ly + buildOrigin.y - prefab.getAnchorY();
int wz = lz + buildOrigin.z - prefab.getAnchorZ();
```

**Other useful methods:**

```java
prefab.getAnchorX() / getAnchorY() / getAnchorZ() // anchor offsets
prefab.getSelectionMin() / getSelectionMax()        // bounding box (Vector3i)
prefab.getBlockCount()                              // total stored block count
prefab.getBlockAtWorldPos(x, y, z)                 // block ID at world pos
prefab.getBlockHolderAtWorldPos(x, y, z)           // full BlockHolder at world pos
```

**Saving:**

```java
// Save to server prefabs directory; throws PrefabSaveException if already exists.
PrefabStore.get().saveServerPrefab("MyPrefab.prefab.json", prefab, /*overwrite*/ false);
```

---

## BuilderToolsPlugin Integration

`BuilderToolsPlugin` is a built-in Hytale plugin (`com.hypixel.hytale.builtin.buildertools`). Use it to access the player's current rotated clipboard `BlockSelection` so rotation is baked in before handing work off to colonists.

**Get or queue work on a player's builder state** (must be called on the world thread):

```java
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.builtin.buildertools.BuilderState;

// Queue a task that runs with the player's BuilderState.
BuilderToolsPlugin.addToQueue(player, playerRef, (ref, builderState, componentAccessor) -> {
    BlockSelection selection = builderState.getSelection(); // nullable; the current clipboard
    if (selection != null) {
        // selection has player rotation already applied
    }
});

// Or get the BuilderState directly (synchronous, on world thread).
BuilderState state = BuilderToolsPlugin.getState(player, playerRef);
```

**Typical plugin pattern** — intercept the paste packet, capture the rotated selection, then store a construction order:

```java
// 1. Register a PlayerPacketFilter to intercept BuilderToolPasteClipboard (packet 407).
// 2. On the world thread, call BuilderToolsPlugin.addToQueue to read the rotated selection.
// 3. Cache the BlockSelection and record the build site for colonist use.

world.execute(() -> {
    BuilderToolsPlugin.addToQueue(player, playerRef, (ref, builderState, accessor) -> {
        BlockSelection rotated = builderState.getSelection();
        if (rotated != null) {
            mySelectionCache.put(buildSite, rotated.cloneSelection());
        }
        // create and enqueue the construction order...
    });
});
```

> **Requirement:** `BuilderTools` must be listed as a dependency in `manifest.json` and be present on the server.
> `builderState.getSelection()` returns `null` if the player has no clipboard loaded.

