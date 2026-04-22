---
name: hytalecolonies-npc-design
version: 7
description: >
  Defines the canonical NPC design architecture for the HytaleColonies plugin.
  Covers the ECS/JSON contract, state machine, notification channel, system responsibilities,
  and authoring rules for colonist roles. Use whenever creating or modifying colonist
  NPC types, job systems, role JSON files, ECS systems, or job-related components.
tags: [hytalecolonies, npc, ecs, colonist, job, state-machine, architecture]
---

# HytaleColonies NPC Design

## Core principle

**ECS is the single source of truth for state and state-transition decisions.**
**JSON is the single source of truth for movement, animation, and behavior while in a state.**

These two concerns must not bleed into each other. ECS never issues a `BodyMotion`.
JSON never sets a `JobState` or decides when to transition between states.

---

## ECS / JSON contract

### ECS → JSON (ECS writes, JSON reads)

| Channel | How |
|---|---|
| `JobComponent.jobState` | JSON gates all behavior on native `"Type": "State"` sensors (with `"IgnoreMissingSetState": true`) |
| Navigation target position | ECS writes the world position; JSON executes `Seek` toward it |
| NPC leash point | ECS writes `NPCEntity.getLeashPoint()` via `ColonistLeashUtil`; JSON `WanderInCircle` constrains to it |

ECS sets the state, the destination, and the wander anchor. JSON drives the body.

### JSON → ECS (JSON writes, ECS reads)

JSON surfaces consequential events upward via **notification flags** on `JobComponent`.
These are `boolean` fields, default `false`, cleared by ECS after reading.

- Add one flag per distinct event type per job.
- The notification action only sets the flag to `true` — no logic inside it.
- ECS decides what the event means.

---

## ECS state machine

`JobComponent.jobState` (`JobState` enum).  Each state belongs to a `JobState.Group` that maps 1-to-1 with the NPC role main-state name.

```
                  ┌── Sleeping
    Idle ─────────┤── TravelingToWorkstation
    (Group.Idle)  └── TravelingToHome

                  ┌── Working (substate: Harvesting)
    Working ──────┤── WaitingForWork
  (Group.Working) ├── TravelingToWorkSite
                  ├── CollectingDrops
                  └── DeliveringItems

    Recharging   (Group.Recharging — reserved, not yet implemented)
```

Typical working cycle:

```
Idle ──► TravelingToWorkSite ──► Working ──► CollectingDrops ──► DeliveringItems ──► TravelingHome ──► Idle
```

### JobState structure

`JobState` is a single flat enum for codec serialization. The `group` field carries the high-level phase; `npcSubState` is the NPC role sub-state name (`null` = use the group name as sub-state).

```java
// Check which phase a state belongs to:
if (state.group == JobState.Group.Working) { ... }

// ColonistStateUtil drives the NPC role state machine using these:
state.npcMainState()  // → "Idle", "Working", or "Recharging"
state.npcSubState     // → "Harvesting", "TravelingToWorkSite", null, etc.
```

Do NOT add switch/case on `JobState` values in new code — use `state.group` for phase checks and `state.npcSubState` for NPC role mirroring.

### State semantics

| State | Meaning | Who transitions away | Leash point set by ECS |
|---|---|---|---|
| `Idle` | At workstation, waiting or missing tools | ECS — claims target → `TravelingToWorkSite` | Workstation block centre |
| `TravelingToWorkSite` | Walking to claimed work block/tree | ECS — arrived → `Working` | (unchanged) |
| `Working` | Actively performing task (mining, chopping) | ECS — on notification → next state | (unchanged) |
| `CollectingDrops` | Picking up drops at harvest site | ECS — timer → `DeliveringItems` | Last harvested block centre |
| `DeliveringItems` | Walking to container and depositing | ECS — delivered → `TravelingHome` | (unchanged) |
| `TravelingHome` | Returning to workstation | ECS — arrived → `Idle` | (unchanged) |
| `WaitingForWork` | No eligible work block found; waiting within `Working` phase | ECS — block becomes available | (unchanged) |
| `TravelingToWorkstation` | Walking from home to the workstation | ECS — arrived | (unchanged) |
| `TravelingToHome` | Walking from workstation back to home | ECS — arrived | (unchanged) |
| `Sleeping` | Reserved: sleeping at night | — | — |
| `Recharging` | Reserved: energy/hunger system | — | — |

