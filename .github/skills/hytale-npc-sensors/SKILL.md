---
name: hytale-npc-sensors
version: 1
source: https://hytalemodding.com/official-documentation/npc/
authors:
  - name: "HytaleModding"
    url: "https://github.com/HytaleModding"
  - name: "Hypixel Studios"
    url: "https://hytale.com/"
tags: [hytale, npc, sensors, detection, filters, block-sensors, navigation]
---

# Hytale NPC Sensors

Full reference for the NPC sensor system. Covers all sensor types (logic, entity detection, world/block, state machine, interaction, environment), entity filters, standard detection patterns, block detection sensors, target slot system, and navigation state queries.

Load alongside `hytale-npc-templates` when defining NPC detection logic, building state transition conditions, or working with block/environment sensors.

## Triggers

- NPC sensor
- sensor type
- Sensor
- Standard_Detection
- Component_Sensor
- entity detection
- Player sensor
- Mob sensor
- Target sensor
- Beacon sensor
- Block sensor
- BlockChange
- BlockType
- SearchRay
- ReadPosition
- Nav sensor
- NavState
- entity filter
- LineOfSight filter
- Attitude filter
- target slot
- LockedTarget
- TargetSlot
- target slot system
- block reservation
- block detection
- navigation state query
- AT_GOAL
- BLOCKED
- ThrottleDuration
- Standard Detection
- Damage Check
- attitude group
- DetectionRange
- HearingRange
- ViewRange
- ViewSector
- filter type
- ItemInHand filter
- Inventory filter
- Stat filter
- EntityEffect filter

---

## Sensors

Sensors are conditions that gate instruction execution. All names are the exact JSON `"Type"` strings registered in the engine.

### Logic / Composition

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `State` | Matches current NPC state | `State`, `IgnoreMissingSetState` |
| `Any` | Always matches | `Once` |
| `And` | All sub-sensors must match | `Sensors` (array), `AutoUnlockTargetSlot` |
| `Or` | Any sub-sensor must match | `Sensors` (array), `AutoUnlockTargetSlot` |
| `Not` | Negate a sub-sensor | `Sensor`, `UseTargetSlot`, `AutoUnlockTargetSlot` |
| `Eval` *(Experimental)* | Evaluate JavaScript expression; accessible vars: `health`, `blocked` | `Expression` |
| `Switch` | Check if a computed boolean is `true` | `Switch` (computable boolean) |
| `Random` | Alternates returning `true`/`false` for random durations | `TrueDurationRange`, `FalseDurationRange` |

### Entity Detection

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `Player` | Detect nearby players; provides player target | `Range`, `MinRange`, `Filters[]`, `LockOnTarget`, `LockedTargetSlot` |
| `Mob` | Detect nearby NPCs/mobs; provides NPC target | `Range`, `MinRange`, `GetPlayers`, `GetNPCs`, `Filters[]`, `LockOnTarget` |
| `Self` | Test if the NPC itself matches entity filters | `Filters[]`; provides vector position |
| `Target` | Check if NPC has a marked target in a slot | `TargetSlot` (default `LockedTarget`), `Range`, `Filters[]` |
| `Beacon` | Listen for inter-NPC broadcasts | `Message`, `Range`, `TargetSlot`, `ConsumeMessage` |
| `Kill` | Detect when NPC makes a kill | `TargetSlot`; provides vector position |
| `Damage` | Detect incoming damage; provides player/NPC/item target | `Combat`, `Friendly`, `Drowning`, `Environment`, `Other`, `TargetSlot` |
| `Count` | Check if a count of NPCs or players in range is within bounds | `Count` ([min,max]), `Range` ([min,max]), `IncludeGroups`, `ExcludeGroups` |
| `HasHostileTargetMemory` | Check if hostile target memory has an entry | — |
| `EntityEvent` | Detect damage/death/interaction of an NPC group entity | `NPCGroup`, `Range`, `EventType` (DAMAGE/DEATH/INTERACTION), `TargetSlot`, `SearchType` |

