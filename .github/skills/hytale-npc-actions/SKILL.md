---
name: hytale-npc-actions
version: 1
source: https://hytalemodding.com/official-documentation/npc/
authors:
  - name: "HytaleModding"
    url: "https://github.com/HytaleModding"
  - name: "Hypixel Studios"
    url: "https://hytale.com/"
tags: [hytale, npc, actions, motions, timers, alarms, flags]
---

# Hytale NPC Actions & Motions

Full reference for NPC actions, body/head motions, the timer/alarm/flag systems, and the `Random` instruction type.

Load alongside `hytale-npc-templates` when implementing NPC behavior that involves actions, movement, or timed events.

## Triggers

- NPC action
- action type
- Actions
- Timeout
- PlayAnimation
- Inventory action
- PickUpItem
- SetLeashPosition
- MakePath
- StorePosition
- Beacon action
- SetFlag
- TimerStart
- TimerStop
- SetAlarm
- NPC motion
- BodyMotion
- HeadMotion
- Seek motion
- Wander
- WanderInCircle
- WanderInRect
- MaintainDistance
- MotionControllerList
- timer system
- alarm system
- flag system
- Random instruction
- ExecuteFor
- ResetOnStateChange
- leash point
- UseTarget
- Inventory operation
- EquipHotbar
- SetHotbar
- Hoover
- PlaySound
- Appearance action
- DisplayName action
- SpawnParticles

---

## Actions

Actions are operations executed when sensor conditions are met. All names are the exact JSON `"Type"` strings registered in the engine.

### Inherited Fields

Every action inherits:

| Field | Default | Description |
|-------|---------|-------------|
| `Once` | `false` | Execute this action at most once per instruction evaluation cycle |
| `Enabled` | `true` | Enable or disable this action without removing it from the JSON |

Actions that extend `BuilderActionWithDelay` also inherit `Delay` — a `[min, max]` range (default `[1, 1]`).
Actions with `Delay`: **`Timeout`**, **`PickUpItem`**, **`DropItem`**.

---

### Audiovisual

| Action Type | Key Fields | Description |
|------------|-----------|-------------|
| `Appearance` | `Appearance` (required string) | Sets the NPC model appearance |
| `DisplayName` | `DisplayName` (required string) | Sets the NPC nameplate display text |
| `PlayAnimation` | `Slot` (required), `Animation` (optional) | Plays an animation on the given slot |
| `PlaySound` | `SoundEventId` (required) | Plays a sound event to nearby players |
| `SpawnParticles` | `ParticleSystem` (required), `Range`, `Offset`, `TargetNodeName`, `IsDetachedFromModel` (default `false`) | Spawns a particle system visible within range |
| `ModelAttachment` | `Slot` (required), `Attachment` (required) | Sets or clears a model attachment on the given slot |

### Combat

| Action Type | Key Fields | Description |
|------------|-----------|-------------|
| `Attack` | `AttackType`, `ChargeFor`, `AttackPauseRange`, `AimingTimeRange`, `LineOfSight`, `AvoidFriendlyFire` (default `true`), `BallisticMode`, `MeleeConeAngle`, `DamageFriendlies`, `SkipAiming`, `ChargeDistance`, `InteractionVars` | Executes the full NPC attack pipeline |
| `ApplyEntityEffect` | `EntityEffect` (required), `UseTarget` (default `true`) | Applies an entity effect to the target (or self when `UseTarget: false`) |
| `BlockHitInteraction` | `Interaction` (required) | Executes the root interaction for each block hit during a charge |
| `EntityHitInteraction` | `Interaction` (required) | Executes the root interaction for each entity hit during a charge |
| `SetStat` | `Stat` (required), `Value` (required), `Add` (default `false`) | Sets (or adds to) a stat value on the target entity |

### Entity