---

## System responsibilities

### Main job system (`DelayedEntitySystem`, ~2s cadence)

Handles low-latency-tolerant decisions:
- `Idle`: scan for targets, claim, write navigation target, transition to `TravelingToWorkSite`. When no work is found sets `workAvailable = false` and stays in `Idle`.
- Shared delivery pipeline: `CollectingDrops` → `DeliveringItems` → `TravelingHome`

### Per-job fast-tick system (`EntityTickingSystem`)

**Required for any job type that needs reactive mid-task detection.**
Query-filtered to colonists in `Working` state only.

- Read and clear notification flags from `JobComponent`
- Apply game-logic decisions (quota check, next target selection, etc.)
- Set `JobState` transition via `CommandBuffer`

---

## Package structure

```
com.hytalecolonies/
├── HytaleColoniesPlugin.java
├── commands/
├── components/
│   ├── jobs/            JobComponent, JobState, JobType, JobTargetComponent,
│   │                    MinerJobComponent, WoodsmanJobComponent,
│   │                    UnemployedComponent, WorkStationComponent
│   ├── npc/             ColonistComponent, MoveToTargetComponent
│   └── world/           ClaimableBlock, ClaimedBlockComponent,
│                        ClaimedBlockRegistry, HarvestableTreeComponent
├── debug/               DebugCategory, DebugConfig, DebugLog, DebugLogUtil, DebugTiming
├── interactions/        SpawnColonistInteraction
├── listeners/           PlayerListener
├── npc/
│   ├── actions/
│   │   ├── common/      Shared actions: HarvestBlock, EquipBestTool, DepositItems,
│   │   │                FindDeliveryContainer, NavigateToWorkstation, NotifyBlockBroken,
│   │   │                ReleaseJobTarget, LogDebug, SetEcsJobState
│   │   ├── miner/       SeekNextMineBlock, ClaimNextMineBlock (deprecated),
│   │   │                IncrementBlocksMined, ResetBlocksMined
│   │   └── woodsman/    SeekNearestTree, ClaimNearestTree (deprecated),
│   │                    FindNextTrunkBlock
│   └── sensors/
│       ├── common/      JobTarget, JobTargetBroken, JobTargetExists,
│       │                AtWorkstation, CollectionTimerElapsed, NoWorkAvailable
│       ├── miner/       MineQuotaReached
│       └── woodsman/    HarvestableTree
├── systems/
│   ├── ColonySystem.java
│   ├── jobs/            ColonistJobSystem, ColonistDeliverySystem, ColonistCleanupSystem,
│   │                    ColonistItemPickupSystem, ClaimedBlockCleanupSystem,
│   │                    JobAssignmentSystems, JobRegistry, ColonistRoleMap,
│   │                    MinerWorkingSystem, WoodsmanWorkingSystem, WorkstationInitSystem
│   ├── npc/             ColonistRemovalSystem, PathFindingSystem
│   └── world/           TreeScannerSystem, TreeDetector, TreeDetectorBFS,
│                        TreeBlockChangeEventSystem, ITreeDetector
├── ui/                  DebugConfigUI, HytaleColoniesDashboardUI
└── utils/               ColonistStateUtil, ColonistLeashUtil, ColonistToolUtil,
                         JobNavigationUtil, ClaimBlockUtil, WorkStationUtil,
                         WorkstationContainerUtil, BlockStateInfoUtil,
                         MinerUtil, WoodsmanUtil
```

### Key util classes

| Class | Purpose |
|---|---|
| `ColonistStateUtil` | Canonical state setter — writes `JobComponent.jobState` AND mirrors to NPC role state machine |
| `WorkStationUtil` | `resolve()` / `resolveAt()` — gets `WorkStationComponent` from entity ref or world position |
| `ClaimBlockUtil` | `claimBlock()` / `unclaimBlock()` — filler-block-aware exclusive block reservation |
| `JobNavigationUtil` | `claimAndNavigateTo()` — atomic claim + set `JobTargetComponent` + write `MoveToTargetComponent` |
| `ColonistLeashUtil` | `setLeash()` / `setLeashToBlockCenter()` — sets NPC wander leash point |
| `ColonistToolUtil` | Tool quality/power suitability checks against block `GatherType` |
| `WorkstationContainerUtil` | `findNearbyContainer()` — spatial index query for delivery chests near workstation |
| `BlockStateInfoUtil` | Converts `BlockStateInfo` index → `Vector3i` world position |
| `MinerUtil` | `findNextMineBlock()`, `blockCenter()` — miner job logic utilities |
| `WoodsmanUtil` | `findNextBaseBlock()` — flood-fill BFS for next standing trunk block |

