---
name: hytalecolonies-npc-design
version: 11
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

`JobComponent.jobState` (`JobState` enum). Each state belongs to a `JobState.Group` that maps 1-to-1 with the NPC role main-state name. The sub-state set is **open-ended** — new job types add new sub-states by adding enum values and a corresponding sensor block in `Template_Colonist.json`.

```
    Idle (Group.Idle)
      ├── .Default                    entry: navigate to workstation, set TravelingToWorkstation
      ├── .TravelingToWorkstation    walk to workstation usually transitions to WaitingForWork
      └── .TravelingToHome           travel to wherever colonist lives

    Working (Group.Working)
      │
      ├── shared sub-states ──────────────────────────────────────────────────────
      │   .TravelingToWorkSite        walk to location where the actual work happens (e.g. mine block, tree, construction site)
      │   .WaitingForWork             colonist is at workstation but figuring out what to do; wander + scan for targets/wait for work assignment
      │   .CollectingDrops            pick up drops after harvesting (e.g. mined block, chopped tree); 
      │   .DeliveringItems            walk to container near workstation and deposit items
      │
      └── job-specific sub-states (examples; add more per new job type) ─────────
          .Harvesting                 actively performing a resource-gathering task at the work site
          .Clearing                   removing obstacles or blocks from a work area
          .Constructing               actively placing or building something at the work site
          .RetrievingBlocks           fetching required materials from a nearby storage point
          ... future job types add sub-states here

    Recharging (Group.Recharging — reserved)
```

Example working cycle (exact transitions vary by job):

```
Idle.Default → Idle.TravelingToWorkstation → Working.WaitingForWork
  → Working.TravelingToWorkSite → Working.<job sub-state>
  → Working.CollectingDrops → Working.DeliveringItems → Idle.TravelingToHome → Idle
```

### Adding a new sub-state

1. Add an enum value to `JobState` with the correct `group` (`Working` or `Idle`) and a `npcSubState` string matching the dot-prefix name in JSON (e.g. `npcSubState = "MyNewState"` → JSON sensor `".MyNewState"`).
2. Add a sub-state sensor block inside the appropriate main-state gate in `Template_Colonist.json` — with `Continue: true` and `"IgnoreMissingSetState": true`.
3. Add a `NoOp` default or a job-specific body component as needed.

### JobState structure

`JobState` is a single flat enum for codec serialization. The `group` field carries the high-level phase; `npcSubState` is the NPC role sub-state name (`null` = use the group name as the sub-state).

```java
// Check which phase a state belongs to:
if (state.group == JobState.Group.Working) { ... }

// ColonistStateUtil drives the NPC role state machine using these:
state.npcMainState()  // → "Idle", "Working", or "Recharging"
state.npcSubState     // → "Harvesting", "TravelingToWorkSite", null, etc.
```

Do NOT add switch/case on `JobState` values in new code — use `state.group` for phase checks and `state.npcSubState` for NPC role mirroring.

### State semantics

**Shared Idle sub-states**

| Sub-state | Purpose |
|---|---|
| `Idle.Default` | Entry point for the off-shift phase. Typically navigates toward the workstation to begin the shift cycle. |
| `Idle.TravelingToWorkstation` | Colonist is en route to their workstation. |
| `Idle.TravelingToHome` | Colonist is returning to a home or rest point after completing work. |

**Shared Working sub-states**

| Sub-state | Purpose |
|---|---|
| `Working.TravelingToWorkSite` | Colonist is travelling to the location where they will perform their job (block, tree, construction site, etc.). |
| `Working.WaitingForWork` | Colonist is at their workstation but has no assigned work yet — scans for available targets and idles. |
| `Working.CollectingDrops` | Colonist collects items dropped at the work site (e.g. harvested resources). |
| `Working.DeliveringItems` | Colonist delivers collected items to a nearby container. |

**Job-specific Working sub-states** (current examples; more will be added per job type)

| Sub-state | General purpose |
|---|---|
| `Working.Harvesting` | Actively gathering resources at the work site (break blocks, interact with objects, etc.). |
| `Working.Clearing` | Removing blocks or obstacles from a designated area before other work can begin. |
| `Working.Constructing` | Placing blocks or structures at the work site. |
| `Working.RetrievingBlocks` | Fetching required materials from a storage point before continuing construction. |

**Reserved**

| State | Purpose |
|---|---|
| `Sleeping` | Reserved: sleeping at night |
| `Recharging` | Reserved: energy/hunger system |

