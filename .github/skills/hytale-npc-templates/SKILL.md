---
name: hytale-npc-templates
version: 7
source: https://hytalemodding.com/official-documentation/npc/
authors:
  - name: "HytaleModding"
    url: "https://github.com/HytaleModding"
  - name: "Hypixel Studios"
    url: "https://hytale.com/"
references:
  - name: "NPC Template Reference"
    url: "references/npc-template-reference.md"
tags: [hytale, npc, behavior, templates, ai, json, states, parameters]
---

# Hytale NPC Templates

Core reference for Hytale's JSON-based NPC template system. Covers template types (Generic/Abstract/Variant), parameter declaration, states/substates, the `Instruction` attribute reference, state sensor validation, state transitions, and the NPC design process.

For deeper topics load the specific sub-skills:
- **Sensors & Detection** — `hytale-npc-sensors`
- **Actions, Motions & Timers** — `hytale-npc-actions`
- **Combat AI** — `hytale-npc-combat`
- **Plugin-Driven Pathfinding** — `hytale-npc-pathfinding`
- **Reusable JSON Components** — `hytale-npc-components`

Use when creating or modifying NPC behavior templates, defining NPC states, working with parameters, or understanding the core template/instruction/state model.

## Triggers

- NPC template
- NPC behavior
- NPC state
- state transition
- NPC component
- Template_
- Variant
- BlankTemplate
- Instructions
- StartState
- StateTransitions
- DeathParticles
- DropDeathItemsInstantly
- DeathAnimationTime
- VisPath
- npc descriptors
- NPC debug
- Enabled flag
- Generic template
- Abstract template
- Variant template
- npc role json
- npc template json
- IgnoreMissingSetState
- separation mode
- Parameters
- Modify
- Compute
- MaxHealth
- Appearance
- NameTranslationKey
- MotionControllerList

### Variant with No Overrides

A Variant that inherits all defaults from an Abstract template needs no `Modify` block:

```json
{
  "Type": "Variant",
  "Reference": "Template_My_Base"
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

## Plugin-Driven A* Pathfinding

> Load `hytale-npc-pathfinding` for the complete guide: JSON template snippet, Java `RefChangeSystem` pattern, and `role.getMarkedEntitySupport().getStoredPosition()` API.

---

## Parameters

### `"Parameters"` vs `"Modify"`

These two blocks are **not interchangeable**:

| Block | Used in | Effect |
|---|---|---|
| `"Parameters"` | `Abstract` / `Generic` templates | Declares accepted parameters and their defaults for the template itself |
| `"Modify"` | `Variant` files | Overrides parameter values in the referenced base template |

Using `"Parameters"` in a `Variant` silently has no effect on the base template — all base template parameters remain at their defaults. Always use `"Modify"` in Variant files.

**Correct Variant override:**
```json
{
  "Type": "Variant",
  "Reference": "Template_My_Base",
  "Modify": {
    "MaxHealth": 50,
    "Appearance": "Kweebec"
  }
}
```

### Parameter declaration (Abstract/Generic)

Parameters are declared in `"Parameters"` and referenced via `{ "Compute": "ParamName" }`. They support computed expressions like `"Compute": "ViewRange / DistractedPenalty"`.

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

**Compute operators (source-verified):**

| Operator / Function | Meaning |
|---|---|
| `+`, `-`, `*`, `/` | Arithmetic |
| `%` | Modulus / remainder |
| `**` | Exponent (e.g. `10**2` = 100) |
| `-` (unary) | Negate a value |
| `E` | Scientific notation (e.g. `5E5` = 500000) |
| `==`, `!=`, `>`, `>=`, `<`, `<=` | Comparison — result is boolean, useful in `Enabled` |
| `true`, `false` | Boolean constants |
| `PI` | Pi constant |
| `( )` | Grouping — e.g. `MaxHealth + (MaxHealth / 2)` |
| `[0, 1, 2]` | Numeric array literal |
| `["A", "B"]` | String array literal |
| `max(a, b)` | Largest of two numbers |
| `min(a, b)` | Smallest of two numbers |
| `isEmpty(x)` | True if string/parameter is empty |
| `isEmptyStringArray(x)` | True if string array is empty |
| `isEmptyNumberArray(x)` | True if number array is empty |
| `random()` | Random double 0.0–1.0 |
| `randomInRange(a, b)` | Random double between a and b |
| `makeRange(x)` | Array `[x, x]` (range with equal min/max) |

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

### Substate defaults

Entering a top-level state without specifying a substate automatically enters its `.Default` substate. So `{ "Type": "State", "State": "Idle" }` is equivalent to entering `Idle.Default`. Set `DefaultSubState` on the role to change the default substate name (default: `"Default"`).

You can also jump directly to a substate (e.g. `Farm.Goto`) without passing through the parent state first.

### Substate limitations

- **No sub-sub-states** — only one level of substate nesting is supported.
- **`InteractionInstruction` cannot check substates** — you must use the full `State.SubState` format there (`.SubState` shorthand does not work).
- As a workaround for sub-sub-state logic, use the **Flag system** (`SetFlag` / `Flag` sensor).

### JSON comment keys

Hytale ignores `"$Comment"` and `"$Todo"` keys anywhere in a role JSON — use them freely for inline notes:

```json
{ "$Comment": "Reset leash when entering Idle", "Type": "SetLeashPosition", "ToCurrent": true }
```

---

## Sensors

> Load `hytale-npc-sensors` for the full sensor type reference, entity filters, detection system, block sensors, target slots, and nav sensor.

---

## Actions

> Load `hytale-npc-actions` for the full action type reference, inventory operations, motions, timers, alarms, flags, and the Random instruction.

---

## Instruction Flags

Official attribute reference for the `Instruction` type (status in parentheses):

| Field | Type | Default | Computable | Description |
|-------|------|---------|------------|-------------|
| `Name` | String | `null` | — | Optional name for descriptor |
| `Tag` *(Experimental)* | String | `null` | — | Internal identifier tag for debugging; must be non-empty if supplied |
| `Enabled` | Boolean | `true` | Yes | Whether this instruction should be enabled on the NPC |
| `Sensor` | ObjectRef | `null` | — | Sensor gating the instruction; absent = always matches |
| `BodyMotion` | ObjectRef | `null` | — | Body motion to execute |
| `HeadMotion` | ObjectRef | `null` | — | Head motion to execute |
| `Actions` | ObjectRef | `null` | — | Actions to execute |
| `ActionsBlocking` | Boolean | `false` | — | Do not execute an action unless the previous action completed |
| `ActionsAtomic` | Boolean | `false` | — | Only execute actions if ALL actions can execute; if any fails, none run |
| `Instructions` | Array | `null` | — | Optional nested list of child instructions |
| `Continue` *(WorkInProgress)* | Boolean | `false` | — | Continue evaluating subsequent siblings after this instruction matched |
| `Weight` | Double | `1.0` | Yes | Weighted chance of picking this instruction in a random instruction (must be > 0) |
| `TreeMode` | Boolean | `false` | — | Behavior-tree selector mode: keep continuing to siblings unless a child matches |
| `InvertTreeModeResult` | Boolean | `false` | Yes | Invert the result of `TreeMode` evaluation when propagating to a parent `TreeMode` node |

> `Once` is a sensor-level flag, not an instruction flag — set it on the `Sensor` object to fire only once per state entry.

### Constraints

- At most one of `BodyMotion` or `Instructions` may be provided (not both).
- At most one of `HeadMotion` or `Instructions` may be provided (not both).
- At most one of `Actions` or `Instructions` may be provided (not both).
- If `TreeMode` is `true`, `Continue` must be `false`.

### Evaluation rules (source-verified)

The engine iterates siblings and for each matching instruction calls `execute()`; only if `isContinueAfter()` returns `false` does it break out of the loop.

- A node with **no sensor always matches** (implicit `Any`).
- Without `Continue: true`, the first matching node **stops all sibling evaluation**.
- `Continue: true` means: "I matched — AND keep evaluating the next sibling too".
- You **cannot** have both `BodyMotion` and `Instructions` on the same instruction (enforced by a validation constraint). Leaf instructions carry motion/actions; nested instruction lists carry children only.
- Only **one** `BodyMotion` and one `HeadMotion` are active per tick (the last `setNextBodyMotionStep`/`setNextHeadMotionStep` call wins). Two `Continue: true` siblings setting `BodyMotion` will have the second one override the first.

**Consequence for state-gated blocks**: if each ECS state is wrapped in a sensorless outer `{ "Instructions": [...] }`, the first wrapper always matches and stops the loop. The correct pattern is `Continue: true` + sensor placed directly on each instruction:

```json
{ "Continue": true, "Sensor": { "Type": "State", "State": "Idle" }, "Instructions": [ ... ] },
{ "Continue": true, "Sensor": { "Type": "State", "State": "Combat" }, "Instructions": [ ... ] }
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

> Load `hytale-npc-actions` for the full body/head motion reference, MotionControllerList, WanderInCircle leash behavior, and motion examples.

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