---

---

## JSON role authoring rules

### Structure

Every colonist role JSON must follow this structure:
1. **Working cross-cut blocks** (one or more, each with `Continue: true`): gated on a native `"Type": "State"` sensor for `Working` — chop/mine loop and block-broken notification. These go at the top level so both fire every tick regardless of other state handling.
2. **Remaining state blocks as direct top-level siblings** (each with `Continue: true` and its own state sensor): mutually exclusive since only one ECS state is active at a time. Include a final `Any` + `BodyMotion:Nothing` fallback.
3. **No role-level `StateTransitions`** unless purely cosmetic (e.g. animation resets)
4. **No bridge actions that make game-logic decisions** — JSON may only call notification flag setters

> **Critical `Continue` rule**: A node without a sensor always "matches". Without `Continue: true`, a matching node consumes the evaluation and stops all subsequent siblings from running. Always set `Continue: true` on every state-gated instruction at the top level.

### State sensor pattern

Colonist role JSONs use the **native `"Type": "State"` sensor** with `"IgnoreMissingSetState": true`. This flag registers a no-op dummy setter alongside the sensor, satisfying the NPC validator's XOR bidirectionality check without requiring actual `"Type": "State"` setter actions in the JSON. ECS drives all state changes externally via `ColonistStateUtil.setJobState()`.

**Never use `"Type": "EcsJobState"` custom sensors** — that was a workaround for the validator and has been removed. The native sensor is cleaner and supports `StateTransitions`.

### Role JSON skeleton

Every state is its own top-level instruction with `Continue: true` and its native `State` sensor placed directly on it. Since only one ECS state is active at a time, each instruction fires or doesn't — `Continue: true` means evaluation proceeds to the next sibling regardless. No artificial grouping needed.

Working states that need two separate tick-level concerns (chop loop AND block-broken notify) appear as two consecutive entries with the same `Working` sensor.

```json
"Instructions": [
  {
    "Continue": true,
    "Sensor": { "Type": "State", "State": "TravelingToWorkSite", "IgnoreMissingSetState": true },
    "Instructions": [ ... Seek NavTarget ... ]
  },
  {
    "$Comment": "Working: final approach + chop/mine loop.",
    "Continue": true,
    "Sensor": { "Type": "State", "State": "Working", "IgnoreMissingSetState": true },
    "Instructions": [
      { "Continue": true, "Sensor": { "Type": "ReadPosition", "Slot": "NavTarget", "Range": 5.0, "MinRange": 1.5 }, "BodyMotion": { "Type": "Seek", "StopDistance": 1.0, "SlowDownDistance": 2.0, "RelativeSpeed": 0.8 } },
      { "Sensor": { "Type": "JobTarget", "Range": 2.5 }, "ActionsBlocking": true, "Actions": [ ... equip, harvest, timeout ... ] }
    ]
  },
  {
    "$Comment": "Working: block-broken notification to ECS.",
    "Continue": true,
    "Sensor": { "Type": "State", "State": "Working", "IgnoreMissingSetState": true },
    "Instructions": [
      { "Sensor": { "Type": "JobTargetBroken" }, "Actions": [ { "Type": "NotifyBlockBroken" } ] }
    ]
  },
  {
    "$Comment": "CollectingDrops: wander around leash point (set by ECS to harvested block) + pick up items.",
    "Continue": true,
    "Sensor": { "Type": "State", "State": "CollectingDrops", "IgnoreMissingSetState": true },
    "Instructions": [
      { "Continue": true, "Sensor": { "Type": "Any" }, "BodyMotion": { "Type": "WanderInCircle", "Radius": 5, "MaxHeadingChange": 60, "RelativeSpeed": 0.5 } },
      { "Sensor": { "Type": "DroppedItem", "Range": 5.0 }, "Actions": [ { "Type": "PickUpItem", "Hoover": true } ] }
    ]
  },
  {
    "Continue": true,
    "Sensor": { "Type": "State", "State": "DeliveringItems", "IgnoreMissingSetState": true },
    "Instructions": [ ... Seek NavTarget ... ]
  },
  {
    "Continue": true,
    "Sensor": { "Type": "State", "State": "TravelingHome", "IgnoreMissingSetState": true },
    "Instructions": [ ... Seek NavTarget ... ]
  },
  {
    "$Comment": "Idle: wander around leash point (set by ECS to workstation) + tool-check particle.",
    "Continue": true,
    "Sensor": { "Type": "State", "State": "Idle", "IgnoreMissingSetState": true },
    "Instructions": [
      { "Continue": true, "Sensor": { "Type": "ReadPosition", "Slot": "NavTarget", "Range": 5.0 }, "BodyMotion": { "Type": "Sequence", "Looped": true, "Motions": [ ... WanderInCircle ... ] } },
      { "Sensor": { "Type": "Not", "Sensor": { "Type": "HasTool", "GatherType": "..." } }, "ActionsBlocking": true, "Actions": [ ... particle + timeout ... ] }
    ]
  },
  { "Sensor": { "Type": "Any" }, "BodyMotion": { "Type": "Nothing" } }
]
```

