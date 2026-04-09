---
name: hytale-npc-templates
version: 3
source: https://hytalemodding.com/official-documentation/npc/
authors:
  - name: "HytaleModding"
    url: "https://github.com/HytaleModding"
  - name: "Hypixel Studios"
    url: "https://hytale.com/"
references:
  - name: "NPC Template Reference"
    url: "references/npc-template-reference.md"
tags: [hytale, npc, behavior, templates, ai, json, pathfinding, navigation]
---

# Hytale NPC Templates

Documents Hytale's JSON-based NPC template and behavior system for defining NPC AI via data-driven templates. Covers template structure, variants, states, substates, sensors, actions, motions, state transitions, components, detection (sight/hearing), combat (melee attacks, chaining), inter-NPC interaction (beacons), leashing, searching, and reusable instruction components. Also covers plugin-driven A* pathfinding via stored position slots.

Use when creating NPC behavior, defining NPC templates, adding NPC states, configuring NPC detection/combat, building reusable NPC components, or making NPCs navigate to plugin-specified positions.

## Triggers

- NPC template
- NPC behavior
- NPC state
- NPC sensor
- NPC action
- NPC motion
- state transition
- NPC combat
- NPC detection
- NPC component
- Template_
- Variant
- BlankTemplate
- Instructions
- StartState
- Random action
- Timeout
- PlayAnimation
- StateTransitions
- Component_Instruction
- Component_Sensor
- Intelligent_Chase
- Soft_Leash
- Standard_Detection
- Damage_Check
- beacon
- NPC group
- AttitudeGroup
- DefaultPlayerAttitude
- InteractionVars
- melee attack
- attack chaining
- Root Interaction
- pathfinding
- NPC navigation
- ReadPosition
- StoredPosition
- NavTarget
- navigate NPC
- plugin pathfinding
- DeathParticles
- DropDeathItemsInstantly
- DeathAnimationTime
- VisPath
- npc descriptors
- NPC debug
- conditional effect filter
- Buffed
- Debuffed
- Enabled flag
- separation mode


### Variant with No Overrides

A Variant that inherits all defaults from an Abstract template needs no `Modify` block:

```json
{
  "Type": "Variant",
  "Reference": "Template_Colonist_Base"
}
```

---

## Minimal Working NPC (Generic)

The safest base for a custom plugin NPC. Mirrors `Empty_Role.json` from `lib/Server/NPC/Roles/`.

```json
{
  "Type": "Generic",
  "Appearance": "Mannequin",
  "MaxHealth": { "Compute": "MaxHealth" },
  "Parameters": {
    "MaxHealth": {
      "Value": 20,
      "Description": "Max health for the NPC"
    }
  },
  "MotionControllerList": [
    { "Type": "Walk" }
  ],
  "Instructions": [
    { }
  ],
  "NameTranslationKey": "server.npcRoles.My_NPC.name"
}
```

---

## Plugin-Driven A* Pathfinding (ReadPosition + Seek)

The correct way to make a plugin navigate an NPC to a position with full A* obstacle avoidance.

### How it works

Hytale's A* system (`BodyMotionFind`, JSON type `"Seek"`) is driven by sensors. The `ReadPosition` sensor reads a **named stored position slot** from `role.getMarkedEntitySupport()`. When the plugin writes a position to that slot, the sensor activates and `Seek` runs A* pathfinding every tick. The NPC stops once within `MinRange`. The sensor is inactive naturally when the slot is at its default `(0,0,0)` and the NPC is far away.

> **Do NOT use `PathManager.setTransientPath()`** for obstacle-aware navigation. That drives scripted waypoint paths (straight lines), not A*.

### JSON (flat ReadPosition + Seek instruction)

```json
"Instructions": [
  {
    "Instructions": [
      {
        "Sensor": {
          "Type": "ReadPosition",
          "Slot": "NavTarget",
          "Range": 200.0,
          "MinRange": 1.5
        },
        "BodyMotion": {
          "Type": "Seek",
          "StopDistance": 1.5,
          "SlowDownDistance": 3.0,
          "RelativeSpeed": 1.0
        }
      }
    ]
  }
]
```

| `ReadPosition` field | Description |
|---|---|
| `Slot` | Named position slot — auto-allocated by name; index 0 = first slot declared in the role |
| `Range` | Max distance from stored position to match |
| `MinRange` | Arrival condition — sensor deactivates when NPC is this close |
| `UseMarkedTarget` | If `true`, reads entity position from a `LockedTargetSlot` instead |

### Java — write position to trigger navigation

```java
NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
Role role = npcEntity.getRole();

// Slot index 0 = first ReadPosition slot declared in the role JSON
role.getMarkedEntitySupport().getStoredPosition(0).assign(targetPosition);
```

### RefChangeSystem trigger pattern

Add a `MoveToTargetComponent` whenever you want the NPC to navigate. A `RefChangeSystem` fires immediately, writes the slot, then removes the component.

```java
public class PathFindingSystem extends RefChangeSystem<EntityStore, MoveToTargetComponent> {

    private static final int NAV_TARGET_SLOT = 0;

    @Override
    public ComponentType<EntityStore, MoveToTargetComponent> componentType() {
        return MoveToTargetComponent.getComponentType();
    }

    @Override
    public void onComponentAdded(Ref<EntityStore> ref, MoveToTargetComponent component,
            Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        commandBuffer.removeComponent(ref, MoveToTargetComponent.getComponentType());
        NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
        if (npcEntity == null) return;
        Role role = npcEntity.getRole();
        if (role == null) return;
        role.getMarkedEntitySupport().getStoredPosition(NAV_TARGET_SLOT).assign(component.target);
    }

    @Override
    public void onComponentSet(Ref<EntityStore> ref, MoveToTargetComponent old,
            MoveToTargetComponent updated, Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer) {
        onComponentAdded(ref, updated, store, commandBuffer);
    }

    @Override
    public void onComponentRemoved(Ref<EntityStore> ref, MoveToTargetComponent component,
            Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {}

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(MoveToTargetComponent.getComponentType());
    }
}
```