| Action Type | Key Fields | Description |
|------------|-----------|-------------|
| `Beacon` | `Message` (required), `Range` (default `64`), `TargetGroups` (required), `SendTargetSlot`, `ExpirationTime` (default `-1`), `SendCount` (default `1`) | Broadcasts a beacon message to nearby NPC groups |
| `IgnoreForAvoidance` | `TargetSlot` (required) | Marks the entity in the given target slot to be ignored for avoidance pathfinding |
| `Notify` | `Message` (required), `ExpirationTime` (default `1`), `UseTargetSlot` | Sends a notify message to the entity in the target slot |
| `OverrideAttitude` | `Attitude` (required), `Duration` (default `10`) | Temporarily overrides the NPC attitude for a duration |
| `ReleaseTarget` | `TargetSlot` (default slot) | Clears the marked target slot |
| `SetMarkedTarget` | `TargetSlot` (default slot), `Rebind` (default `false`) | Stores the sensor target into the marked target slot |

Supplemental entity actions registered by other modules:

| Action Type | Key Fields | Description |
|------------|-----------|-------------|
| `AddToHostileTargetMemory` | — | Adds the locked target to hostile target memory |
| `JoinFlock` | — | Joins or builds a flock with the locked target |
| `LeaveFlock` | — | Leaves the current flock |
| `FlockBeacon` | `Message`, `Range` | Sends a beacon to all flock members |
| `FlockState` | `State` | Sets the state for all flock members |
| `FlockTarget` | — | Sets the locked target for all flock members |
| `StartObjective` | — | Starts an objective for the interacting player |
| `CompleteTask` | — | Marks the NPC's current task as complete (plays animation) |
| `Mount` | `AnchorX`, `AnchorY`, `AnchorZ`, `MovementConfig` | Enables/disables player mounting on this entity |
| `OpenBarterShop` | `BarterShop` | Opens a barter shop UI for the current interaction player |
| `OpenShop` | `Shop` | Opens a shop UI for the current interaction player |

### Interaction

| Action Type | Key Fields | Description |
|------------|-----------|-------------|
| `LockOnInteractionTarget` | `TargetSlot` (default slot), `Rebind` (default `false`) | Saves the current interaction target entity to the marked target slot |
| `SetInteractable` | `Interactable` (default `true`), `Hint`, `ShowPrompt` (default `true`) | Sets per-player interactability, hint text, and prompt visibility |

### Items

| Action Type | Key Fields | Description |
|------------|-----------|-------------|
| `DropItem` | `Delay`, `Item`, `DropList`, `ThrowSpeed` (default `1`), `Distance`, `DropSector`, `PitchHigh` (default `false`) | Drops a specific item or a drop list with throw parameters |
| `Inventory` | `Operation` (default `Add`), `Count` (default `1`), `Item`, `UseTarget` (default `true`), `Slot` (default `0`) | Adds, removes, or equips inventory items |
| `PickUpItem` | `Delay`, `Range` (default `1`), `StorageTarget` (default `Hotbar`), `Hoover` (default `false`), `Items` | Picks up nearby dropped items; optional item filter and hoover mode |

### Lifecycle

| Action Type | Key Fields | Description |
|------------|-----------|-------------|
| `Spawn` | `Kind` (required), `SpawnDirection`, `SpawnAngle` (default `360`), `FanOut` (default `false`), `DistanceRange`, `CountRange`, `DelayRange`, `Flock`, `LaunchAtTarget`, `PitchHigh`, `LaunchSpread`, `JoinFlock` (default `false`), `SpawnState`, `SpawnSubState` | Spawns one or more instances of a role |
| `Despawn` | `Force` (default `false`) | Requests the NPC despawn/removal flow |
| `DelayDespawn` | `Time` (required), `Shorten` (default `false`) | Sets a timed despawn delay |
| `Die` | (none) | Triggers the NPC death flow |
| `Remove` | `UseTarget` (default `true`) | Removes the target entity (or self) from the world immediately |
| `Role` | `Role` (required), `ChangeAppearance` (default `true`), `State`, `DetachFromSpawning` (default `false`) | Changes the NPC's active role |

### Movement