> **Working state approach**: Always include a `ReadPosition NavTarget` Seek with `Continue: true` as the first inner instruction inside the Working block, before the harvest action. This finishes closing the final gap to the target block after arriving from `TravelingToJob`. Without it the NPC stands still wherever the travel Seek stopped.

### Permitted in JSON per state

| ECS State | Permitted JSON behavior |
|---|---|
| `Idle` | `WanderInCircle` (leash → workstation, set by ECS); tool-check particle + wait |
| `TravelingToWorkSite` | Seek NavTarget only |
| `Working` | Final-approach Seek + tool equip → harvest → timeout loop; `NotifyBlockBroken` on broken block |
| `CollectingDrops` | `WanderInCircle` (leash → harvest site, set by ECS) + `PickUpItem` |
| `DeliveringItems` | Seek NavTarget only |
| `TravelingHome` | Seek NavTarget only |

### JSON sub-states

Sub-states within an ECS state block are fine for rich behavior sequences. Rules:
- Must be entered and exited within the same ECS state
- Must not set `JobState` or call any bridge action other than notification flags
- ECS must always be able to interrupt them by changing `JobState`

### What JSON must never do

- Decide when to transition `JobState`
- Call any action that performs game-logic computations
- Use `"Type": "State"` sensors **without** `"IgnoreMissingSetState": true` when states are externally driven — the validator will reject the role at startup

---

## Component conventions

### `JobComponent`

Carries: current `JobState`, workstation position, delivery-pipeline state, `workAvailable` flag, and any notification flags.

- Transient fields (not persisted): flags, runtime positions, counters that reset on restart
- Persisted fields: `jobState`, `workStationBlockPosition`

### Per-job component

Carries only persisted per-worker state meaningful across restarts (e.g. progress counters that prevent over-work after restart).

Does not carry: flags, counters derivable from workstation config, or transient runtime state.

### Workstation component

Single source of truth for all job configuration. Never hard-code these values.

---

## Leash point conventions

`WanderInCircle` constrains wander to a circle around `NPCEntity.getLeashPoint()`. ECS is responsible for keeping this pointed at the correct anchor as state changes. Use `ColonistLeashUtil` for all leash updates:

```java
// Set leash to workstation (call in idle handler every tick):
ColonistLeashUtil.setLeashToBlockCenter(ref, store, workStationPos);

// Set leash to harvested block (call before transitioning to CollectingDrops):
ColonistLeashUtil.setLeashToBlockCenter(ref, store, lastHarvestedBlockPos);
```

| When | Leash set to |
|---|---|
| Entering `Idle` / every idle tick | Workstation block centre |
| Entering `CollectingDrops` (woodsman) | Last harvested tree base block centre |
| Entering `CollectingDrops` (miner) | Last mined block centre |

---

## Adding a new job type checklist

1. **Create an `Idle` state handler** in the main job system (`ColonistJobSystem`):
   - Call `ColonistLeashUtil.setLeashToBlockCenter(ref, store, workStationPos)` to anchor wander
   - Scan for a target block, claim it, write `JobTargetComponent` + nav via `JobNavigationUtil`
   - Transition to `TravelingToWorkSite`