### World / Block

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `Block` | Detect block at offset; provides block position | `Offset`, `Tag` / `BlockType` |
| `BlockType` | Check block type at position | `Offset`, `BlockType` |
| `BlockChange` | Detect nearby block breaks/placements | `Range` |
| `CanPlaceBlock` | Check if NPC can place at offset | `Direction`, `Offset` |
| `SearchRay` | Raycast for blocks or entities | `Range`, `Direction` |
| `Light` | Check light level at NPC position | `Min`, `Max` |
| `Time` | Check in-game time of day | `Min`, `Max` |
| `ReadPosition` | Retrieve a stored position slot; provides position | `Slot`, `Range`, `MinRange` |
| `Path` | Check pathfinding status | `PathType` (`Found`, `Failed`, `None`) |

### Items

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `DroppedItem` | Detect nearby dropped items; provides position | `Range`, `Items[]` (glob patterns) |

### State Machine

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `IsBusy` | Check if NPC is in a `BusyStates` state defined on the role | — |
| `Flag` | Check a named boolean flag | `Name`, `Set` |
| `Timer` | Check if a named timer exists and is in a given state/range | `Name`, `State` (RUNNING/PAUSED/STOPPED/ANY), `TimeRemainingRange` |
| `Alarm` | Check alarm state (SET/UNSET/PASSED) | `Name`, `State`, `Clear` |
| `Animation` | Check if a specific animation is playing | `Slot`, `Animation` |
| `Age` | Trigger when NPC age falls in a range (ISO-8601 duration/period) | `AgeRange` ([from, to]) |

### Interaction

| Sensor Type | Description |
|------------|-------------|
| `HasInteracted` | Detect that a player has interacted with this NPC |
| `CanInteract` | Check if interaction is possible |
| `InteractionContext` | Access data from the current interaction |

### Environment / Movement

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `Leash` | Check distance from leash point | `Range` |
| `InAir` | NPC is airborne | — |
| `OnGround` | NPC is on the ground | — |
| `InWater` | NPC is in water | — |
| `Weather` | Check current weather | — |
| `Nav` | Check navigation/pathfinding state | — |
| `MotionController` | Check active motion controller type | — |

---

## Entity Filters

Used in `Filters[]` on `Player`, `Mob`, and `Target` sensors.

| Filter Type | Description |
|-------------|-------------|
| `Attitude` | Filter by attitude toward the **locked target** (HOSTILE/FRIENDLY/NEUTRAL/IGNORE/REVERED) |
| `LineOfSight` | Only entities in unobstructed line of sight |
| `HeightDifference` | Filter by vertical height difference between NPC and entity |
| `ViewSector` | Filter to entities within a view cone angle |
| `Combat` | Filter to entities currently in a given combat state (`Mode`: Melee/Ranged/Attacking/Any/None…) |
| `ItemInHand` | Filter by item the entity is holding (`Items` glob array) |
| `NPCGroup` | Filter by NPC group (`IncludeGroups` or `ExcludeGroups`; exactly one required) |
| `MovementState` | Filter by movement state (WALKING/RUNNING/CROUCHING/IDLE/JUMPING/FALLING…) |
| `SpotsMe` | Filter to entities that can see the NPC (configurable `ViewAngle`, `ViewTest`, `TestLineOfSight`) |
| `StandingOnBlock` | Filter by the block directly beneath the entity (`BlockSet`) |
| `InsideBlock` | Filter to entities inside a specific block (`BlockSet`) |
| `Stat` | Filter by entity stat value comparison (`Stat`, `StatTarget`, `RelativeTo`, `RelativeToTarget`) |
| `Inventory` | Filter by inventory contents |
| `Flock` | Filter by flock membership/status (`FlockStatus`, `FlockPlayerStatus`, `Size`, `CheckCanJoin`) |
| `Altitude` | Filter by height above ground (`min`, `max`) |
| `Not` | Negate a filter |
| `And` | All filters must match |
| `Or` | Any filter must match |

### Filter uniqueness rule

Each filter type can appear **at most once** per flat `Filters` array. To match multiple items, list them all in a single filter's `Items` array (OR logic):

```json
{
  "Type": "Player",
  "Range": 15,
  "Filters": [
    { "Type": "LineOfSight" },
    { "Type": "ViewSector", "ViewSector": 180 },
    { "Type": "ItemInHand", "Items": [ "*_Axe*", "*_Hatchet*" ] }
  ]
}
```

> Duplicating a filter type (e.g. two `ItemInHand` entries) causes `has defined a filter of type X more than once` at startup.

### Mob Sensor (NPC Group Filtering)