---

## System responsibilities

### Main job system (`DelayedEntitySystem`, ~2s cadence)

Handles decisions that do not need sub-second precision: scanning for available work, claiming targets, updating navigation and leash positions, managing shared pipeline transitions (delivery, off-shift travel), and advancing colonists that are waiting for conditions to change.

> Do not use the 2s cadence system for events that must be detected mid-task (e.g. a block breaking, a quota being reached). The lag is unacceptable. Use a per-tick system instead.

### Per-job fast-tick system (`EntityTickingSystem`)

**Required for any job type that reacts to events that happen while in the `Working` state.**
Query-filtered to colonists in the `Working` group only.

- Read and clear notification flags from `JobComponent` on every tick
- Apply game-logic decisions and write `JobState` transitions via `CommandBuffer`
- Job-specific logic (next target selection, quota checks, progress tracking) belongs here, not in the 2s system

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

All colonist roles are `Variant`s of `Template_Colonist`. The template owns the entire instruction tree. Every state/sub-state sensor lives **directly in the template** so that ECS `role.getStateSupport().setState()` can reach it. Components inject only leaf instruction bodies.

The template structure:
1. **`Working` main-state block** (`Continue: true`): gates all working-shift sub-states. Each sub-state is an inner child also with `Continue: true` and its own dot-prefix `State` sensor. Sub-states that need two tick-level concerns (action loop + event notification) use two consecutive inner blocks with the **same sub-state sensor**.
2. **`Idle` main-state block** (`Continue: true`): gates all idle sub-states.
3. **Final `Any` + `BodyMotion:Nothing` fallback** at the outermost level.
4. **`StateTransitions`** for cosmetic/cleanup on main-state entry/exit (tool clear on leave `Working`, `ResetInstructions` on enter `Idle`).
5. **No bridge actions that make game-logic decisions** — JSON may only set notification flags.

**Sensor placement rule**: `Continue: true` and the `State` sensor go **directly on each instruction** — never wrap states in a sensorless outer `{ "Instructions": [...] }`. A sensorless node always matches and stops sibling evaluation, so only the first state's sensor would ever fire.

### State sensor pattern

All state sensors in colonist roles use the **native `"Type": "State"` sensor** with `"IgnoreMissingSetState": true`. This flag registers a no-op dummy setter alongside the sensor, satisfying the NPC validator's XOR bidirectionality check (every sensor must have a paired setter and vice versa) without requiring actual setter actions in JSON. ECS drives all state changes externally via `ColonistStateUtil.setJobState()`.

```json
{ "Type": "State", "State": "Working", "IgnoreMissingSetState": true }
{ "Type": "State", "State": ".Harvesting", "IgnoreMissingSetState": true }
```

**Never omit `IgnoreMissingSetState`** on an externally-driven state sensor. The validator will reject the role at startup with `"State sensor or State setter action/motion exists without accompanying state/setter"`, and `Unknown NPC role` errors will repeat every tick.

**`StateTransitions` does not satisfy the validator.** Its `From`/`To` entries call `registerStateRequirer()` only — they never register a sensor or setter pair. A role that relies on `StateTransitions` alone for a state will still fail validation.

**Actions in `StateTransitions` must always return `true`.** An action that returns `false` causes the transition to never complete and `rootInstruction.execute()` is skipped every tick — the NPC's instruction body never runs.

**Never use `"Type": "EcsJobState"` custom sensors** — that was an early workaround and has been removed.

### Role JSON skeleton

The template uses two main-state gates (`Working`, `Idle`) at the top level, each with sub-state inner blocks. Sub-states that need two tick-level concerns (loop + notify) use two consecutive siblings with the same sub-state sensor and `Continue: true`. Each leaf instruction body is injected via a `{ "Reference": { "Compute": "ParamName" }, "Interfaces": ["..."] }` node.