| Action Type | Key Fields | Description |
|------------|-----------|-------------|
| `ActionCrouch` | `Crouch` (default `true`) | Sets the NPC crouch movement state |
| `ActionOverrideAltitude` | `DesiredAltitudeRange` (required) | Overrides the desired altitude range for flying/airborne movement |
| `ActionRecomputePath` | (none) | Forces path recomputation on the motion controller |

### State Machine

| Action Type | Key Fields | Description |
|------------|-----------|-------------|
| `State` | `State` (required), `ClearState` (default `true`), `ClearHeadMotion` (default `true`), `ClearBodyMotion` (default `true`) | Switches the NPC to a new main/sub state, optionally clearing motions |
| `ParentState` | `State` (required, import alias from `_ImportStates`), `ClearHeadMotion` (default `true`), `ClearBodyMotion` (default `true`) | Switches using an imported parent state alias |
| `ToggleStateEvaluator` | `On` (required boolean) | Enables or disables the state evaluator |

### Timer & Alarm

| Action Type | Key Fields | Description |
|------------|-----------|-------------|
| `TimerStart` | `Name` (required), `StartValueRange` (required range), `RestartValueRange` (required range), `Rate` (default `1`), `Repeating` (default `false`) | Creates and starts a named countdown timer |
| `TimerContinue` | `Name` (required) | Resumes a paused timer |
| `TimerPause` | `Name` (required) | Pauses an active timer |
| `TimerModify` | `Name` (required), `AddValue` (default `0`), `MaxValue` (range), `Rate` (default `0`), `SetValue` (default `0`), `Repeating` (default `false`) | Mutates active timer fields in-place |
| `TimerStop` | `Name` (required) | Stops and resets a timer |
| `TimerRestart` | `Name` (required) | Restarts an already-initialized timer |
| `SetAlarm` | `Name` (required), `DurationRange` (required — `["P0D","P0D"]` unsets) | Sets or unsets a named alarm timestamp |

### Utility

| Action Type | Key Fields | Description |
|------------|-----------|-------------|
| `Nothing` | (none) | No-op; always returns true |
| `Random` | `Actions` (required, weighted list) | Picks a weighted random action to execute |
| `ResetInstructions` | `Instructions` (list, empty = all) | Resets specified instruction slot indices (or all if empty) |
| `Sequence` | `Blocking` (default `false`), `Atomic` (default `false`), `Actions` (required) | Runs an ordered list of actions |
| `SetFlag` | `Name` (required), `SetTo` (default `true`) | Sets a named flag slot to a boolean value |
| `Timeout` | `Delay`, `DelayAfter` (default `false`), `Action` (optional wrapped action) | Delays execution; optionally wraps another action |

### World

| Action Type | Key Fields | Description |
|------------|-----------|-------------|
| `MakePath` | `Path` (required) | Creates a transient path definition from a path asset |
| `PlaceBlock` | `Range` (default `3`), `AllowEmptyMaterials` (default `false`) | Places the selected block at the sensor target position |
| `RecomputePath` | (none) | Forces path recomputation |
| `ResetBlockSensors` | `BlockSets` (list, empty = all) | Clears block sensor reservations and cache |
| `ResetPath` | (none) | Clears the active path state |
| `ResetSearchRays` | `Names` (list, empty = all) | Clears cached search-ray results |
| `SetBlockToPlace` | `Block` (required) | Sets the block type item to use for the next `PlaceBlock` |
| `SetLeashPosition` | `ToCurrent` (default `false`), `ToTarget` (default `false`) | Updates the NPC wander leash point |
| `StorePosition` | `Slot` (required) | Stores the current sensor position in the given world support slot |
| `TriggerSpawners` | `SpawnMarker`, `Range` (required), `Count` (default `0`), `TargetSlot`, `Rebind` (default `false`) | Triggers nearby manual spawn markers |

### Debug (internal use only)

| Action Type | Key Fields | Description |
|------------|-----------|-------------|
| `Log` | `Message` (required) | Logs a message to the server console |
| `Test` | (various) | Internal test action; do not use in production roles |

### Enabled flag on actions

Action elements can be **selectively disabled** using the `Enabled` flag:

