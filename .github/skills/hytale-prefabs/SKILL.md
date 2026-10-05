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

---

## 3D Prefab Preview (interactive UI widget, Update 5+)

The vanilla prefab browser (`Pages/PrefabListPage.ui`, used by `PrefabPage` in `BuilderTools`) shows a rotate/tilt/zoom/pan-able live 3D preview via the
`PrefabPreviewComponent` widget. This is available to any custom server UI, not just the vanilla browser.

**`.ui` markup:**

```
PrefabPreviewComponent #PrefabPreview {
  AllowDragYaw: true;
  AllowDragPitch: true;
  AllowZoom: true;
  AllowPan: true;
  ShowAnchor: true;
}
```

**Feeding it block data** — the widget has no server-side handle; the client renders whatever block data was last delivered via a
`com.hypixel.hytale.protocol.packets.buildertools.BuilderToolPrefabPreview` packet sent directly to the viewing player (not through `UICommandBuilder`):

```java
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolPrefabPreview;

BuilderToolPrefabPreview packet = new BuilderToolPrefabPreview();
packet.tilt = 23;            // degrees
packet.spinSpeed = 27;       // auto-rotate speed; 0 = static
packet.previewScale = 100;   // max on-screen size
if (selection.getBlockCount() > BuilderToolsPlugin.STREAM_TO_CLIENT_LIMIT) {
    packet.blocksOmitted = true;
    packet.bounds = selection.toEditorSelectionBounds(); // shows a bounding box instead of blocks
} else {
    var editorPacket = selection.toPacket();
    packet.blocksChange = editorPacket.blocksChange;
    packet.fluidsChange = editorPacket.fluidsChange;
    packet.entityChanges = editorPacket.entityChanges;
}
packet.biomeTint = ...; // sample from the viewer's local chunk, see PrefabPage.applyTintFromPlayerPosition
packet.waterTint = ...;
playerRef.getPacketHandler().write(packet); // sent directly, bypasses the UI command builder
```

Send an empty packet (`blocksChange`/`fluidsChange` left null) to clear the preview. Only one preview is shown per player at a time — sending a new packet
replaces it. `BuilderToolsPlugin.STREAM_TO_CLIENT_LIMIT` (4,000,000 blocks) is the cutoff for streaming full block data vs. just a bounding box.

