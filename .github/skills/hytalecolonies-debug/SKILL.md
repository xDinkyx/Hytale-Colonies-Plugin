---
name: hytalecolonies-debug
version: 1
description: >
  Documents the debug logging and category system for the HytaleColonies plugin.
  Covers DebugLog usage (NPC UUID tagging), DebugCategory registration, DebugConfig
  persistence, DebugConfigUI, and DebugConfig.ui. Use whenever adding new log calls,
  adding new debug categories, or working with the runtime debug configuration UI.
tags: [hytalecolonies, debug, logging, debugcategory, debugconfig, debugconfigui]
---

# HytaleColonies Debug System

## Overview

The plugin has a layered debug system:

| Class | Role |
|---|---|
| `DebugCategory` (enum) | Enumerates all log categories; each has an independent minimum `Level` |
| `DebugLog` | Gate-checked logging utility; all log calls go through here |
| `DebugConfig` | Persistent BSON config; stores the level name per category and draw-toggle booleans |
| `DebugConfigUI` (Java) | `InteractiveCustomUIPage` that lets admins cycle levels and toggle draw flags at runtime |
| `DebugConfig.ui` | Hytale `.ui` layout file with one `TextButton` row per category |

All four artefacts (`DebugCategory`, `DebugConfig`, `DebugConfigUI`, `DebugConfig.ui`) must be
kept in sync whenever a category is added, removed, or renamed.

---

## Rule: NPC logs must include the colonist UUID

Every `DebugLog` call that is made inside a method with access to a `Ref<EntityStore>` and a
`Store<EntityStore>` for a colonist entity **must** include the NPC UUID in the log message using
`DebugLog.npcId(ref, store)`.

### Pattern

```java
DebugLog.info(DebugCategory.JOB_SYSTEM,
        "[Tag] [%s] Human-readable message here.", DebugLog.npcId(ref, store));
```

- The UUID placeholder `%s` always appears as the **first** format argument, immediately after the
  bracketed tag.
- The tag format is `[SystemName] [%s]` -- e.g. `[ColonistJob] [%s]`, `[WoodsmanJob] [%s]`.
- `DebugLog.npcId(ref, store)` returns the UUID string of the NPC, or `"?"` if no
  `UUIDComponent` is present.

### When the ref is obtained inline (not yet a local variable)

```java
// archetypeChunk / chunk -- ref not yet extracted as a local variable
DebugLog.fine(DebugCategory.JOB_SYSTEM,
        "[ColonistJob] [%s] Tick state %s.",
        DebugLog.npcId(archetypeChunk.getReferenceTo(index), store),
        job.getCurrentTask());
```

### When store is `entityStore.getStore()` inside a world.execute() lambda

```java
DebugLog.info(DebugCategory.MINER_JOB,
        "[MinerJob] [%s] Claimed next mine block at %s.",
        DebugLog.npcId(ctx.colonistRef, entityStore.getStore()), nextBlock);
```

### When ref/store come from an ECS system tick

When iterating inside an `EntityTickingSystem` or `DelayedEntitySystem`, use the chunk ref and store directly:

```java
DebugLog.fine(DebugCategory.WOODSMAN_JOB,
        "[WoodsmanJob] [%s] Working -- blockId=%d.",
        DebugLog.npcId(archetypeChunk.getReferenceTo(index), store), blockId);
```

---

## Adding a new log category

Follow all four steps every time you add a category. Skipping any step causes a compile error or a
missing row in the Debug Config UI.

### Step 1 -- Add to `DebugCategory` enum

File: `src/main/java/com/hytalecolonies/debug/DebugCategory.java`

```java
public enum DebugCategory {
    // ... existing entries ...
    MY_NEW_CATEGORY("My New Category");   // <-- add here
    // ...
}
```

The string argument is the human-readable display name shown in the UI.

### Step 2 -- Add to `DebugConfig`

File: `src/main/java/com/hytalecolonies/debug/DebugConfig.java`

**2a.** Add a private field (default level `"INFO"` unless the category warrants `"WARNING"`):

```java
private String myNewCategoryLevel = "INFO";
```

**2b.** Add a `KeyedCodec` entry to `CODEC` (append before `.build()`):