```json
{
  "Type": "Attack",
  "AttackType": { "Compute": "SpecialAttack" },
  "Enabled": false
}
```

### Beacon Range from Template Variables

```json
{
  "Type": "Beacon",
  "Message": "Alert_Allies",
  "TargetGroups": { "Compute": "WarnGroups" },
  "Range": { "Compute": "AlertBeaconRange" }
}
```

### Random Action (Weighted State Selection)

```json
{
  "Actions": [
    {
      "Type": "Random",
      "Actions": [
        { "Weight": 70, "Action": { "Type": "State", "State": ".Guard" } },
        { "Weight": 20, "Action": { "Type": "State", "State": "Sleep" } },
        { "Weight": 10, "Action": { "Type": "State", "State": "Eat" } }
      ]
    }
  ]
}
```

### Timeout with State Switch

```json
{
  "Continue": true,
  "ActionsBlocking": true,
  "Actions": [
    { "Type": "Timeout", "Delay": [15, 30] },
    { "Type": "State", "State": ".Default" }
  ]
}
```

### Inventory Action

Full set of `Inventory` operations (confirmed from decompiled source):

| Operation | Description |
|-----------|-------------|
| `Add` | Add items to the NPC's inventory |
| `Remove` | Remove items from the NPC's inventory |
| `Equip` | Equip item as weapon or armour |
| `ClearHeldItem` | Clear the currently held item |
| `RemoveHeldItem` | Destroy the currently held item |
| `SetHotbar` | Place an item in a specific hotbar slot |
| `EquipHotbar` | Place item in hotbar slot AND activate that slot |
| `SetOffHand` | Place an item in a specific off-hand slot |
| `EquipOffHand` | Place item in off-hand slot AND activate it |

> **`UseTarget: false`** is required to act on the NPC itself, not its sensor target.

```json
{
  "Type": "Inventory",
  "Operation": "EquipHotbar",
  "Item": { "Compute": "WeaponItem" },
  "Slot": 0,
  "UseTarget": false
}
```

### PickUpItem Action

Two modes:
- **Sensor-driven (default)**: requires a `DroppedItem` sensor to provide the item target.
- **Hoover mode** (`Hoover: true`): sucks up all items in range; no sensor needed.

| Field | Default | Description |
|---|---|---|
| `Delay` | `[1, 1]` | Inherited from `BuilderActionWithDelay` — delay range before executing |
| `Range` | `1.0` | Pickup radius |
| `StorageTarget` | `Hotbar` | Where to put the item: `Hotbar`, `Inventory`, or `Destroy` |
| `Hoover` | `false` | If `true`, pick up all items in range (no sensor needed) |
| `Items` | all | Glob item patterns to filter in hoover mode |

```json
{
  "Type": "PickUpItem",
  "Hoover": true,
  "Range": 3.0,
  "StorageTarget": "Inventory",
  "Items": ["Hyforged:*Wood*", "Hyforged:*Log*"]
}
```

---

## Motions

Motions control NPC movement. Set via `BodyMotion` or `HeadMotion` on an instruction.

### Body Motion

| Motion Type | Description | Key Fields |
|------------|-------------|------------|
| `Nothing` | Stand still | — |
| `Seek` | Move toward target/position using A* | `SlowDownDistance`, `StopDistance`, `AbortDistance` (default 96), `RelativeSpeed`, `UsePathfinder`; constraint: `SlowDownDistance >= StopDistance` |
| `Flee` | Move away from target | `SlowDownDistance`, `StopDistance`, `HoldDirectionTimeRange` |
| `Wander` | Unconstrained random wandering | `MaxHeadingChange`, `RelativeSpeed`, `MinWalkTime`, `MaxWalkTime` |
| `WanderInCircle` | Circular wandering constrained to the NPC's **leash point** | `Radius` (default 10), `MaxHeadingChange`, `RelativeSpeed` |
| `WanderInRect` | Rectangular wandering constrained to the NPC's **spawn/leash position** | `Width` (default 10), `Depth` (default 10), `RelativeSpeed` |
| `MaintainDistance` | Keep a specified distance range from target | `DesiredDistanceRange`, `StrafingDurationRange` |
| `Sequence` | Chain motions in order | `Motions` (array), `Looped` |
| `Timer` | Run a motion for a capped duration | `Time` (range), `Motion` |
| `Path` | Walk along a named path marker | `Shape` (LOOP/LINE/CHAIN/POINTS), `Direction`, `MinNodeDelay`, `MaxNodeDelay` |
| `Teleport` | Teleport NPC to sensor-provided position | `OffsetRange`, `Orientation` |
| `Leave` | Exit the current area | — |
| `TakeOff` | Begin flying/airborne movement | — |
| `Land` | Descend and land | — |
| `MatchLook` | Match look direction to a target | — |
| `AimCharge` | Charge up an aimed attack (combat) | — |