```json
{
  "Sensor": {
    "Type": "Mob",
    "Range": 2.5,
    "Filters": [
      { "Type": "NPCGroup", "IncludeGroups": { "Compute": "FoodNPCGroups" } },
      { "Type": "LineOfSight" }
    ]
  }
}
```

### Sensor with Filters

```json
{
  "Sensor": {
    "Type": "Target",
    "Range": { "Compute": "AttackDistance" },
    "Filters": [
      { "Type": "LineOfSight" }
    ]
  },
  "Actions": [ ... ]
}
```

---

## Detection System

### Standard Detection Sensor

Handles sight (view range + cone + line of sight) and hearing (range-based, ignores crouching/still targets, blocked by walls).

```json
{
  "Sensor": {
    "Reference": "Component_Sensor_Standard_Detection",
    "Modify": {
      "ViewRange": { "Compute": "ViewRange" },
      "ViewSector": { "Compute": "ViewSector" },
      "HearingRange": { "Compute": "HearingRange" },
      "ThroughWalls": false,
      "AbsoluteDetectionRange": { "Compute": "AbsoluteDetectionRange" },
      "Attitudes": ["Hostile"]
    }
  },
  "Actions": [
    { "Type": "State", "State": "Alerted" }
  ]
}
```

**Detection order:**
1. **Absolute detection range** — guaranteed detection within this radius
2. **View range/sector** — line-of-sight check within the view cone
3. **Hearing range** — detects walking/running (non-crouching) targets, blocked by walls

### Reduced Detection (Distracted States)

Divide ranges by a penalty factor for sleeping/eating states:

```json
"ViewRange": { "Compute": "ViewRange / DistractedPenalty" },
"HearingRange": { "Compute": "ViewRange / DistractedPenalty" }
```

### Damage Check Component

Detects incoming damage and transitions to combat/alert:

```json
{
  "Reference": "Component_Instruction_Damage_Check",
  "Modify": {
    "_ExportStates": ["Alerted", "Alerted"],
    "AlertedRange": { "Compute": "AlertedRange" }
  }
}
```

### Attitude Groups

Define NPC relationship groups:

```json
{
  "Groups": {
    "Friendly": ["Goblin"],
    "Hostile": []
  }
}
```

### Template Attitude Configuration

```json
"DefaultPlayerAttitude": "Hostile",
"DefaultNPCAttitude": "Ignore",
"AttitudeGroup": { "Compute": "AttitudeGroup" }
```

---

## Block Detection Sensors

### Block: Sensor — cached block search

Scans for any block in a `BlockSet` within a radius. **The result is cached** — the sensor does not re-scan every tick once a block is found; it remembers the found block until it changes/is removed or `ResetBlockSensors` is called. **All `Block` sensors on the same NPC that search the same `BlockSet` share the same cached target.** Provides a vector position.

```json
{
  "Sensor": {
    "Type": "Block",
    "Blocks": { "Compute": "TreeBlocks" },
    "Range": 20.0,
    "MaxHeight": 8.0,
    "Reserve": true
  },
  "BodyMotion": { "Type": "Seek", "StopDistance": 1.5, "SlowDownDistance": 3.0 }
}
```

| Field | Default | Description |
|-------|---------|-------------|
| `Range` | — | Search radius (required) |
| `MaxHeight` | 4.0 | Vertical search range |
| `Random` | `false` | Pick a random matching block; otherwise pick the closest |
| `Reserve` | `false` | Reserve this block so other NPCs skip it |

Use `ResetBlockSensors` action to clear the cached result (e.g., after the block is destroyed):

```json
{ "Type": "ResetBlockSensors" }
```

### BlockChange: Sensor — detect block events

Fires when a block in a `BlockSet` within range is damaged, destroyed, or interacted with. Provides player/NPC target.

```json
{
  "Sensor": {
    "Type": "BlockChange",
    "BlockSet": { "Compute": "MineableBlocks" },
    "Range": 15.0,
    "EventType": "DESTRUCTION",
    "SearchType": "PlayerFirst"
  },
  "Actions": [ { "Type": "State", "State": "Alert" } ]
}
```

| `EventType` | Description |
|-------------|-------------|
| `DAMAGE` | Block is being attacked (default) |
| `DESTRUCTION` | Block is fully destroyed |
| `INTERACTION` | Block is interacted with (e.g., right-clicked) |

