---
name: hytalecolonies-npc-design
version: 2
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
| `JobComponent.jobState` | JSON gates all behavior on `EcsJobState` sensors |
| Navigation target position | ECS writes the world position; JSON executes `Seek` toward it |

ECS sets the state and the destination. JSON drives the body.

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
Idle ──► TravelingToJob ──► Working ──► CollectingDrops ──► DeliveringItems ──► TravelingHome ──► Idle
  │                                                                                               ▲
  └──► NoWork ─────────────────────────────────────────────────────────────────────────────────-─┘
                                  (when new work assigned externally)
```

### State semantics

| State | Meaning | Who transitions away |
|---|---|---|
| `Idle` | At workstation, waiting for a claimable target | ECS — claims target → `TravelingToJob` |
| `TravelingToJob` | Walking to claimed target | ECS — arrived → `Working` |
| `Working` | Actively performing task (mining, chopping) | ECS — on notification → next state |
| `CollectingDrops` | Waiting at drop site for items to settle | ECS — timer → `DeliveringItems` |
| `DeliveringItems` | Walking to container and depositing | ECS — delivered → `TravelingHome` |
| `TravelingHome` | Returning to workstation | ECS — arrived → `Idle` |
| `NoWork` | No targets available, resting at workstation | ECS — new work available → `Idle` |

`Sleeping` and `Recharging` are reserved for future use.

---

## System responsibilities

### Main job system (`DelayedEntitySystem`, ~2s cadence)

Handles low-latency-tolerant decisions:
- `Idle`: scan for targets, claim, write navigation target, transition to `TravelingToJob`
- `NoWork`: re-check for available work each cycle
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
1. **Global navigation** (always `Continue: true`): read navigation target position → `Seek`
2. **Per-ECS-state behavior blocks**: each gated on `EcsJobState`
3. **No role-level `StateTransitions`** unless purely cosmetic (e.g. animation resets)
4. **No bridge actions that make game-logic decisions** — JSON may only call notification flag setters

### Permitted in JSON per state

| ECS State | Permitted JSON behavior |
|---|---|
| `Idle` | Tool-check particle + wait; slow wander near workstation |
| `TravelingToJob` | Seek NavTarget only |
| `Working` | Approach/act/reposition sub-states; tool equip → swing → cooldown loop; notification action on task event |
| `CollectingDrops` | Wander + `PickUpItem` |
| `DeliveringItems` | Seek NavTarget only |
| `TravelingHome` | Seek NavTarget only |
| `NoWork` | Idle/rest animation; gentle wander |

### JSON sub-states

Sub-states within an ECS state block are fine for rich behavior sequences. Rules:
- Must be entered and exited within the same ECS state
- Must not set `JobState` or call any bridge action other than notification flags
- ECS must always be able to interrupt them by changing `JobState`

### What JSON must never do

- Decide when to transition `JobState`
- Call `SetEcsJobState` or any action that performs game-logic computations
- Gate core behavior on role-level state when an `EcsJobState` sensor exists

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

## Adding a new job type checklist

1. Add an `Idle` state handler for the job, registered in the main job system
2. If reactive mid-task detection is needed: add a fast-tick `EntityTickingSystem` for `Working` state
3. Add notification flags to `JobComponent` for each consequential event
4. Add a thin notification action class per flag (sets flag only, no logic), register in the plugin
5. Add a job-specific component for persisted per-worker state only
6. Write the role JSON with `EcsJobState`-gated behavior and no logic actions

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