```java
.append(new KeyedCodec<>("MyNewCategoryLevel", Codec.STRING),
        (c, v) -> c.myNewCategoryLevel = v, c -> c.myNewCategoryLevel)
.add()
```

**2c.** Add to `applyToCategories()`:

```java
DebugCategory.MY_NEW_CATEGORY.setMinLevel(parseLevel(myNewCategoryLevel));
```

**2d.** Add a case to `setLevelForCategory()`:

```java
case MY_NEW_CATEGORY -> myNewCategoryLevel = name;
```

**2e.** Add a case to `getLevelNameForCategory()`:

```java
case MY_NEW_CATEGORY -> myNewCategoryLevel;
```

### Step 3 -- Add a button row to `DebugConfig.ui`

File: `src/main/resources/Common/UI/Custom/hytalecolonies/DebugConfig.ui`

Add a new row group inside the `TopScrolling` group, following the existing pattern. The button ID
must match what `buttonId(DebugCategory.MY_NEW_CATEGORY)` produces (see Step 4):

```
Group {
    LayoutMode: Left;
    Anchor: (Height: 32);
    Label { Text: "My New Category"; Anchor: (Width: 200, Height: 32); Style: (FontSize: 13, TextColor: #c8d0dc, VerticalAlignment: Center); }
    TextButton #MyNewCategoryButton {
        Text: "INFO";
        Anchor: (Width: 160, Height: 32);
        Style: (Default: (Background: #2b3542, LabelStyle: (FontSize: 13, TextColor: #96a9be, HorizontalAlignment: Center, VerticalAlignment: Center)), Hovered: (Background: #3b4552, LabelStyle: (FontSize: 13, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center)));
    }
}
Group { Anchor: (Height: 6); }
```

Place it in the LOG LEVELS section in an alphabetically or logically sensible position between
existing rows.

### Step 4 -- Verify the button ID mapping

`DebugConfigUI.buttonId()` converts `DebugCategory.MY_NEW_CATEGORY` to `MyNewCategoryButton` by:
1. Splitting the enum name on `_`.
2. Title-casing each part.
3. Appending `Button`.

Examples:

| Enum name | Button ID |
|---|---|
| `MOVEMENT` | `MovementButton` |
| `JOB_SYSTEM` | `JobSystemButton` |
| `COLONIST_DELIVERY` | `ColonistDeliveryButton` |
| `MY_NEW_CATEGORY` | `MyNewCategoryButton` |

The Java UI code (`DebugConfigUI.java`) requires **no manual changes** -- it iterates all
`DebugCategory.values()` automatically at build time. The button must exist in the `.ui` file with
the correct ID.

The default text shown in the `.ui` file (`"INFO"` or `"WARNING"`) is overridden at runtime by
`DebugConfigUI.build()` using the persisted config value, so the default text in the `.ui` file is
for visual reference only.

---

## Quick reference: DebugLog API

```java
// Fine (verbose debug) -- only visible when category.minLevel == FINE
DebugLog.fine(DebugCategory.MOVEMENT, "[Movement] [%s] xzDist=%.2f", npcId, dist);

// Info -- visible at default INFO threshold
DebugLog.info(DebugCategory.JOB_SYSTEM, "[ColonistJob] [%s] State -> %s.", npcId, state);

// Warning -- always visible unless the category is OFF
DebugLog.warning(DebugCategory.JOB_ASSIGNMENT, "[RoleSwitch] [%s] Unknown role '%s'.", npcId, role);

// Severe/error
DebugLog.severe(DebugCategory.COLONIST_LIFECYCLE, "[ColonistRemoval] [%s] Unexpected state.", npcId);
```

All methods accept `printf`-style varargs. The underlying logger is `HytaleColoniesPlugin.LOGGER`.

---

## Level cycle in the UI

The UI cycles through: `FINE` -> `INFO` -> `WARNING` -> `SEVERE` -> `OFF` -> `FINE`.

Display labels used by `DebugConfigUI.levelLabel()`:

| Level | Button text |
|---|---|
| FINE | `FINE (debug)` |
| INFO | `INFO` |
| WARNING | `WARNING` |
| SEVERE | `SEVERE` |
| OFF | `OFF` |