2. **Create a fast-tick `EntityTickingSystem`** for `Working` state reaction (if needed):
   - Filter query to entities with both `JobComponent` (state == Working) and the job-specific component
   - Read and clear notification flags from `JobComponent`
   - Call job utility (`MinerUtil`, `WoodsmanUtil`, or new `XxxUtil` in `utils/`) for target logic
   - Set leash to harvest location before transitioning to `CollectingDrops`
3. **Add notification flag fields** to `JobComponent` (one `boolean` per event type)
4. **Add a notification action class** in `npc/actions/common/` (sets the flag to `true` only — no logic)
5. **Add a job-specific component** in `components/jobs/` for persisted-only per-worker state
6. **Add utility methods** to a new `utils/XxxUtil.java` for complex job-specific searches
7. **Write the role JSON** in `src/main/resources/Server/NPC/Roles/` as a `Variant` of `Template_Colonist_Base` (see Role JSON file structure below). Custom sensors, actions, and `StateTransitions` go in the template; only job-specific `WaitingForWork` blocks go in per-job `Component_Instruction` files.
8. **Add subpackages** under `npc/actions/` and `npc/sensors/` for the new job's custom building blocks
9. **Register** all components, actions, and sensors in `HytaleColoniesPlugin.java`

---

## Role JSON file structure

All harvester-type colonist roles use a **three-layer hierarchy**:

```
Templates/Template_Colonist_Base.json    (Abstract -- all shared state logic)
  └── Templates/Component_Instruction_Colonist_WaitingForWork_<Job>.json
        (Class:Instruction -- job-specific tool checks + target scanning)
  └── Colonist_<Job>.json                (Variant -- 4 parameter overrides only)
```

### Template_Colonist_Base.json (Abstract)

Parameters exposed to variants:

| Parameter | Default | Purpose |
|---|---|---|
| `NameTranslationKey` | `server.npcRoles.Colonist.name` | NPC display name |
| `Appearance` | `Mannequin` | Model |
| `MaxHealth` | `20` | HP |
| `MaxSpeed` | `3` | Walk speed |
| `GatherType` | `Rocks` | Passed to `EquipBestTool` on entering Working |
| `DebugCategory` | `COLONIST_JOB` | Category string for all `LogDebug` actions |
| `WaitingForWorkComponent` | `Component_Instruction_Colonist_WaitingForWork_Miner` | Per-job WaitingForWork block |

The template owns all state machine instructions **except** `WaitingForWork`, which it delegates via `{ "Reference": { "Compute": "WaitingForWorkComponent" } }`.

### Component_Instruction_Colonist_WaitingForWork_<Job>.json (Class: Instruction)

Located in `Templates/`. Contains the **full** `WaitingForWork` instruction node (outer sensor + inner instructions = tool checks, tool-specific scan action, `JobTargetExists` check). Each job has its own file with hardcoded `Category` strings.

### Colonist_<Job>.json (Variant)

Only four parameter overrides needed:
```json
{
  "Type": "Variant",
  "Reference": "Template_Colonist_Base",
  "Parameters": {
    "NameTranslationKey": { "Value": "...", "Description": "..." },
    "GatherType":          { "Value": "Rocks|Woods|...", "Description": "..." },
    "DebugCategory":       { "Value": "MINER_JOB|WOODSMAN_JOB|...", "Description": "..." },
    "WaitingForWorkComponent": { "Value": "Component_Instruction_Colonist_WaitingForWork_<Job>", "Description": "..." }
  }
}
```

`Colonist_Constructor.json` remains a flat `Generic` because its Working sub-states (Clearing, Constructing, RetrievingBlocks) do not match the harvester template pattern.

`Colonist_Dummy.json` is a standalone `Generic` with simple idle wander — it is not a variant and does not reference the template.

### Adding a new harvester job (JSON checklist)

1. Create `Templates/Component_Instruction_Colonist_WaitingForWork_<Job>.json` with the job-specific `WaitingForWork` block (wander, tool checks, scan action, `JobTargetExists` → `TravelingToWorkSite`).
2. Create `Colonist_<Job>.json` as a `Variant` of `Template_Colonist_Base` with the four parameter overrides.
3. The template handles all other states automatically.

---

## Known pitfalls