Trigger navigation from anywhere:

```java
store.addComponent(npcRef, MoveToTargetComponent.getComponentType(), new MoveToTargetComponent(targetPos));
```

---

## Parameters

Parameters are defined in the `"Parameters"` block and referenced via `{ "Compute": "ParamName" }`. They support computed expressions like `"Compute": "ViewRange / DistractedPenalty"`.

```json
"Parameters": {
  "Appearance": {
    "Value": "Bear_Grizzly",
    "Description": "Model to be used"
  },
  "ViewRange": {
    "Value": 15,
    "Description": "View range in blocks"
  },
  "DistractedPenalty": {
    "Value": 2,
    "Description": "Factor by which view/hearing range is divided when distracted"
  }
}
```

**Computed expressions:** `{ "Compute": "ViewRange / DistractedPenalty" }` divides ViewRange by DistractedPenalty at runtime.

---

## States & Substates

### Setting the Start State

```json
"StartState": "Idle",
```

### Top-Level States

Top-level states are behavioral modes like `Idle`, `Sleep`, `Eat`, `Combat`, `Alerted`, `ReturnHome`, `Search`.

```json
"Instructions": [
  {
    "Sensor": { "Type": "State", "State": "Idle" },
    "Instructions": [ ... ]
  },
  {
    "Sensor": { "Type": "State", "State": "Sleep" },
    "Instructions": [ ... ]
  },
  {
    "Sensor": { "Type": "State", "State": "Combat" },
    "Instructions": [ ... ]
  }
]
```

### Substates

Substates are nested within a parent state and prefixed with `.`. The `.Default` substate is used automatically when entering the parent state.

```json
{
  "Sensor": { "Type": "State", "State": "Idle" },
  "Instructions": [
    {
      "Sensor": { "Type": "State", "State": ".Default" },
      "Instructions": [ ... ]
    },
    {
      "Sensor": { "Type": "State", "State": ".Guard" },
      "Instructions": [ ... ]
    }
  ]
}
```

### Switching States

Use a `State` action to switch:

```json
{ "Type": "State", "State": "Combat" }
```

For substates:

```json
{ "Type": "State", "State": ".Guard" }
```

---

## Sensors

Sensors are conditions that gate instruction execution.

| Sensor Type | Description | Key Fields |
|------------|-------------|------------|
| `State` | Matches current NPC state | `State` |
| `Any` | Always matches | `Once` (execute only once) |
| `Target` | Detects locked/nearby target | `Range`, `TargetSlot`, `Filters` |
| `Mob` | Detects nearby NPCs | `Range`, `Filters` ([`NPCGroup`, `LineOfSight`]) |
| `Beacon` | Listens for inter-NPC messages | `Message`, `Range`, `TargetSlot` |
| `Damage` | Reacts to incoming damage | `Combat`, `TargetSlot` |
| `Leash` | Checks distance from spawn | `Range` |
| `And` | Combines multiple sensors | `Sensors` (array) |
| `Reference` | Uses a reusable sensor component | Component name |

### Target Filters (including Update 4 additions)

| Filter Type | Description |
|-------------|-------------|
| `LineOfSight` | Requires unobstructed line of sight |
| `NPCGroup` | Filters by NPC group membership |
| `Buffed` | Target currently has an active buff (Update 4) |
| `Debuffed` | Target currently has an active debuff (Update 4) |

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

---

## Actions

Actions are operations executed when sensor conditions are met.

| Action Type | Description | Key Fields |
|------------|-------------|------------|
| `State` | Switch to a different state | `State` |
| `ParentState` | Switch using imported state name | `State` (from `_ImportStates`) |
| `Random` | Randomly pick a weighted action | `Actions` (array with `Weight` + `Action`) |
| `Timeout` | Wait for a duration | `Delay` (`[min, max]` or fixed) |
| `PlayAnimation` | Play an animation | `Slot`, `Animation` |
| `Attack` | Execute an attack interaction | `Attack`, `AttackPauseRange` |
| `Inventory` | Manipulate NPC inventory | `Operation`, `Item`, `Slot`, `UseTarget` |
| `Beacon` | Send message to nearby NPCs | `Message`, `TargetGroups`, `SendTargetSlot`, `Range` (supports template variables) |
| `TriggerSpawnBeacon` | Trigger a manual spawn beacon | `BeaconSpawn`, `Range` |
| `SetStat` | Set an entity stat | `Stat`, `Value` |
| `Remove` | Remove the target entity | — |
| `Despawn` | Despawn this NPC | — |
| `Sequence` | Execute multiple actions in same tick | `Actions` (array) |

Action elements can be **selectively disabled** using the `Enabled` flag. Useful for disabling actions conditionally without removing them from the template:
>
> ```json
> {
>   "Type": "Attack",
>   "Attack": { "Compute": "SpecialAttack" },
>   "Enabled": false
> }
> ```

### Beacon Range from Template Variables

The `Beacon` action's `Range` field can be computed from template variables:

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

### Inventory Actions

| Operation | Description |
|-----------|-------------|
| `SetHotbar` | Place an item in a hotbar slot |
| `EquipHotbar` | Switch active hotbar slot |