### Head Motion

| Motion Type | Description | Key Fields |
|------------|-------------|------------|
| `Watch` | Track a sensor target | `RelativeTurnSpeed` |
| `Observe` | Sweep/pan head across an angle range | `AngleRange`, `PauseTimeRange`, `PickRandomAngle` |
| `Aim` | Aim at a target (combat) | `RelativeTurnSpeed` |
| `Sequence` | Chain head motions in order | `Motions` (array) |
| `Timer` | Run a head motion for a capped duration | `Time` (range), `Motion` |
| `Nothing` | Hold head still | — |

### MotionControllerList

Defined at template level:

```json
"MotionControllerList": [
  {
    "Type": "Walk",
    "MaxWalkSpeed": 3,
    "Gravity": 10,
    "MaxFallSpeed": 8,
    "Acceleration": 10
  }
]
```

### Head Motion (Watch Target)

```json
{
  "Continue": true,
  "Sensor": { "Type": "Target", "Range": { "Compute": "AlertedRange" } },
  "HeadMotion": { "Type": "Watch" }
}
```

### Body Motion with Pathfinding

```json
{
  "Sensor": { "Type": "Leash", "Range": { "Compute": "LeashDistance * 0.3" } },
  "BodyMotion": {
    "Type": "Seek",
    "SlowDownDistance": { "Compute": "LeashDistance * 0.4" },
    "StopDistance": { "Compute": "LeashDistance * 0.2" },
    "RelativeSpeed": 0.8,
    "UsePathfinder": true
  }
}
```

### WanderInCircle — leash point

`WanderInCircle` constrains movement to a circle centred on `NPCEntity.getLeashPoint()`. The leash point defaults to the NPC's spawn position. To wander around a workstation or harvested block, set the leash point first:

```java
NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
if (npc != null) npc.getLeashPoint().assign(targetPosition);
```

```json
// Set leash to current NPC position via JSON:
{ "Type": "SetLeashPosition", "ToCurrent": true }
```

### Search Wander Pattern

```json
"BodyMotion": {
  "Type": "Sequence",
  "Motions": [
    {
      "Type": "Timer",
      "Time": [3, 6],
      "Motion": { "Type": "Wander", "MaxHeadingChange": 1, "RelativeSpeed": 0.5 }
    },
    {
      "Type": "Sequence",
      "Looped": true,
      "Motions": [
        {
          "Type": "Timer",
          "Time": [3, 6],
          "Motion": { "Type": "WanderInCircle", "Radius": 10, "MaxHeadingChange": 60, "RelativeSpeed": 0.5 }
        },
        {
          "Type": "Timer",
          "Time": [2, 3],
          "Motion": { "Type": "Nothing" }
        }
      ]
    }
  ]
}
```

---

## Timer System

Named, countdown timers. Each has a current value that decrements at a configurable `Rate`. Stop/pause/restart via actions. Query state and remaining time via `Timer: Sensor`.

### Starting and managing a timer

