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

### Global Sensor Base Keys

All sensors inherit:

| Key | Default | Description |
|-----|---------|-------------|
| `Once` | `false` | If true, sensor fires at most once per instruction lifetime |
| `Enabled` | `true` | If false, sensor is permanently disabled |

### SensorEntityBase Inherited Keys

`Player`, `Mob`, `Target`, and `Count` sensors all inherit these keys in addition to the global base:

| Key | Default | Description |
|-----|---------|-------------|
| `MinRange` | `0` | Minimum detection distance |
| `Range` | *(required)* | Maximum detection radius |
| `LockOnTarget` | `false` | Lock the matched entity into `LockedTargetSlot` |
| `Rebind` | `false` | Re-evaluate and update the locked target each tick |
| `LockedTargetSlot` | `"LockedTarget"` | Target slot name to write the locked entity into |
| `AutoUnlockTarget` | `false` | Automatically clear the slot when sensor no longer matches |
| `OnlyLockedTarget` | `false` | Only match if the entity is already in `LockedTargetSlot` |
| `IgnoredTargetSlot` | `null` | Target slot to exclude from search results |
| `UseProjectedDistance` | `false` | Use projected (forward-facing) distance instead of Euclidean |
| `Prioritiser` | *(optional)* | Sort/prioritise matched entities before selecting |
| `Collector` | *(optional)* | Override how matched entities are collected |
| `Filters` | *(optional)* | Array of entity filters (see Entity Filters section) |

### Logic / Composition

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `Any` | Always true | — |
| `And` | All child sensors must match | `Sensors` (required array), `AutoUnlockTargetSlot` |
| `Or` | Any child sensor can match | `Sensors` (required array), `AutoUnlockTargetSlot` |
| `Not` | Negates a wrapped sensor | `Sensor` (required), `UseTargetSlot`, `AutoUnlockTargetSlot` |
| `Switch` | Constant boolean switch | `Switch` (required, computable boolean) |
| `Random` | Alternates true/false windows by random duration | `TrueDurationRange` (required), `FalseDurationRange` (required) |
| `Eval` *(Experimental)* | Evaluate JS expression | `Expression` (required) |
| `Many` | Compound sensor combining multiple sensor configs | *(multiple sensor sub-configs)* |
| `ValueProviderWrapper` | Wraps a sensor and forwards its value outputs to parameter overrides | `Sensor` (required), `ValueToParameterMappings` (required), `PassValues` (default true) |
| `AdjustPosition` | Offsets the position output of a wrapped sensor | `Sensor` (required), `Offset` (required) |

### Entity Detection

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `Player` | Finds matching players | *SensorEntityBase keys* (`Range` required) |
| `Mob` | Finds matching NPCs/entities | *SensorEntityBase keys* + `GetPlayers` (default false), `GetNPCs` (default true), `ExcludeOwnType` (default true) |
| `Self` | Applies entity filters to the NPC itself | `Filters` (required); provides vector position |
| `Target` | Validates locked target in a slot with optional filters/range | `TargetSlot` (default `"LockedTarget"`), `Range`, `AutoUnlockTarget` (default false), `Filters` |
| `Beacon` | Receives broadcast beacon messages from other NPCs | `Message` (required), `Range` (default 64), `TargetSlot`, `ConsumeMessage` (default true), `Rebind` (default false) |
| `Kill` | Matches when NPC killed an entity | `TargetSlot` (default null); provides vector position |
| `Damage` | Matches incoming damage by category; optionally locks attacker target | `Combat` (default true), `Friendly` (default false), `Drowning` (default false), `Environment` (default false), `Other` (default false), `TargetSlot` |
| `Count` | Counts entities in range; matches if count within bounds | `Count` (required range [min,max]), `Range` (required range [min,max]), `IncludeGroups`, `ExcludeGroups` |
| `HasHostileTargetMemory` | Checks if hostile target memory has an entry | — |
| `EntityEvent` | Matches entity event messages by group/type | `Range` (required), `NPCGroup` (required), `EventType` (default DAMAGE: DAMAGE/DEATH/INTERACTION), `TargetSlot`, `SearchType` (default PlayerOnly), `Rebind` (default false), `FlockOnly` (default false) |

### Combat Sensors

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `ChargeState` | Matches BodyMotionCharge phase/state | `States` (required) |
| `ChargeBlockCollisions` | Reports block collisions during a charge | `BlockFilter` (default empty blockset) |
| `ChargeEntityCollisions` | Reports entity collisions during a charge | `GetPlayers` (default true), `GetNPCs` (default true), `ExcludeOwnType` (default false) |