```json
{
  "Type": "Inventory",
  "Operation": "SetHotbar",
  "Item": { "Compute": "EatItem" },
  "Slot": 2,
  "UseTarget": false
}
```

> **`UseTarget: false`** is required to act on the NPC itself, not its target.

---

## Instruction Flags

| Flag | Default | Description |
|------|---------|-------------|
| `Continue` | `false` | If `true`, continue evaluating subsequent siblings even after this one matches |
| `ActionsBlocking` | `false` | Actions execute one at a time in sequence; each must complete before the next starts |
| `ActionsAtomic` | `false` | Only execute actions if ALL actions can currently execute; if any one fails, none run |
| `TreeMode` | `false` | Behavior-tree selector mode: keep continuing to siblings unless a child matches (see below) |
| `InvertTreeModeResult` | `false` | Invert the success/failure result propagated to a parent `TreeMode` instruction |
| `Once` | `false` | (On sensors) Sensor fires only once; resets when the state is cleared |
| `Enabled` | `true` | Computable — can conditionally disable an entire instruction |

### Evaluation rules (source-verified)

The engine iterates siblings and for each matching instruction calls `execute()`; only if `isContinueAfter()` returns `false` does it break out of the loop.

- A node with **no sensor always matches** (implicit `Any`).
- Without `Continue: true`, the first matching node **stops all sibling evaluation**.
- `Continue: true` means: "I matched — AND keep evaluating the next sibling too".
- You **cannot** have both `BodyMotion` and `Instructions` on the same instruction (enforced by a validation constraint). Leaf instructions carry motion/actions; nested instruction lists carry children only.
- Only **one** `BodyMotion` and one `HeadMotion` are active per tick (the last `setNextBodyMotionStep`/`setNextHeadMotionStep` call wins). Two `Continue: true` siblings setting `BodyMotion` will have the second one override the first.

**Consequence for state-gated blocks**: if each ECS state is wrapped in a sensorless outer `{ "Instructions": [...] }`, the first wrapper always matches and stops the loop. The correct pattern is `Continue: true` + sensor placed directly on each instruction:

```json
{ "Continue": true, "Sensor": { "Type": "EcsJobState", "JobState": "StateA" }, "Instructions": [ ... ] },
{ "Continue": true, "Sensor": { "Type": "EcsJobState", "JobState": "StateB" }, "Instructions": [ ... ] }
```

### ActionsBlocking — sequential action execution (source-verified)

`ActionsBlocking: true` runs actions as a **one-at-a-time sequence**. The engine tracks the current index, advances only when the current action "completes" (returns true from `execute()`), and runs nothing if the current action reports `canExecute() = false`. This is the correct way to chain: Timeout → State switch.

```json
{
  "Continue": true,
  "ActionsBlocking": true,
  "Actions": [
    { "Type": "Timeout", "Delay": [5, 10] },
    { "Type": "State", "State": "Idle" }
  ]
}
```

### TreeMode — behavior-tree selector semantics (source-verified)

When `TreeMode: true` is set on an instruction:
1. When it is matched, `continueAfter` is **forced to `true`** internally (it always yields to its next sibling).
2. If **any child** instruction's sensor matches, `continueAfter` is **set back to `false`** (it stops yielding; this instruction "succeeded").
3. Net result: **"keep going past this instruction unless at least one child succeeded."**

This maps to a behavior-tree **Selector (fallback) node**: keep trying siblings until something works. Constraint: `If TreeMode is true, Continue must be false` (the engine manages `continueAfter` dynamically).

```json
{
  "TreeMode": true,
  "Instructions": [
    {
      "Sensor": { "Type": "Target", "Range": 5 },
      "Actions": [ { "Type": "State", "State": "Combat" } ]
    }
  ]
}
```

`InvertTreeModeResult` flips the success/failure signal propagated to a parent `TreeMode` node — used for behavior-tree Decorator patterns.

---

## Motions

Motions control NPC movement. Set via `BodyMotion` or `HeadMotion` on instructions.

| Motion Type | Description | Key Fields |
|------------|-------------|------------|
| `Nothing` | Stand still | — |
| `Seek` | Move toward target/position | `SlowDownDistance`, `StopDistance`, `AbortDistance` (default 96), `RelativeSpeed`, `UsePathfinder`; constraint: `SlowDownDistance >= StopDistance` |
| `Flee` | Move away from target | `SlowDownDistance`, `StopDistance`, `HoldDirectionTimeRange` |
| `Wander` | Unconstrained random wandering | `MaxHeadingChange`, `RelativeSpeed`, `MinWalkTime`, `MaxWalkTime` |
| `WanderInCircle` | Circular wandering constrained to the NPC's **leash point** | `Radius` (default 10), `MaxHeadingChange`, `RelativeSpeed` |
| `WanderInRect` | Rectangular wandering constrained to the NPC's **spawn/leash position** | `Width` (default 10), `Depth` (default 10), `RelativeSpeed` |
| `MaintainDistance` | Keep a specified distance range from target | `DesiredDistanceRange`, `StrafingDurationRange` |
| `Watch` | Look at target (head only) | `RelativeTurnSpeed` |
| `Observe` | Sweep/pan head across an angle range | `AngleRange`, `PauseTimeRange`, `PickRandomAngle` |
| `Aim` | Aim at target (combat, head) | `RelativeTurnSpeed` |
| `Sequence` | Chain motions in order | `Motions` (array), `Looped` |
| `Timer` | Run a motion for a capped duration | `Time` (range), `Motion` |
| `Path` | Walk along a named path marker | `Shape` (LOOP/LINE/CHAIN/POINTS), `Direction`, `MinNodeDelay`, `MaxNodeDelay` |
| `Teleport` | Teleport NPC to sensor-provided position | `OffsetRange`, `Orientation` |

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