```json
"Instructions": [
  {
    "$Comment": "Working -- gates all working-shift sub-states.",
    "Continue": true,
    "Sensor": { "Type": "State", "State": "Working", "IgnoreMissingSetState": true },
    "Instructions": [
      {
        "$Comment": "Harvesting #1 -- job-specific seek + break loop.",
        "Continue": true,
        "Sensor": { "Type": "State", "State": ".Harvesting", "IgnoreMissingSetState": true },
        "Instructions": [
          { "Reference": { "Compute": "HarvestingComponent" }, "Interfaces": ["HytaleColonies.Instruction.Colonist.StateBody"] }
        ]
      },
      {
        "$Comment": "Harvesting #2 -- block-broken notification to ECS.",
        "Continue": true,
        "Sensor": { "Type": "State", "State": ".Harvesting", "IgnoreMissingSetState": true },
        "Instructions": [
          { "Sensor": { "Type": "JobTargetBroken" }, "Actions": [ { "Type": "NotifyBlockBroken" } ] }
        ]
      },
      {
        "$Comment": "TravelingToWorkSite -- job-specific seek + arrival transition.",
        "Continue": true,
        "Sensor": { "Type": "State", "State": ".TravelingToWorkSite", "IgnoreMissingSetState": true },
        "Instructions": [
          { "Reference": { "Compute": "TravelingToWorkSiteComponent" }, "Interfaces": ["HytaleColonies.Instruction.Colonist.StateBody"] }
        ]
      },
      { "$Comment": "... additional job-specific or shared Working sub-states follow the same pattern ..." },
      { "Sensor": { "Type": "Any" }, "BodyMotion": { "Type": "Nothing" } }
    ]
  },
  {
    "$Comment": "Idle -- gates all off-shift sub-states.",
    "Continue": true,
    "Sensor": { "Type": "State", "State": "Idle", "IgnoreMissingSetState": true },
    "Instructions": [
      {
        "$Comment": "TravelingToWorkstation -- seek + arrive -> WaitingForWork.",
        "Continue": true,
        "Sensor": { "Type": "State", "State": ".TravelingToWorkstation", "IgnoreMissingSetState": true },
        "Instructions": [
          { "Continue": true, "Sensor": { "Type": "Any" }, "Actions": [{ "Type": "NavigateToWorkstation" }] },
          { "Continue": true, "Sensor": { "Type": "ReadPosition", "Slot": "NavTarget", "Range": 200.0, "MinRange": 1.0 }, "BodyMotion": { "Type": "Seek", "StopDistance": 0.5, "SlowDownDistance": 4, "RelativeSpeed": 1.0 } },
          { "Sensor": { "Type": "AtWorkstation" }, "Actions": [{ "Type": "SetEcsJobState", "JobState": "WaitingForWork" }] }
        ]
      },
      {
        "$Comment": "Default -- entry idle state body.",
        "Sensor": { "Type": "State", "State": ".Default", "IgnoreMissingSetState": true },
        "Instructions": [
          { "Reference": { "Compute": "DefaultIdleComponent" }, "Interfaces": ["HytaleColonies.Instruction.Colonist.StateBody"] }
        ]
      },
      { "$Comment": "... additional Idle sub-states follow the same pattern ..." },
      { "Sensor": { "Type": "Any" }, "BodyMotion": { "Type": "Nothing" } }
    ]
  },
  { "Sensor": { "Type": "Any" }, "BodyMotion": { "Type": "Nothing" } }
]
```

> **Leaf body approach**: Each sub-state that does Seek + work has a `ReadPosition NavTarget` Seek with `Continue: true` as the first inner instruction, before the work action. This closes the final gap after arriving from `TravelingToWorkSite`. Without it the NPC stands still wherever the travel Seek stopped.

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

Keep all transient notification flags on `JobComponent` — not on per-job components. Sensors in the NPC role have direct access to `JobComponent` fields; scattering flags across job-specific components makes them harder to read and clear.

### Per-job component

Carries only persisted per-worker state meaningful across restarts (e.g. progress counters that prevent over-work after restart).

Does not carry: flags, counters derivable from workstation config, or transient runtime state.

### Workstation component

Single source of truth for all job configuration. Never hard-code these values.

### Instruction component authoring rules

Components (`"Type": "Component", "Class": "Instruction"`) inject leaf instruction bodies into the template. Rules:

- **No `"Type": "State"` sensors inside a component.** Component-owned `State` sensors create component-local states in a separate `componentLocalStateMachines` map. ECS `role.getStateSupport().setState(mainState, subState)` can only reach states in the role's main state machine — it cannot reach component-local states. Symptom: `"State 'Idle.TravelingToWorkstation' does not exist and was set by an external call"` repeating in logs; NPC never acts. The fix is always to move the sensor into the template.
- **Always declare `"Interface"`** at the component root (e.g. `"Interface": "HytaleColonies.Instruction.Colonist.StateBody"`). Every `{ "Reference": { "Compute": "..." } }` node in the template must declare `"Interfaces": ["..."]` with the matching name. Interface names are pure string equality — any custom namespace works.
- **Never add `"Nullable": true`** to a computable `Reference` node. It causes the component to be silently skipped for up to ~60 seconds after the NPC enters the state (lazy resolution treats the not-yet-resolved reference as absent). A visible error on startup is preferable to silent behavioral failures.
- **Always include `"Type": "Component"`** at the component root alongside `"Class"` and `"Interface"`. Without it the `Instruction` factory ignores the `Content` wrapper and the component loads empty, cascading failures to all Variants that reference it.

