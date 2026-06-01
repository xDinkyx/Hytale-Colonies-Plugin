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

### State / Lifecycle

| Action Type | Description | Key Fields |
|------------|-------------|------------|
| `State` | Switch NPC to a different state or sub-state | `State` |
| `ParentState` | Switch using an imported state slot | `State` (from `_ImportStates`) |
| `Role` | **Switch the NPC's entire role** (job type) | `Role` |
| `Spawn` | Spawn a new NPC | — |
| `Die` | Trigger NPC death | — |
| `Despawn` | Despawn this NPC | — |
| `DelayDespawn` | Despawn after a delay | `Delay` |
| `Remove` | Remove entity from world immediately | — |

### Control Flow

| Action Type | Description | Key Fields |
|------------|-------------|------------|
| `Sequence` | Execute multiple actions in the same tick | `Actions` (array) |
| `Random` | Randomly pick a weighted action | `Actions` (array of `{ "Weight": N, "Action": {...} }`) |
| `Test` *(DO NOT USE)* | Internal test action only; not for production roles | — |
| `Timeout` | Wait for a duration | `Delay` (`[min, max]` or fixed) |
| `Nothing` | No-op | — |
| `Log` | Debug log a message | `Message` |
| `SetFlag` | Set a named boolean flag | `Name`, `Value` |

### Timers

| Action Type | Description | Key Fields |
|------------|-------------|------------|
| `TimerStart` | Start a named timer | `Timer`, `Duration` |
| `TimerContinue` | Resume a paused timer | `Timer` |
| `TimerPause` | Pause a running timer | `Timer` |
| `TimerModify` | Change a timer's remaining duration | `Timer`, `Delta` |
| `TimerStop` | Stop and reset a timer | `Timer` |
| `TimerRestart` | Restart a timer from zero | `Timer` |
| `SetAlarm` | Trigger an alarm | — |

### Entity / Communication

| Action Type | Description | Key Fields |
|------------|-------------|------------|
| `Beacon` | Broadcast a message to nearby NPCs | `Message`, `TargetGroups`, `SendTargetSlot`, `Range` |
| `Notify` | Send a beacon-like message to a specific entity | `Message`, `ExpirationTime`, `UseTargetSlot` |
| `SetMarkedTarget` | Mark an entity as the role's locked target | `TargetSlot` |
| `ReleaseTarget` | Release the current marked target | `TargetSlot` |
| `OverrideAttitude` | Override attitude toward another entity | `Attitude`, `Duration` |
| `IgnoreForAvoidance` | Stop avoiding a specific entity in pathfinding | `TargetSlot` |
| `ApplyEntityEffect` | Apply a status effect to the target | `EntityEffect`, `UseTarget` |
| `SetStat` | Set a stat value on the target entity | `Stat`, `Value`, `Add` |
| `AddToHostileTargetMemory` | Add the locked target to hostile target memory | — |
| `Attack` | Execute an attack interaction | `Attack`, `AttackPauseRange` |
| `TriggerSpawners` | Trigger NPC spawners in range | — |
| `OverrideAltitude` | Override desired altitude for flying NPCs | `DesiredAltitudeRange` |
| `ToggleStateEvaluator` | Enable or disable state evaluator | `Enabled` |
| `JoinFlock` | Join or build a flock with the locked target | — |
| `LeaveFlock` | Leave the current flock | — |
| `FlockBeacon` | Send a beacon to all flock members | `Message`, `Range` |
| `FlockState` | Set the state for all flock members | `State` |
| `FlockTarget` | Set the locked target for all flock members | — |
| `StartObjective` | Start an objective for the interacting player | — |
| `CompleteTask` | Mark the NPC's current task as complete (plays animation) | — |
| `Mount` | Enable/disable the player mounting this entity | `AnchorX`, `AnchorY`, `AnchorZ`, `MovementConfig` |
| `OpenBarterShop` | Open a barter shop UI for the current interaction player | `BarterShop` |
| `OpenShop` | Open a shop UI for the current interaction player | `Shop` |

### World Interaction

| Action Type | Description | Key Fields |
|------------|-------------|------------|
| `MakePath` | Pathfind to a sensor-provided location | — |
| `StorePosition` | Save the current sensor position to a named slot | `Slot` |
| `PlaceBlock` | Place a block (requires `SetBlockToPlace` first) | — |
| `SetBlockToPlace` | Configure which block type to place | `Block` |
| `ResetBlockSensors` | Clear block sensor state | — |
| `ResetPath` | Clear pathfinding state | — |
| `ResetSearchRays` | Clear search ray state | — |
| `RecomputePath` | Force pathfinding to recompute the current path | — |
| `SetLeashPosition` | Set the NPC wander leash point | `ToCurrent`, `ToTarget` |

### Items

| Action Type | Description | Key Fields |
|------------|-------------|------------|
| `Inventory` | Add, remove, or equip items | `Operation`, `Item`, `Count`, `Slot`, `UseTarget` |
| `PickUpItem` | Pick up a nearby dropped item | `Range`, `StorageTarget`, `Hoover`, `Items[]` |
| `DropItem` | Drop an item from inventory | — |

### Visual / Audio

| Action Type | Description | Key Fields |
|------------|-------------|------------|
| `PlayAnimation` | Play an animation | `Slot`, `Animation` |
| `PlaySound` | Play a sound to nearby players | `Sound`, `Range` |
| `Appearance` | Change NPC model/appearance | — |
| `DisplayName` | Set or update NPC nameplate text | `Name` |
| `SpawnParticles` | Spawn a particle effect | — |
| `ModelAttachment` | Attach a model to a slot | `Slot` |
| `Crouch` | Instruct NPC to crouch | — |

### Interaction

| Action Type | Description |
|------------|-------------|
| `SetInteractable` | Toggle whether players can interact with the NPC |
| `LockOnInteractionTarget` | Lock target to the player who initiated the interaction |

### Enabled flag on actions

Action elements can be **selectively disabled** using the `Enabled` flag:

```json
{
  "Type": "Attack",
  "Attack": { "Compute": "SpecialAttack" },
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
| `Range` | `1.0` | Pickup radius |
| `StorageTarget` | `Hotbar` | Where to put the item: `Hotbar`, `Inventory`, or `Destroy` |
| `Hoover` | `false` | If `true`, pick up all items in range (no sensor needed) |
| `Items[]` | all | Glob item patterns to filter in hoover mode |

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

## Related Skills

- `hytale-npc-templates` — Core template structure, states, parameters, instruction flags
- `hytale-npc-sensors` — All sensor types, entity filters, detection, block sensors, target slots
- `hytale-npc-combat` — Combat AI patterns, attack interactions, beacons
- `hytale-npc-pathfinding` — Plugin-driven A* navigation via ReadPosition/Seek
- `hytale-npc-components` — Reusable JSON instruction components