```json
// Start a 5-10 second cooldown timer named "AttackCooldown":
{ "Type": "TimerStart", "Name": "AttackCooldown", "StartValueRange": [5, 10], "Rate": 1.0 }

// Stop it:
{ "Type": "TimerStop", "Name": "AttackCooldown" }

// Pause / continue:
{ "Type": "TimerPause", "Name": "AttackCooldown" }
{ "Type": "TimerContinue", "Name": "AttackCooldown" }

// Restart to original values:
{ "Type": "TimerRestart", "Name": "AttackCooldown" }

// Modify (add time, change rate, set repeating):
{ "Type": "TimerModify", "Name": "AttackCooldown", "AddValue": 3.0, "Repeating": true }
```

### Querying a timer

```json
{
  "Sensor": {
    "Type": "Timer",
    "Name": "AttackCooldown",
    "State": "STOPPED",
    "TimeRemainingRange": [0, 999]
  },
  "Actions": [ { "Type": "Attack", "Attack": { "Compute": "Attack" } } ]
}
```

| `State` flag | Meaning |
|---|---|
| `RUNNING` | Timer is ticking |
| `PAUSED` | Timer is paused |
| `STOPPED` | Timer has expired or was stopped |
| `ANY` | Any state |

---

## Alarm System

Lighter-weight alternative to timers for simple one-shot delays.

```json
// Set an alarm to fire in 3-5 seconds:
{ "Type": "SetAlarm", "Name": "CooldownAlarm", "DurationRange": ["PT3S", "PT5S"] }

// Check if it has passed (and auto-clear it):
{
  "Sensor": { "Type": "Alarm", "Name": "CooldownAlarm", "State": "PASSED", "Clear": true },
  "Actions": [ ... ]
}
```

Duration uses ISO-8601 duration strings: `"PT5S"` = 5 seconds, `"PT1M30S"` = 90 seconds.

| `State` flag | Meaning |
|---|---|
| `SET` | Alarm is active, hasn't passed yet |
| `UNSET` | Alarm was never set or was cleared |
| `PASSED` | Alarm time has elapsed |

---

## Flag System

Named boolean flags that persist on the NPC within a session.

```json
// Set a flag:
{ "Type": "SetFlag", "Name": "HasSpokenToPlayer", "SetTo": true }

// Clear it:
{ "Type": "SetFlag", "Name": "HasSpokenToPlayer", "SetTo": false }

// Test it:
{
  "Sensor": { "Type": "Flag", "Name": "HasSpokenToPlayer", "Set": true },
  "Actions": [ ... ]
}
```

---

## Random: Instruction (source-verified)

`Random: Instruction` picks a weighted random child instruction and executes it. It does **not** re-pick every tick; it keeps the same choice for `ExecuteFor` seconds, then re-rolls.

| Field | Default | Description |
|-------|---------|-------------|
| `ExecuteFor` | `[inf, inf]` | `[min, max]` seconds to keep the same random choice before picking a new one |
| `ResetOnStateChange` | `true` | Whether to re-pick when the NPC state changes |

The selected child's sensor is still checked every tick — if it fails the engine runs nothing for that tick (does NOT fall back to another child).

```json
{
  "Type": "Random",
  "ExecuteFor": [5, 10],
  "Instructions": [
    { "Weight": 3, "BodyMotion": { "Type": "WanderInCircle", "Radius": 8 } },
    { "Weight": 1, "BodyMotion": { "Type": "Nothing" } }
  ]
}
```

---

## Additional Sensor Types (from official source)

The following sensor types exist in the official source but are documented in `hytale-npc-sensors`:

| Sensor Type | Notes |
|---|---|
| `AdjustPosition` | Adjusts NPC position |
| `ChargeState` | Detects charge state |
| `ChargeBlockCollisions` | Detects block collisions during a charge |
| `ChargeEntityCollisions` | Detects entity collisions during a charge |
| `IsBackingAway` | True when NPC is actively backing away from target |

---

## Related Skills

- `hytale-npc-templates` — Core template structure, states, parameters, instruction flags
- `hytale-npc-sensors` — All sensor types, entity filters, detection, block sensors, target slots
- `hytale-npc-combat` — Combat AI patterns, attack interactions, beacons
- `hytale-npc-pathfinding` — Plugin-driven A* navigation via ReadPosition/Seek
- `hytale-npc-components` — Reusable JSON instruction components