---

## Leash point conventions

`WanderInCircle` constrains wander to a circle around `NPCEntity.getLeashPoint()` — **not** the NPC's current position. The leash point defaults to the NPC's spawn position. ECS is responsible for updating it whenever the wander anchor changes. Use `ColonistLeashUtil` for all leash writes:

```java
// Set leash to workstation (call when entering an idle/waiting state):
ColonistLeashUtil.setLeashToBlockCenter(ref, store, workStationPos);

// Set leash to work site (call before entering a wander-based collection state):
ColonistLeashUtil.setLeashToBlockCenter(ref, store, workSitePos);
```

Failing to update the leash causes the NPC to wander around its original spawn point rather than the expected location.

| When | Leash set to |
|---|---|
| Entering an idle/waiting state near the workstation | Workstation block centre |
| Entering a wander-based collection state at a work site | Work site block centre |

---

## Adding a new job type checklist

1. **Extend the main job system** to handle the new job's idle-phase decisions: scanning for work targets, claiming them, setting navigation and leash positions.
2. **Add a per-tick `EntityTickingSystem`** if the job produces mid-task events (block broken, quota hit, placement complete). Filter the query to entities in the `Working` group with the job's component. Read and clear flags each tick.
3. **Add notification flag fields** to `JobComponent` (one `boolean` per distinct event type).
4. **Add notification action classes** that set a flag to `true` only — no logic. Register them in `HytaleColoniesPlugin.java`.
5. **Add a job-specific component** in `components/jobs/` for state that needs to survive server restarts. Transient runtime state stays on `JobComponent`.
6. **Add utility classes** under `utils/` for complex job-specific logic (target search, progress tracking, etc.).
7. **Write the role JSON** as a `Variant` of `Template_Colonist` in `src/main/resources/Server/NPC/Roles/`. Use `"Modify"` to override the relevant component parameters. All state/sub-state sensors are already declared in the template.
8. **Add subpackages** under `npc/actions/` and `npc/sensors/` for any new custom building blocks.
9. **Register** all new components, actions, and sensors in `HytaleColoniesPlugin.java`.

---

## Role JSON file structure

All colonist roles use a **two-layer hierarchy**:

```
Templates/Template_Colonist.json         (Abstract -- owns ALL state/sub-state sensors)
  ├── Templates/Component_Instruction_Harvesting_<Job>.json
  ├── Templates/Component_Instruction_WaitingForWork_<Job>.json
  ├── Templates/Component_Instruction_TravelingToWorkSite_<Variant>.json
  ├── Templates/Component_Instruction_Idle_Default_<Variant>.json
  └── ... (other sub-state body components as needed)
  └── Colonist_<Job>.json                (Variant -- parameter overrides only)
```

### Template_Colonist.json (Abstract)

The template owns **all** `State` sensors for both main states (`Working`, `Idle`) and all sub-states. Components are referenced for leaf instruction bodies only — no `State` sensors inside components.

Parameters exposed to variants:

| Parameter | Default | Purpose |
|---|---|---|
| `NameTranslationKey` | `server.npcRoles.Colonist.name` | NPC display name |
| `Appearance` | `Mannequin` | Model |
| `MaxHealth` | `20` | HP |
| `MaxSpeed` | `3` | Walk speed |
| `DebugCategory` | `COLONIST_JOB` | Category string for `LogDebug` in `StateTransitions` |
| `HarvestingComponent` | `Component_Instruction_Harvesting_NoOp` | Body for `.Harvesting` sub-state |
| `WaitingForWorkComponent` | `Component_Instruction_WaitingForWork_Miner` | Body for `.WaitingForWork` sub-state |
| `TravelingToWorkSiteComponent` | `Component_Instruction_TravelingToWorkSite_Harvester` | Body for `.TravelingToWorkSite` sub-state |
| `ClearingComponent` | `Component_Instruction_NoOp` | Body for `.Clearing` sub-state |
| `ConstructingComponent` | `Component_Instruction_NoOp` | Body for `.Constructing` sub-state |
| `RetrievingBlocksComponent` | `Component_Instruction_NoOp` | Body for `.RetrievingBlocks` sub-state |
| `DefaultIdleComponent` | `Component_Instruction_Idle_Default_Worker` | Body for `.Default` idle sub-state |