| Pitfall | What goes wrong | Prevention |
|---|---|---|
| Setting `JobState` from JSON | Desync — JSON fires on different tick than ECS reads | Never call `SetEcsJobState` from JSON |
| `Once: true` + same-tick ECS check | Flag fires, ECS reads before propagation or after clearOnce, silent no-op | Move state change to ECS handler, not JSON entry action |
| `ActionsBlocking` containing bridge actions | Blocking pipeline freezes NPC when ECS state changes | Only use `ActionsBlocking` for pure behavior sequences |
| Storing runtime flags in per-job component instead of `JobComponent` | Extra component, harder to access from sensors | Keep all transient job flags on `JobComponent` |
| Using the 2s job system for reactive working-state detection | 2-second lag before events are processed | Use `EntityTickingSystem` filtered to `Working` state |
| Comparing server log timestamps to local file timestamps | Hytale server logs are in **UTC**. Local system time may differ by hours. Always convert before comparing. | Use `(Get-Date).ToUniversalTime()` or compare log UTC timestamps to UTC build times |
| Wrapping each state in a separate outer `{ "Instructions": [...] }` (no sensor, no `Continue`) | First wrapper always matches (no sensor = always true), stops all subsequent siblings — only the first state's sensor is ever evaluated | Put `Continue: true` and the `State` sensor directly on each top-level instruction; no outer wrappers |
| Global `Continue: true` Seek at top of instruction list | The global Seek fires every tick regardless of state, preventing state-local wander motions from being reached before it | Put Seek inside each travel-state block; use the NPC leash point for wander anchoring |
| Using `"Type": "State"` sensor without `"IgnoreMissingSetState": true` when ECS drives the state | NPC validator throws SEVERE at startup: "State sensor or State setter action/motion exists without accompanying state/setter" — role is rejected entirely and `Unknown NPC role` errors repeat every tick | Always add `"IgnoreMissingSetState": true` to every externally-driven state sensor |
| Adding `StateTransitions` `From`/`To` states hoping they satisfy the validator | `StateTransitions` only calls `registerStateRequirer()`, never `registerStateSensor()` or `registerStateSetter()` — it cannot satisfy the XOR check | Use `"IgnoreMissingSetState": true` on the sensor instead |
| Custom action in `StateTransitions` returns `false` | Transition never completes; `rootInstruction.execute()` is skipped every tick — NPC instruction body never runs. | Actions in `StateTransitions` must always return `true`. Conditional logic belongs in the instruction body. |
| `WanderInCircle` wandering to wrong location | `WanderInCircle` wanders around `NPCEntity.getLeashPoint()`, not the NPC's current position. Defaults to spawn point. | Set leash via `ColonistLeashUtil` when entering each state that uses wander |
| Computable `Reference` missing `Interfaces` | NPC validator rejects the template at startup: "Computable references must define a list of 'Interfaces' to control which components can be attached." — all Variants of the template also fail with "Reference to unknown builder". | Always add `"Interfaces": ["YourNamespace.InterfaceName"]` to every `{ "Reference": { "Compute": "..." } }` node. The component file must also declare `"Interface": "YourNamespace.InterfaceName"`. Interface names are **pure string equality** — any custom namespace (e.g. `HytaleColonies.*`) works. **Do NOT add `"Nullable": true`** — see warning below. |
| `"Nullable": true` on a computable `Reference` node | Instructions inside the resolved component silently skip for the first ~60 seconds after the NPC enters that state. The Reference appears to resolve lazily; `Nullable: true` treats the not-yet-resolved reference as absent and silently skips it on every tick until something (e.g., an inventory change event) forces resolution. Symptom: `ActionsBlocking` instructions with sensors (especially `Inventory` sensors) never fire during the initial period; only start firing after the first inventory event. | **Omit `"Nullable": true` entirely.** If the component can't be resolved, a visible error is preferable to silent behavioral failures. |
| Missing `"Type": "Component"` on a component file | Log: `Unknown JSON attribute 'Content' found in Instruction|???` — the `Instruction` factory defaults to `BuilderInstruction` which ignores `Content`. The component loads as an empty builder; interface validation on the referencing template then fails, cascading to all Variants. | Component files that use a `Content` wrapper **must** have `"Type": "Component"` at the root (alongside `"Class"` and `"Interface"`). Without it, `Content` is silently ignored. |
| Multiple `BodyMotion` entries in sibling instructions with `Continue: true` | Interaction between multiple `BodyMotion` definitions and `Continue` is not fully verified — behaviour may be unexpected | Keep at most one `BodyMotion` per instruction block; use `Continue: true` only for passing through to actions in subsequent siblings |