### World / Block / Environment

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `Block` | Scans for matching blocks around NPC; cached; provides position | `Range` (required), `Blocks` (required blockset), `MaxHeight`, `Random` (default false), `Reserve` (default false) |
| `BlockType` | Wraps a position-providing sensor and checks block type at that position | `Sensor` (required), `BlockSet` (required) |
| `BlockChange` | Matches block event stream for a blockset | `BlockSet` (required), `EventType` (default DAMAGE: DAMAGE/DESTRUCTION/INTERACTION) |
| `CanPlaceBlock` | Checks block placeability with retry throttling | `Direction` (default Forward), `Offset` (default BodyPosition), `RetryDelay` (default 5), `AllowEmptyMaterials` (default false) |
| `SearchRay` | Raycasts for blockset at a fixed angle; cached; provides position | `Name` (required), `Angle` (required, degrees -90..90), `Range` (required), `Blocks` (required blockset), `MinRetestAngle` (default 5), `MinRetestMove` (default 1), `ThrottleTime` (default 0.5) |
| `Light` | Matches light channel ranges at NPC position | `LightRange`, `SkyLightRange`, `SunlightRange`, `RedLightRange`, `GreenLightRange`, `BlueLightRange`, `UseTargetSlot` |
| `Path` | Finds nearest/specified path | `Path`, `Range` (default 10), `PathType` (default AnyPrefabPath) |
| `ReadPosition` | Checks distance to stored position or target slot; provides position | `Slot` (required), `Range` (required), `MinRange` (default 0), `UseMarkedTarget` (default false) |
| `SensorAge` | Matches NPC temporal age range | `AgeRange` (required) |
| `Time` | Matches world time period | `Period` (required), `CheckDay` (default true), `CheckYear` (default false), `ScaleDayTimeRange` (default true) |
| `Weather` | Matches weather id/pattern | `Weathers` (required) |
| `Leash` | True when NPC exceeds leash distance | `Range` (required) |

### Items

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `DroppedItem` | Finds dropped items in range; provides position | `Range` (required), `ViewSector` (default 0=360), `LineOfSight` (default false), `Items` (glob patterns), `Attitudes` |

### State Machine

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `State` | Matches current NPC state/substate | `State` (required), `IgnoreMissingSetState` (default false) |
| `IsBusy` | Matches NPC busy state | — |
| `Flag` | Matches named flag boolean value | `Name` (required), `Set` (default true) |
| `Timer` | Matches timer state or time-remaining range | `Name` (required), `State` (default ANY: RUNNING/PAUSED/STOPPED/ANY), `TimeRemainingRange` |
| `Alarm` | Matches alarm set/unset/passed; optional clear on pass | `Name` (required), `State` (required: SET/UNSET/PASSED), `Clear` (default false) |
| `Animation` | Matches current animation slot/id | `Slot` (required), `Animation` (required) |

### Interaction

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `CanInteract` | Matches interactable players in view/attitude set | `ViewSector` (default 0=360), `Attitudes` (default neutral/friendly/revered) |
| `HasInteracted` | True if interaction occurred this tick | — |
| `InteractionContext` | Matches interaction context key | `Context` (required) |

### Movement

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `InAir` | Matches when NPC is airborne | — |
| `OnGround` | Matches when NPC is grounded | — |
| `InWater` | Matches when NPC is in water | — |
| `IsBackingAway` | Matches combat backing-away state | — |
| `Nav` | Matches nav/pathfinder state | `NavStates` (default empty=all states: INIT/PROGRESSING/BLOCKED/DEFER/AT_GOAL/ABORTED), `ThrottleDuration` (default 0), `TargetDelta` (default 0) |
| `MotionController` | Matches active motion controller id | `MotionController` (required) |

---

## Entity Filters

Used in `Filters` arrays on `Player`, `Mob`, `Target`, and `DroppedItem` sensors.

| Filter Type | Description | Key Fields |
|-------------|-------------|------------|
| `Attitude` | Matches relation attitude set | `Attitudes` (required: HOSTILE/FRIENDLY/NEUTRAL/IGNORE/REVERED) |
| `LineOfSight` | Requires unobstructed line of sight | — |
| `HeightDifference` | Matches vertical delta range between NPC and entity | `HeightDifference` (range), `UseEyePosition` (default true) |
| `ViewSector` | Matches entities within a view cone angle | `ViewSector` (default 0=360) |
| `Combat` | Matches combat history/state | `Sequence`, `TimeElapsedRange`, `Mode` (default Attacking: Melee/Ranged/Attacking/Any/None) |
| `ItemInHand` | Matches held item patterns | `Items` (required, glob array), `Hand` (default Both: MainHand/OffHand/Both) |
| `NPCGroup` | Matches NPC group tags | `IncludeGroups`, `ExcludeGroups` |
| `MovementState` | Matches movement state enum | `State` (required: WALKING/RUNNING/CROUCHING/IDLE/JUMPING/FALLING) |
| `SpotsMe` | True if the candidate entity can see this NPC | `ViewAngle` (default 90), `ViewTest` (default VIEW_SECTOR), `TestLineOfSight` (default true) |
| `StandingOnBlock` | Matches block stood on | `BlockSet` (required) |
| `InsideBlock` | Matches if entity is inside blockset | `BlockSet` (required) |
| `Stat` | Compares entity stat ratio/range | `Stat`, `StatTarget`, `RelativeTo`, `RelativeToTarget`, `ValueRange` (required) |
| `Inventory` | Matches inventory composition or free slots | `Items`, `CountRange`, `FreeSlotRange` |
| `Flock` | Matches flock membership/status | `FlockStatus`, `FlockPlayerStatus`, `Size`, `CheckCanJoin` |
| `Altitude` | Matches altitude above ground | `AltitudeRange` (required) |
| `EntityEffect` | Matches active effect present on entity | `EffectId` (required) |
| `IsDead` | Matches dead entities | — |
| `Not` | Negates a wrapped filter | *(wrapped filter config)* |
| `And` | All child filters must match | *(child filter array)* |
| `Or` | Any child filter can match | *(child filter array)* |

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