### Component interfaces

| Interface | Used for |
|---|---|
| `HytaleColonies.Instruction.Colonist.StateBody` | All leaf body components (Harvesting, TravelingToWorkSite, Clearing, Constructing, RetrievingBlocks, Idle_Default, NoOp) |
| `HytaleColonies.Instruction.Colonist.WaitingForWork` | WaitingForWork components (tool checks + scan) |

Every `{ "Reference": { "Compute": "..." } }` node in the template **must** declare `"Interfaces": ["HytaleColonies.Instruction.Colonist.StateBody"]` (or `WaitingForWork` for that slot). Component files must declare the matching `"Interface": "..."` field.

### Colonist_<Job>.json (Variant)

Only parameter overrides needed. Use `"Modify"` — **not** `"Parameters"`:
```json
{
  "Type": "Variant",
  "Reference": "Template_Colonist",
  "Modify": {
    "NameTranslationKey": "server.npcRoles.Colonist_Miner.name",
    "DebugCategory": "MINER_JOB",
    "HarvestingComponent": "Component_Instruction_Harvesting_Miner",
    "WaitingForWorkComponent": "Component_Instruction_WaitingForWork_Miner",
    "TravelingToWorkSiteComponent": "Component_Instruction_TravelingToWorkSite_Harvester"
  }
}
```

> **`"Modify"` vs `"Parameters"`**: `"Modify"` is the correct section for a `Variant` to override values in its base template. `"Parameters"` only populates the variant's *own* scope and has no effect on the base template. Using `"Parameters"` in a `Variant` silently has no effect — all base template parameters remain at their defaults.

All roles (Miner, Woodsman, Constructor, Jobless) are `Variant`s of `Template_Colonist`. `Colonist_Jobless.json` only overrides `NameTranslationKey` and `DefaultIdleComponent` (→ wander body).

### Adding a new harvester job (JSON checklist)

1. Create `Templates/Component_Instruction_WaitingForWork_<Job>.json` — interface `HytaleColonies.Instruction.Colonist.WaitingForWork`. Implements the idle scanning behavior (wander near workstation, check for valid targets, transition to `TravelingToWorkSite` when one is found).
2. Create `Templates/Component_Instruction_Harvesting_<Job>.json` — interface `HytaleColonies.Instruction.Colonist.StateBody`. Implements the active work loop for this job (seek target, perform action, notify ECS of completion).
3. Create or reuse a `TravelingToWorkSite` component appropriate for this job's arrival behavior.
4. Create `Colonist_<Job>.json` as a `Variant` of `Template_Colonist` with `"Modify"` overriding the relevant component parameters.
5. Sub-states the job does not use default to `NoOp` and are no-ops automatically.

---

## Known pitfalls

The items below are non-obvious operational gotchas. Structural design rules (state sensors, component authoring, leash, system cadence) are covered in their respective sections above.

| Pitfall | What goes wrong | Prevention |
|---|---|---|
| Building to validate JSON changes | Build shows `BUILD SUCCESSFUL` even when JSON has runtime errors — the server only logs role parse failures at startup. | Reload JSON via the **asset editor** in-game (no restart needed) and watch the server log for SEVERE/WARNING errors. |
| Comparing server log timestamps to local file timestamps | Server logs are in **UTC**; local system time may differ by hours. | Use `(Get-Date).ToUniversalTime()` or compare log timestamps to UTC build times. |
| `Once: true` on a sensor + same-tick ECS read | Flag fires, ECS reads before propagation or after `clearOnce`, silent no-op. | Put state transitions in the ECS handler, not JSON entry actions. |
| `ActionsBlocking` containing actions that call ECS or read game state | Blocking pipeline freezes the NPC when ECS changes state mid-sequence. | Use `ActionsBlocking` only for pure behavior sequences (equip → swing → timeout). |
| Multiple `BodyMotion` on siblings with `Continue: true` | The last `setNextBodyMotionStep` call wins — the second `BodyMotion` silently overrides the first. | Keep at most one `BodyMotion` per logical instruction block. |