`WanderInCircle` does **not** wander around the NPC's current position — it constrains movement to a circle centred on `NPCEntity.getLeashPoint()`. The leash point defaults to the NPC's spawn position. To make wander stay around a different location (e.g. a workstation or a harvested block), set the leash point from ECS via `NPCEntity.getLeashPoint().assign(position)` before entering the state, or use the `SetLeashPosition` action (`ToCurrent: true` sets it to the NPC's current position; `ToTarget: true` sets it to the locked target entity's position).

```java
// From ECS Java code:
NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
if (npc != null) npc.getLeashPoint().assign(targetPosition);
```

```json
// From JSON (set leash to current NPC position):
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

## State Sensor Validator (XOR Rule)

The NPC role loader runs a validation pass (`StateMappingHelper.StateMap.validate()`) that enforces **strict bidirectionality** between state sensors and state setters using a XOR check:

| Situation | Result |
|---|---|
| `"Type": "State"` sensor for `Foo` exists AND a `"Type": "State"` action setter for `Foo` exists | pass |
| Sensor exists, **no setter** | SEVERE error — role rejected |
| Setter exists, **no sensor** | SEVERE error — role rejected |

Symptoms of failure: `FAIL: MyRole.json: State sensor or State setter action/motion exists without accompanying state/setter: MyState` at startup, followed by `Unknown NPC role 'MyRole' -- cannot switch` repeating every tick.

### `IgnoreMissingSetState` — escape hatch for externally-driven states

When a plugin drives NPC state externally (e.g. via ECS calling `role.getStateSupport().setState()`), the JSON has sensors but no setter actions. Adding `"IgnoreMissingSetState": true` to the sensor registers a no-op dummy setter, satisfying the XOR check:

```json
{ "Type": "State", "State": "TravelingToJob", "IgnoreMissingSetState": true }
```

**`StateTransitions` does NOT satisfy the validator.** Its `From`/`To` entries call `registerStateRequirer()` only — they never touch the sensor or setter bitsets. A role with only `StateTransitions` referencing a state and no sensor+setter pair will still fail validation.

---

## State Transitions

State transitions define actions executed **sequentially** when switching between states. Defined in `"StateTransitions"` at the template level (above `"Instructions"`).

An empty `"From"` or `"To"` array means **all states**.

```json
"StateTransitions": [
  {
    "States": [
      { "From": ["Idle"], "To": ["Sleep"] }
    ],
    "Actions": [
      { "Type": "PlayAnimation", "Slot": "Status", "Animation": "Laydown" },
      { "Type": "Timeout", "Delay": [1, 1] }
    ]
  },
  {
    "States": [
      { "From": ["Sleep"], "To": [] }
    ],
    "Actions": [
      { "Type": "PlayAnimation", "Slot": "Status", "Animation": "Wake" },
      { "Type": "Timeout", "Delay": [1, 1] }
    ]
  }
]
```

### Inventory State Transitions (Equip/Unequip)

```json
{
  "States": [
    { "From": ["Idle"], "To": ["Eat"] }
  ],
  "Actions": [
    { "Type": "Inventory", "Operation": "SetHotbar", "Item": { "Compute": "EatItem" }, "Slot": 2, "UseTarget": false },
    { "Type": "Inventory", "Operation": "EquipHotbar", "Slot": 2, "UseTarget": false }
  ]
}
```

### Combat Entry Transition (Warn Allies)

```json
{
  "States": [
    { "From": [], "To": ["Combat"] }
  ],
  "Actions": [
    { "Type": "PlayAnimation", "Slot": "Status" },
    { "Type": "Beacon", "Message": "Goblin_Ogre_Warn", "TargetGroups": { "Compute": "WarnGroups" }, "SendTargetSlot": "LockedTarget" }
  ]
}
```

---

## Reusable Components

Components are reusable chunks of instruction/sensor logic. They use `"Type": "Component"` and expose parameters.

### Component Structure

```json
{
  "Type": "Component",
  "Class": "Instruction",
  "Parameters": {
    "_ImportStates": ["Main"],
    "Animation": {
      "Value": "",
      "Description": "The animation to play"
    },
    "Duration": {
      "Value": [3, 5],
      "Description": "The amount of time to wait before transitioning"
    }
  },
  "Content": {
    "Continue": true,
    "Instructions": [
      {
        "Reference": "Component_Instruction_State_Timeout",
        "Modify": {
          "_ExportStates": ["Main"],
          "Delay": { "Compute": "Duration" }
        }
      },
      {
        "Reference": "Component_Instruction_Play_Animation",
        "Modify": {
          "Animation": { "Compute": "Animation" }
        }
      }
    ]
  }
}
```

### Using Components (Reference + Modify)

```json
{
  "Reference": "Component_Instruction_Intelligent_Idle_Motion_Follow_Path"
}
```

With parameter overrides:

```json
{
  "Reference": "Component_Instruction_State_Timeout",
  "Modify": {
    "_ExportStates": ["Idle.Default"],
    "Delay": [30, 45]
  }
}
```

### State Import/Export Pattern

- `_ImportStates`: Declares named state slots a component expects from the user.
- `_ExportStates`: Provides concrete state names to fill those slots.
- `ParentState` action: Uses the imported state name to switch states.

```json
// In component:
"_ImportStates": ["Main"],
"Actions": [
  { "Type": "ParentState", "State": "Main" }
]