> Load `hytale-npc-components` for the component structure, `_ImportStates`/`_ExportStates` pattern, and the full list of built-in `Component_Instruction_*` and `Component_Sensor_*` entries.

---

## Detection System

> Load `hytale-npc-sensors` for standard detection configuration, sight/hearing parameters, attitude groups, and the `Component_Sensor_Standard_Detection` reference.

---

## Combat System, Attack & Group AI

> Load `hytale-npc-combat` for the complete combat AI pattern (Alerted-Combat-Chase), attack chaining, `InteractionVars`, beacon communication, NPC groups, spawn beacons, ReturnHome, and Search states.

---

## Block Detection, Target Slots & Navigation

> Load `hytale-npc-sensors` for `Block: Sensor` (caching, `Reserve`), `BlockChange`, `BlockType`, `SearchRay`, `StorePosition`, the target slot system, and `Nav: Sensor` state queries.

---

## Timers, Alarms, Flags & Random Instruction

> Load `hytale-npc-actions` for the Timer system (`TimerStart`/`TimerStop`), the Alarm system (ISO-8601 durations), named Flags, and the `Random: Instruction` weighted picker.

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
  "DeathAnimationTime": 5.0,
  "BusyStates": [],
  "SpawnLockTime": 1.5
}
```

### Death-Related Template Parameters

These NPC template parameters are configurable:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `DeathParticles` | string | `null` | Particle system to spawn on NPC death |
| `DropDeathItemsInstantly` | boolean | `false` | If `true`, drops loot immediately instead of waiting for body despawn |
| `DeathAnimationTime` | float | `5.0` | Duration (seconds) before the NPC body despawns (Experimental) |
| `DespawnAnimationTime` | float | `0.8` | Duration of despawn animation before removal (Experimental) |
| `BusyStates` | StringList | required | States in which the NPC cannot be interacted with; used by the `IsBusy` sensor |
| `DefaultSubState` | string | `"Default"` | Substate entered when switching to a parent state without specifying a substate |
| `SpawnLockTime` | float | `1.5` | Seconds the NPC is locked and cannot execute behavior after spawning |

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

> Load `hytale-npc-sensors` for the full entity filter reference (`Inventory`, `ItemInHand`, `EntityType`, `NPCGroup`, `LineOfSight`, etc.), the uniqueness rule, and AND-logic patterns.

---

## Related Skills

- `hytale-npc-sensors` — Full sensor types, entity filters, detection, block sensors, target slots, nav sensor
- `hytale-npc-actions` — All action types, motions, timers, alarms, flags, Random instruction
- `hytale-npc-combat` — Combat AI patterns, attack interactions, beacons, ReturnHome/Search
- `hytale-npc-pathfinding` — Plugin-driven A* navigation via ReadPosition/Seek
- `hytale-npc-components` — Reusable JSON instruction/sensor components
- `hytale-spawning-npcs` — Programmatic NPC spawning via Java (NPCPlugin API)
- `hytale-ecs` — Entity Component System patterns
- `hytale-items` — Item registry, ItemStack, and interactions
- `hytale-entity-effects` — Status effects and buffs
- `hytale-events` — Event system for reacting to NPC-related events

---

## Official Javadoc References

- [`NPCPlugin`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/NPCPlugin.html) — NPC system entry point; `registerCoreComponentType()`, `spawnNPC()`, `spawnEntity()`
- [`Role`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/Role.html) — Runtime role object. Key support accessors:
  - [`getStateSupport()`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/Role.html#getStateSupport())
  - [`getCombatSupport()`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/Role.html#getCombatSupport())
  - [`getWorldSupport()`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/Role.html#getWorldSupport())
  - [`getMarkedEntitySupport()`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/Role.html#getMarkedEntitySupport()) — stored position slots
  - [`getPositionCache()`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/Role.html#getPositionCache())
  - [`getEntitySupport()`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/Role.html#getEntitySupport())
  - [`getRoleStats()`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/Role.html#getRoleStats())
  - [`getDebugSupport()`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/Role.html#getDebugSupport())
- [`Role.AvoidanceMode`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/Role.AvoidanceMode.html) — avoidance mode enum
- [`Role.SeparationMode`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/Role.SeparationMode.html) — separation mode enum
- [`Role.DeferredAction`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/Role.DeferredAction.html) — deferred action interface
- [`com.hypixel.hytale.server.npc.role` package](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/package-summary.html)
- [`AllNPCsLoadedEvent`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/AllNPCsLoadedEvent.html) — fired after all NPC roles finish loading
