---
name: hytalecolonies-npc-design
version: 3
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

`JobComponent.jobState` (`JobState` enum):

```
Idling ──► TravelingToJob ──► Working ──► CollectingDrops ──► DeliveringItems ──► TravelingHome ──► Idling
```

### State semantics

| State | Meaning | Who transitions away | Leash point set by ECS |
|---|---|---|---|
| `Idling` | At workstation, waiting or missing tools | ECS — claims target → `TravelingToJob` | Workstation block centre |
| `TravelingToJob` | Walking to claimed target | ECS — arrived → `Working` | (unchanged) |
| `Working` | Actively performing task (mining, chopping) | ECS — on notification → next state | (unchanged) |
| `CollectingDrops` | Picking up drops at harvest site | ECS — timer → `DeliveringItems` | Last harvested block centre |
| `DeliveringItems` | Walking to container and depositing | ECS — delivered → `TravelingHome` | (unchanged) |
| `TravelingHome` | Returning to workstation | ECS — arrived → `Idling` | (unchanged) |

`Sleeping` and `Recharging` are reserved for future use.

---

## System responsibilities

### Main job system (`DelayedEntitySystem`, ~2s cadence)

Handles low-latency-tolerant decisions:
- `Idling`: scan for targets, claim, write navigation target, transition to `TravelingToJob`. When no work is found sets `workAvailable = false` and stays in `Idling`.
- Shared delivery pipeline: `CollectingDrops` → `DeliveringItems` → `TravelingHome`

### Per-job fast-tick system (`EntityTickingSystem`)

**Required for any job type that needs reactive mid-task detection.**
Query-filtered to colonists in `Working` state only.

- Read and clear notification flags from `JobComponent`
- Apply game-logic decisions (quota check, next target selection, etc.)
- Set `JobState` transition via `CommandBuffer`

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
    "Sensor": { "Type": "State", "State": "TravelingToJob", "IgnoreMissingSetState": true },
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
    "$Comment": "Idling: wander around leash point (set by ECS to workstation) + tool-check particle.",
    "Continue": true,
    "Sensor": { "Type": "State", "State": "Idling", "IgnoreMissingSetState": true },
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
| `Idling` | `WanderInCircle` (leash → workstation, set by ECS); tool-check particle + wait |
| `TravelingToJob` | Seek NavTarget only |
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
| Entering `Idling` / every idle tick | Workstation block centre |
| Entering `CollectingDrops` (woodsman) | Last harvested tree base block centre |
| Entering `CollectingDrops` (miner) | Last mined block centre |

---

## Adding a new job type checklist

1. Add an `Idle` state handler for the job, registered in the main job system
2. In the idle handler: call `ColonistLeashUtil.setLeashToBlockCenter(ref, store, workStationPos)` to anchor wander to the workstation
3. If reactive mid-task detection is needed: add a fast-tick `EntityTickingSystem` for `Working` state
4. In the working system: call `ColonistLeashUtil.setLeashToBlockCenter(ref, store, lastTargetPos)` before transitioning to `CollectingDrops`
5. Add notification flags to `JobComponent` for each consequential event
6. Add a thin notification action class per flag (sets flag only, no logic), register in the plugin
7. Add a job-specific component for persisted per-worker state only
8. Write the role JSON with native `"Type": "State"` sensors (all with `"IgnoreMissingSetState": true`) and no logic actions

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
| `WanderInCircle` wandering to wrong location | `WanderInCircle` wanders around `NPCEntity.getLeashPoint()`, not the NPC's current position. Defaults to spawn point. | Set leash via `ColonistLeashUtil` when entering each state that uses wander |
| Multiple `BodyMotion` entries in sibling instructions with `Continue: true` | Interaction between multiple `BodyMotion` definitions and `Continue` is not fully verified — behaviour may be unexpected | Keep at most one `BodyMotion` per instruction block; use `Continue: true` only for passing through to actions in subsequent siblings |