> A real custom-UI example: `com.hytalecolonies.ui.ConstructorPrefabPage` (HytaleColonies plugin) embeds this in a construction-order prefab picker —
> single click on a file previews it (packet above), an explicit `#LoadButton` (bound to `FileBrowserEventData.KEY_BROWSE`) confirms the selection and arms
> a `com.hytalecolonies.ConstructionPlacementSession` (the plugin's own state, not a BuilderTools clipboard).

---

## Prefab Preview Holograms (world-placed ghosts, Update 6+)

`PersistentPrefabPreview` (`com.hypixel.hytale.server.core.modules.entity.component`) spawns a ghost/hologram entity in the world showing an unrotated prefab,
optionally revealed bottom-up one layer at a time. Distinct from the `.ui` widget above — this is a real (but non-solid, non-colliding) world entity.

```java
import com.hypixel.hytale.server.core.modules.entity.component.PersistentPrefabPreview;
import com.hypixel.hytale.math.vector.Rotation3f;

// Spawn: key is resolved via PrefabStore.findBrowsablePrefabPath (path relative to a pack's
// "Server/Prefabs/" dir, no .prefab.json/.lpf suffix), layers = how many bottom layers to reveal
// (Integer.MAX_VALUE = show everything).
Ref<EntityStore> previewRef = PersistentPrefabPreview.spawn(store, position, new Rotation3f(), "Monuments/MyStructure", Integer.MAX_VALUE);

// Reveal progressively (e.g. driven by real build progress, or a timer for a guided-build effect).
PersistentPrefabPreview.updateLayers(store, previewRef, 3);

// Remove when done.
PersistentPrefabPreview.remove(store, previewRef);
```

Also exposed to players directly via `/prefabpreview spawn <key> [layers]`, `/prefabpreview layers <n>`, `/prefabpreview remove [radius]` (builtin
`BuilderTools` command, `HytalePermissionsProvider.GROUP_WORLD_EDITOR`).

**Important details (verified from `PrefabPreviewSystems.PrefabPreviewSetup`):**
- The component only persists a `prefabKey` + layer count; on (re)load it **always re-resolves the prefab fresh from disk** via
  `PrefabStore.findBrowsablePrefabPath` + `getPrefab`. There is no way to feed it an already-rotated/mutated `BlockSelection` object directly. This is NOT a
  dead end: the hologram entity has a normal `TransformComponent` (the `spawn()` overloads all take a `Rotation3f`), so a rotated placement is shown
  correctly by orienting the whole hologram via that rotation, the same way any other rotated entity/prop works -- exactly like base-game rotated prefabs.
  The catch is *sourcing* the angle: `BuilderToolsPlugin.BuilderState` tracks the clipboard's cumulative rotation (`cumulativeRotX/Y/Z`) but does not expose
  a public getter, so a plugin cannot read "how much did the player rotate the vanilla clipboard". **Don't build on top of the vanilla clipboard/paste tool
  for this reason** -- own the rotation value yourself instead (see the HytaleColonies pattern below), which sidesteps the problem entirely and needs no
  detection/guessing of any kind.
- Tint (biome/water) is auto-derived from the anchor's world position (`PrefabPreviewSystems.deriveTint`) unless you use the tint-override `spawn`
  overload (persists an explicit tint that survives restarts, e.g. for previews floating away from loaded chunks).
- Block data is resolved once and cached (`PrefabPreview.getBlocks()`); a viewer receives the full list only the first time the entity becomes visible to
  them. Calling `updateLayers` after that only re-sends the layer count (cheap), not the block list.
- `visibleLayerCount` reveal semantics are local-Y-based (relative to the prefab's own bounding box), independent of the entity's world Y position.

**Real usage in this codebase -- a fully self-owned placement tool, not a BuilderTools reskin:** HytaleColonies' constructor tool
(`Tool_Colony_Constructor_PlacePrefab`) does not use `BuilderTool`/`Builder_Tool` interactions, the clipboard, or paste-packet interception at all. Instead:
  - `Use` -> `ConstructionOpenPicker` interaction opens `ConstructorPrefabPage` (the 3D-preview picker above), which arms a
    `com.hytalecolonies.ConstructionPlacementSession` (per-player, plugin-owned state: prefab id, position, rotation, ghost ref) with the chosen prefab.
  - `Primary` -> `PlaceConstructionGhostInteraction` (`SimpleBlockInteraction`, gets the target block position for free from the standard Interaction
    system) snaps the player's current body yaw to 0/90/180/270 and (re)spawns a `PersistentPrefabPreview` ghost there via
    `com.hytalecolonies.utils.ConstructionPlacementUtil` -- repeatable, so the player can reposition before confirming.
  - `Secondary` -> `ConfirmConstructionPlacementInteraction` finalizes the session into a real order, storing the exact `rotationDegrees` the player
    chose directly on the order entry -- no detection, no clipboard, no guessing, because the plugin was the one that decided the rotation in the first
    place.
  - `com.hytalecolonies.utils.ConstructionPreviewUtil` then drives a *second*, independent hologram per active order (toggled Off/Progress/Full from the
    constructor workstation UI), reusing the same `rotationDegrees` value stored on the order.
  - Item JSON declares its own `Interactions`/`Interaction`/`RootInteraction` asset chain (`Server/Item/{Interactions,RootInteractions}/Colony/*.json`) --
    the same pattern used by any custom Java interaction in this codebase, registered via `Interaction.CODEC.register(...)`. See `hytale-items` skill.