// When referencing:
"Modify": {
  "_ExportStates": ["Idle.Default"]
}
```

### Common Built-In Components

| Component | Class | Purpose |
|-----------|-------|---------|
| `Component_Instruction_Intelligent_Idle_Motion_Follow_Path` | Instruction | Follow a path marker for idle guard behavior |
| `Component_Instruction_Intelligent_Chase` | Instruction | Smart chase behavior with pathfinding and lost-target handling |
| `Component_Instruction_Soft_Leash` | Instruction | Return home if NPC goes too far from spawn |
| `Component_Instruction_Damage_Check` | Instruction | React to incoming damage |
| `Component_Instruction_Play_Animation` | Instruction | Play a named animation |
| `Component_Instruction_State_Timeout` | Instruction | Wait then switch to a parent state |
| `Component_Instruction_Play_Animation_In_State_For_Duration` | Instruction | Play animation for a random duration then switch state |
| `Component_Sensor_Standard_Detection` | Sensor | Sight + hearing detection with attitude filtering |
| `Component_Sensor_Lost_Target_Detection` | Sensor | Detect a previously-seen target |

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

## Combat System

### Alerted State Pattern

Transitional state between detection and combat:

```json
{
  "Sensor": { "Type": "State", "State": "Alerted" },
  "Instructions": [
    {
      "Reference": "Component_Instruction_Play_Animation",
      "Modify": { "Animation": "Alerted" }
    },
    {
      "Continue": true,
      "Sensor": {
        "Type": "Target",
        "Range": { "Compute": "AlertedRange" },
        "Filters": [ { "Type": "LineOfSight" } ]
      },
      "HeadMotion": { "Type": "Watch" }
    },
    {
      "Sensor": { "Type": "Target", "Range": { "Compute": "AlertedRange" } },
      "ActionsBlocking": true,
      "Actions": [
        { "Type": "Timeout", "Delay": [1, 1] },
        { "Type": "State", "State": "Combat" }
      ]
    },
    {
      "Actions": [ { "Type": "State", "State": "Idle" } ]
    }
  ]
}
```

### Combat State with Chase Substate

```json
{
  "Sensor": { "Type": "State", "State": "Combat" },
  "Instructions": [
    {
      "Sensor": { "Type": "State", "State": ".Chase" },
      "Instructions": [
        {
          "Sensor": {
            "Type": "Target",
            "Range": { "Compute": "AttackDistance" },
            "Filters": [ { "Type": "LineOfSight" } ]
          },
          "Actions": [ { "Type": "State", "State": ".Default" } ]
        },
        {
          "Reference": "Component_Instruction_Soft_Leash",
          "Modify": {
            "_ExportStates": ["ReturnHome"],
            "LeashDistance": { "Compute": "LeashDistance" },
            "LeashMinPlayerDistance": { "Compute": "LeashMinPlayerDistance" },
            "LeashTimer": { "Compute": "LeashTimer" },
            "HardLeashDistance": { "Compute": "HardLeashDistance" }
          }
        },
        {
          "Reference": "Component_Instruction_Intelligent_Chase",
          "Modify": {
            "_ExportStates": ["Search", "Search", "ReturnHome"],
            "ViewRange": { "Compute": "AlertedRange * 2" },
            "HearingRange": { "Compute": "HearingRange * 2" },
            "StopDistance": 0.1,
            "RelativeSpeed": 0.5
          }
        }
      ]
    },
    {
      "$Comment": "NPC melee attack",
      "Sensor": {
        "Type": "Target",
        "Range": { "Compute": "AttackDistance" },
        "Filters": [ { "Type": "LineOfSight" } ],
        "ActionsBlocking": true,
        "Actions": [
          {
            "Type": "Attack",
            "Attack": { "Compute": "Attack" },
            "AttackPauseRange": { "Compute": "AttackPauseRange" }
          },
          { "Type": "Timeout", "Delay": [0.2, 0.2] }
        ],
        "HeadMotion": {
          "Type": "Aim",
          "RelativeTurnSpeed": { "Compute": "CombatRelativeTurnSpeed" }
        }
      },
      "Actions": [ { "Type": "State", "State": ".Chase" } ]
    }
  ]
}
```

### Key Combat Parameters

```json
"Attack": {
  "Value": "Root_NPC_Goblin_Ogre_Attack",
  "Description": "The attack to use."
},
"AttackDistance": {
  "Value": 2,
  "Description": "The distance at which an NPC will execute attacks"
},
"AttackPauseRange": {
  "Value": [1.5, 2],
  "Description": "Absolute minimum time before a second attack"
},
"CombatRelativeTurnSpeed": {
  "Value": 1.5,
  "Description": "Turn speed modifier in combat"
},
"LeashDistance": {
  "Value": 20,
  "Description": "Range after which NPC starts wanting to return"
},
"HardLeashDistance": {
  "Value": 60,
  "Description": "Absolute maximum from leash position"
}
```

---

## Attack Interactions

### Root Interaction (Chaining Attacks)

```json
{
  "Interactions": [
    {
      "Type": "Chaining",
      "ChainId": "Slashes",
      "ChainingAllowance": 15,
      "Next": [
        "Goblin_Ogre_Swing_Left",
        "Goblin_Ogre_Swing_Right",
        "Goblin_Ogre_Swing_Down"
      ]
    }
  ],
  "Tags": {
    "Attack": ["Melee"]
  }
}
```

NPCs attack in sequence: first `Swing_Left`, then `Swing_Right` (if within 15s), then `Swing_Down`.

### Individual Attack Interaction

```json
{
  "Type": "Simple",
  "Effects": {
    "ItemPlayerAnimationsId": "Goblin_Club",
    "ItemAnimationId": "SwingLeft"
  },
  "RunTime": 0.2,
  "Next": {
    "Type": "Selector",
    "RunTime": 0.25,
    "Selector": {
      "Id": "Horizontal",
      "Direction": "ToLeft",
      "TestLineOfSight": true,
      "ExtendTop": 0.5,
      "ExtendBottom": 2,
      "StartDistance": 0.1,
      "EndDistance": 3.5,
      "Length": 60,
      "RollOffset": 0,
      "YawStartOffset": -30
    },
    "HitEntity": {
      "Interactions": [
        {
          "Parent": "DamageEntityParent",
          "DamageCalculator": {
            "BaseDamage": { "Physical": 8 },
            "RandomPercentageModifier": 0.1
          },
          "DamageEffects": {
            "Knockback": { "Force": 0.5, "RelativeX": -5, "RelativeZ": -5, "VelocityY": 5 },
            "WorldSoundEventId": "SFX_Unarmed_Impact",
            "WorldParticles": [ { "SystemId": "Impact_Blade_01" } ]
          }
        }
      ]
    },
    "Next": {
      "Type": "Simple",
      "RunTime": 0.1
    }
  }
}
```

### InteractionVars (Template-Level Damage Override)

Templates can define overridable interaction variable slots:

```json
"InteractionVars": {
  "Melee_Damage": {
    "Interactions": [
      {
        "Parent": "NPC_Attack_Melee_Damage",
        "DamageCalculator": {
          "Type": "Absolute",
          "BaseDamage": { "Physical": 10 },
          "RandomPercentageModifier": 0.1
        }
      }
    ]
  }
}
```

Variants override these via `"Modify"`:

```json
"InteractionVars": {
  "Melee_SwingDown_Damage": {
    "Interactions": [
      {
        "Parent": "Goblin_Ogre_Swing_Down_Damage",
        "DamageCalculator": {
          "Type": "Absolute",
          "BaseDamage": { "Physical": 20 }
        }
      }
    ]
  }
}
```

---

## Inter-NPC Interaction

### Beacon Communication

NPCs communicate via named beacon messages. One NPC sends a message, another listens for it.

**Listening for a beacon (receiver):**

```json
{
  "Sensor": {
    "Type": "Beacon",
    "Message": "Annoy_Ogre",
    "Range": 5
  },
  "Actions": [
    {
      "Type": "Attack",
      "Attack": { "Compute": "SleepingAttack" },
      "AttackPauseRange": [1, 2]
    }
  ]
}
```

**Sending a beacon (via state transition):**

```json
{
  "Type": "Beacon",
  "Message": "Goblin_Ogre_Warn",
  "TargetGroups": { "Compute": "WarnGroups" },
  "SendTargetSlot": "LockedTarget"
}
```

### NPC Groups

Define groups for filtering:

```json
{
  "IncludeRoles": ["Goblin_Scrapper"]
}
```

```json
{
  "IncludeRoles": ["Edible_Rat"]
}
```

### Spawn Beacons (Manual NPC Spawning)

Trigger beacon-based NPC spawning for inter-NPC behaviors:

**Spawn beacon definition:**

```json
{
  "Environments": [],
  "NPCs": [
    { "Weight": 1, "Id": "Edible Rat" }
  ],
  "SpawnAfterGameTimeRange": ["PT5M", "PT10M"],
  "NPCSpawnState": "Seek",
  "TargetSlot": "LockedTarget"
}
```

**Triggering from template:**

```json
{
  "Continue": true,
  "Sensor": { "Type": "Any", "Once": true },
  "Actions": [
    {
      "Type": "TriggerSpawnBeacon",
      "BeaconSpawn": { "Compute": "FoodNPCBeacon" },
      "Range": 15
    }
  ]
}
```

### Edible Critter Template Pattern

Generic template for NPCs that seek a target and get consumed:

```json
{
  "Type": "Abstract",
  "KnockbackScale": 0.5,
  "Parameters": {
    "Appearance": { "Value": "Rat", "Description": "Model to be used" },
    "WalkSpeed": { "Value": 3, "Description": "How fast this critter moves" },
    "SeekRange": { "Value": 40, "Description": "How far it can be from eater" },
    "MaxHealth": { "Value": 100, "Description": "Max health for the NPC" }
  },
  "Appearance": { "Compute": "Appearance" },
  "StartState": "Idle",
  "MaxHealth": { "Compute": "MaxHealth" },
  "Instructions": [
    {
      "Instructions": [
        {
          "Sensor": { "Type": "State", "State": "Idle" },
          "Instructions": [
            {
              "Sensor": { "Type": "Beacon", "Message": "Approach_Target", "TargetSlot": "LockedTarget" },
              "Actions": [ { "Type": "State", "State": "Seek" } ]
            },
            {
              "ActionsBlocking": true,
              "Actions": [
                { "Type": "Timeout", "Delay": [1, 1] },
                { "Type": "Despawn" }
              ]
            }
          ]
        },
        {
          "Sensor": { "Type": "State", "State": "Seek" },
          "Instructions": [
            {
              "Sensor": { "Type": "Target", "TargetSlot": "LockedTarget", "Range": { "Compute": "SeekRange" } },
              "BodyMotion": { "Type": "Seek", "SlowDownDistance": 0.1, "StopDistance": 0.1 }
            },
            {
              "ActionsBlocking": true,
              "Actions": [
                { "Type": "Timeout", "Delay": [1, 1] },
                { "Type": "State", "State": "Idle" }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

---

## ReturnHome & Search States

### ReturnHome

Handles returning to spawn point after leash triggers. Heals to full on arrival.

```json
{
  "Sensor": { "Type": "State", "State": "ReturnHome" },
  "Instructions": [
    {
      "Sensor": {
        "Type": "And",
        "Sensors": [
          { "Type": "Damage", "Combat": true, "TargetSlot": "LockedTarget",
            "Enabled": { "Compute": "AbsoluteDetectionRange > 0" } },
          { "Type": "Target", "TargetSlot": "LockedTarget",
            "Range": { "Compute": "AbsoluteDetectionRange" } }
        ]
      },
      "Actions": [ { "Type": "State", "State": "Combat" } ]
    },
    {
      "Sensor": { "Type": "Leash", "Range": { "Compute": "LeashDistance * 0.3" } },
      "BodyMotion": {
        "Type": "Seek",
        "SlowDownDistance": { "Compute": "LeashDistance * 0.4" },
        "StopDistance": { "Compute": "LeashDistance * 0.2" },
        "RelativeSpeed": 0.8,
        "UsePathfinder": true
      }
    },
    {
      "Actions": [
        { "Type": "SetStat", "Stat": "Health", "Value": 1000000 },
        { "Type": "State", "State": "Idle" }
      ]
    }
  ]
}
```

### Search State

Wander around looking for lost target before returning to idle:

```json
{
  "Sensor": { "Type": "State", "State": "Search" },
  "Instructions": [
    {
      "Sensor": { "Type": "Damage", "Combat": true, "TargetSlot": "LockedTarget" },
      "Actions": [ { "Type": "State", "State": "Alerted" } ]
    },
    {
      "Instructions": [
        {
          "Sensor": { "Reference": "Component_Sensor_Lost_Target_Detection", "Modify": { ... } },
          "Actions": [ { "Type": "State", "State": "Combat" } ]
        },
        {
          "Sensor": { "Reference": "Component_Sensor_Standard_Detection", "Modify": { ... } },
          "Actions": [ { "Type": "State", "State": "Alerted" } ]
        },
        {
          "BodyMotion": { "Type": "Sequence", "Motions": [ /* wander pattern */ ] },
          "ActionsBlocking": true,
          "Actions": [
            { "Type": "Timeout", "Delay": [4, 5] },
            { "Type": "State", "State": "Idle" }
          ]
        }
      ]
    }
  ]
}
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

Fires when a block in a `BlockSet` within range is damaged, destroyed, or interacted with by a player or NPC. Provides player/NPC target.

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

Fires a ray at a fixed angle from the NPC's heading to detect blocks. The result is **cached** and only re-tested when the NPC rotates or moves past thresholds (`MinRetestAngle`, `MinRetestMove`). Identifies blocks directly ahead (good for detecting trees/ore before the NPC reaches them). Provides vector position.

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

Stores the vector position provided by the instruction's sensor into a named slot. This slot can then be read by `ReadPosition` to navigate to that position later.

```json
{
  "Sensor": { "Type": "Block", "Blocks": { "Compute": "TreeBlocks" }, "Range": 20.0 },
  "Actions": [ { "Type": "StorePosition", "Slot": "NavTarget" } ]
}
```

---

## Target Slot System

NPCs can lock onto entities using **named target slots** (strings). The default slot name is `"LockedTarget"`. Multiple custom slots allow tracking several entities simultaneously.

| Action / Sensor | Slot field | Description |
|-----------------|------------|-------------|
| `Mob` / `Player` / `Damage` sensor | `LockedTargetSlot` | Slot where the matched entity is stored |
| `Target` sensor | `TargetSlot` | Test if a specific slot has a valid entity |
| `SetMarkedTarget` action | `TargetSlot` | Explicitly copy sensor-provided target into a slot |
| `ReleaseTarget` action | `TargetSlot` | Clear a slot |
| `Not` sensor + `UseTargetSlot` | — | Feed a stored target into action context without a fresh sensor |

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

## Timer System

Named, countdown timers. Each has a current value that decrements at a configurable `Rate`. Stop/pause/restart via actions. Query state and remaining time via `Timer: Sensor`.

### Starting and managing a timer

```json
// Start a 5–10 second cooldown timer named "AttackCooldown":
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

Lighter-weight alternative to timers for simple "set a one-shot delay" patterns. No rate/pause needed.

```json
// Set an alarm to fire in 3–5 seconds:
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

Named boolean flags that persist on the NPC within a session. Use for "has this happened yet?" gates.

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

The selected child's sensor is still checked every tick — if it fails the engine runs nothing for that tick (it does NOT fall back to another child; it simply does nothing until the timeout expires and re-picks).

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

Add to the top of a template to display the current state:

```json
"Debug": "DisplayState",
```

Enable NPC pathfinding visualization with the `VisPath` debug flag (Update 4):

```json
"Debug": "VisPath",
```

Generate an NPC descriptors file for debugging NPC configurations (Update 4):

```
/npc descriptors
```

Use `$Comment` fields for documentation:

```json
"$Comment": "Check for any hostile targets in range that could alert the NPC"
```

---

## NPC Design Process

1. **Read design requirements** — Understand what the NPC should do.
2. **Decide on states** — Break behavior into top-level states (Idle, Sleep, Eat, Combat, etc.) and substates.
3. **Find reusable components** — Check existing `Component_Instruction_*` and `Component_Sensor_*` files.
4. **Identify reusable parts** — Extract common logic into new components.
5. **Build incrementally** — Add one behavior at a time and **test after each addition**.
6. **Parameterize** — Expose configurable values so variants can customize behavior.

### Common Template Header Fields

```json
{
  "Type": "Abstract",
  "Debug": "DisplayState",
  "StartState": "Idle",
  "DefaultPlayerAttitude": "Hostile",
  "DefaultNPCAttitude": "Ignore",
  "AttitudeGroup": { "Compute": "AttitudeGroup" },
  "KnockbackScale": 0.5,
  "Appearance": { "Compute": "Appearance" },
  "DropList": { "Compute": "DropList" },
  "MaxHealth": { "Compute": "MaxHealth" },
  "NameTranslationKey": { "Compute": "NameTranslationKey" },
  "DeathParticles": { "Compute": "DeathParticles" },
  "DropDeathItemsInstantly": false,
  "DeathAnimationTime": 2.0
}
```

### Death-Related Template Parameters

These NPC template parameters are configurable:

| Parameter | Type | Description |
|-----------|------|-------------|
| `DeathParticles` | string | Particle system to spawn on NPC death |
| `DropDeathItemsInstantly` | boolean | If `true`, drops loot immediately instead of waiting for body despawn |
| `DeathAnimationTime` | float | Duration (seconds) before the NPC body despawns |

---

## Key Points

1. **Templates are Abstract, Variants are concrete** — Templates define reusable behavior; variants provide specific values.
2. **States are the backbone** — Every NPC behavior is organized into states and substates.
3. **Test incrementally** — Add one behavior at a time and test before moving on.
4. **Parameterize everything** — Use `Parameters` + `{ "Compute": "..." }` so variants can customize.
5. **Extract components** — If logic appears in multiple places, make it a component.
6. **Detection priority** — Place `Damage_Check` first, then `Standard_Detection`, then state-specific logic.
7. **Use state transitions for visual polish** — Animations, inventory swaps, and beacon messages.
8. **Beacons for inter-NPC communication** — Don't hard-code NPC coupling; use message passing.
9. **Leash prevents runaway NPCs** — Always add `Soft_Leash` in combat to prevent infinite chasing.
10. **`UseTarget: false`** — Required for actions that modify the NPC itself (inventory, stats).
11. **`Continue: true` on every state block** — Without it, the first matching state stops the engine.
12. **`ActionsBlocking` for sequential logic** — Use it with `Timeout → State` to chain actions over time.
13. **`TreeMode` for fallback logic** — Keeps evaluating siblings until a child matches (behavior-tree selector).
14. **`Block: Sensor` caches its result** — Call `ResetBlockSensors` after destroying the block.
15. **`Reserve: true`** on `Block: Sensor` prevents multiple NPCs competing for the same block.
16. **Timers for long-running cooldowns; Alarms for simpler one-shot delays; Flags for one-time booleans.**

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Template won't compile | Check that all referenced components exist and state names match |
| NPC stuck in one state | Verify state switch actions and `ActionsBlocking` flags |
| NPC doesn't detect player | Check `ViewRange`, `ViewSector`, `HearingRange`, `AbsoluteDetectionRange` parameters |
| NPC chases forever | Add `Component_Instruction_Soft_Leash` with proper `LeashDistance` |
| Animations not playing | Ensure `PlayAnimation` action uses correct `Slot` and animation name |
| NPC ignores damage | Add `Component_Instruction_Damage_Check` to each state's instructions |
| Items not equipping | Call inventory operations with `"UseTarget": false` |
| State transitions not firing | Ensure `StateTransitions` block is above `Instructions` in the JSON |
| Beacon messages not received | Verify NPC groups and beacon `Range` parameter |
| Food NPC not spawning | Check spawn beacon exists, is placed in world, and `TriggerSpawnBeacon` range is sufficient |
| First NPC state blocks all others | Missing `Continue: true` — without it, the first matching entry stops evaluation |
| Only one state ever evaluates | Outer sensorless wrappers, or missing `Continue: true` on state blocks |
| WanderInCircle wanders away from workstation | WanderInCircle uses `getLeashPoint()` — set it from ECS or use `SetLeashPosition` before the state |
| NPC not reaching target closely enough | `StopDistance` too large on `Seek`; add an inner `Seek` with tighter `StopDistance` in the Working state |
| NPC finds wrong block / blocks fight over same block | Use `Block: Sensor` with `Reserve: true` so NPCs don't share target blocks |
| `has defined a filter of type X more than once` at startup | Each filter type can appear at most once per flat `Filters` array. Use a single filter with multiple patterns in `Items` instead of duplicate filter entries. |

---

## Entity Filters

Each filter type (`Inventory`, `ItemInHand`, `LineOfSight`, etc.) can appear **at most once** per flat `Filters` array on a sensor. To match against multiple item patterns, list them all in the `Items` array of a single filter entry:

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

```json
{
  "Type": "Self",
  "Filters": [
    { "Type": "Inventory", "Items": [ "*_Axe*", "*_Hatchet*" ] }
  ]
}
```

The `Items` array uses glob patterns and matches if the entity has any item matching any of the listed patterns.
```

---

## Related Skills

- `hytale-spawning-npcs` — Programmatic NPC spawning via Java (NPCPlugin API)
- `hytale-ecs` — Entity Component System patterns
- `hytale-items` — Item registry, ItemStack, and interactions
- `hytale-entity-effects` — Status effects and buffs
- `hytale-events` — Event system for reacting to NPC-related events

---

## Reference

- Source: [Hytale Modding - NPC Tutorial](https://hytalemodding.dev/en/docs/official-documentation/npc)