| `SearchType` | Description |
|--------------|-------------|
| `PlayerOnly` | Only events from players (default) |
| `NpcOnly` | Only events from NPCs |
| `PlayerFirst` | Players first, then NPCs |
| `NpcFirst` | NPCs first, then players |

### BlockType: Sensor — check block at a position

Wraps another sensor (which provides a position) and checks whether the block at that position matches a `BlockSet`.

```json
{
  "Sensor": {
    "Type": "BlockType",
    "Sensor": { "Type": "ReadPosition", "Slot": "NavTarget", "Range": 5.0 },
    "BlockSet": { "Compute": "TreeBlocks" }
  }
}
```

### SearchRay: Sensor — directional block detection

Fires a ray at a fixed angle from the NPC's heading to detect blocks. The result is **cached** and only re-tested when the NPC rotates or moves past thresholds. Provides vector position.

```json
{
  "Sensor": {
    "Type": "SearchRay",
    "Name": "ForwardTreeCheck",
    "Angle": 0.0,
    "Range": 8.0,
    "Blocks": { "Compute": "TreeBlocks" },
    "MinRetestAngle": 5.0,
    "MinRetestMove": 1.0,
    "ThrottleTime": 0.5
  },
  "Actions": [ { "Type": "StorePosition", "Slot": "NavTarget" } ]
}
```

`Angle` field: 0 = horizontal (straight ahead), positive = downward, range −90..90.

Use `ResetSearchRays` action to clear cached results:

```json
{ "Type": "ResetSearchRays", "Names": ["ForwardTreeCheck"] }
```

### StorePosition: Action — save sensor position to a slot

Stores the vector position provided by the instruction's sensor into a named slot for later navigation.

```json
{
  "Sensor": { "Type": "Block", "Blocks": { "Compute": "TreeBlocks" }, "Range": 20.0 },
  "Actions": [ { "Type": "StorePosition", "Slot": "NavTarget" } ]
}
```

---

## Target Slot System

NPCs lock onto entities using **named target slots** (strings). The default slot is `"LockedTarget"`. Multiple custom slots allow tracking several entities simultaneously.

| Action / Sensor | Slot field | Description |
|-----------------|------------|-------------|
| `Mob` / `Player` / `Damage` sensor | `LockedTargetSlot` | Slot where the matched entity is stored |
| `Target` sensor | `TargetSlot` | Test if a specific slot has a valid entity |
| `SetMarkedTarget` action | `TargetSlot` | Explicitly copy sensor-provided target into a slot |
| `ReleaseTarget` action | `TargetSlot` | Clear a slot |

```json
// Store the attacker in a custom slot on damage:
{
  "Sensor": { "Type": "Damage", "Combat": true, "TargetSlot": "Attacker" },
  "Actions": [ { "Type": "State", "State": "Combat" } ]
}

// Later: seek toward "Attacker":
{
  "Sensor": { "Type": "Target", "TargetSlot": "Attacker", "Range": 50 },
  "BodyMotion": { "Type": "Seek", "StopDistance": 1.5, "SlowDownDistance": 3.0 }
}
```

---

## Navigation State Query (Nav: Sensor)

`Nav: Sensor` queries the NPC's current pathfinder state. Use it to detect arrival, failure, or being stuck.

| `NavState` flag | Meaning |
|-----------------|---------|
| `INIT` | Doing nothing / idle |
| `PROGRESSING` | Moving or computing a path |
| `AT_GOAL` | Reached target |
| `BLOCKED` | Can't advance any further |
| `ABORTED` | Search stopped, target not reached |
| `DEFER` | Delaying / throttled retry |

```json
{
  "Sensor": {
    "Type": "Nav",
    "NavStates": ["AT_GOAL"],
    "ThrottleDuration": 0.0
  },
  "Actions": [ { "Type": "State", "State": "Working" } ]
}
```

`ThrottleDuration`: minimum seconds the NPC must stay in the queried state before the sensor fires (useful with `BLOCKED`/`ABORTED` to avoid reacting to transient hiccups).

---

## Related Skills

- `hytale-npc-templates` — Core template structure, states, parameters, instruction flags
- `hytale-npc-actions` — All action types, motions, timers, alarms, flags
- `hytale-npc-combat` — Combat AI patterns, attack interactions, beacons
- `hytale-npc-pathfinding` — Plugin-driven A* navigation via ReadPosition/Seek
- `hytale-npc-components` — Reusable JSON instruction components
