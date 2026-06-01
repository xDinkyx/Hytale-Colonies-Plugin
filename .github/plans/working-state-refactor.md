# Working State Refactor Plan

## Goal

Replace the current sprawling Working sub-state tree (6 parameters, 8 template blocks, job-specific NoOp
proliferation) with a clean, linear, shared pipeline that every job type flows through.

## Target Working State Flow

```
WaitingForWork          scan for work; simple wander only (no tool checks here)
ClaimWork               batch-claim planned blocks; defer to arrival for interactables
RetrievingItems         pick up required items (tools + job materials) from linked container;
                        wait for player to stock container if items are missing
TravelingToWorkSite     seek to work site
PerformWork             unified block-removal/placement/interaction loop (job-specific body)
WaitingForClaimRelease  wait for another colonist to release an interactable block claim
DeliveringItems         deposit items not in defaultRequiredItems back to container
  --> loop back to WaitingForWork
```

## Key Design Decisions

- **No separate Harvesting sub-state.** Harvesting and Clearing are functionally identical
  (both break blocks). Both are covered by `PerformWork`. A future `Harvesting` sub-state can
  be added for farmer/gatherer jobs that interact with crops differently.
- **`Working` enum renames to `PerformWork`** (npcSubState `"Harvesting"` -> `"PerformWork"`).
- **Item requirements are JSON-configurable.** Each workstation JSON defines a
  `DefaultRequiredItems` field (array of item ID patterns). Both `RetrievingItems` and
  `DeliveringItems` read from this single source -- no duplication in code.
- **RetrievingItems is dynamic.** Fixed required items come from `DefaultRequiredItems`.
  Constructor also retrieves the specific blocks for its current build queue (determined when
  the queue is planned). If the container cannot satisfy the request, the colonist idles and
  waits -- surfacing a "needs items" signal for the player.
- **WaitingForWork simplified.** Tool checks move to `RetrievingItems`. `WaitingForWork` only
  wanders and looks for available targets, then signals ECS.

---

## Steps (least to most impactful)

---

### Step 1 -- Rename `RetrievingBlocks` -> `RetrievingItems`

Pure rename. Zero behavior change. Establishes the correct name before any new logic.

**Java**
- `JobState.WorkingRetrievingBlocks` -> `WorkingRetrievingItems`
- `npcSubState` value: `"RetrievingBlocks"` -> `"RetrievingItems"`
- All references in `ConstructorWorkingSystem` and `ConstructorJobCheckSystem`

**JSON**
- `Template_Colonist.json`: parameter `RetrievingBlocksComponent` -> `RetrievingItemsComponent`;
  sub-state sensor string `"RetrievingBlocks"` -> `"RetrievingItems"`
- `Component_Instruction_RetrievingBlocks_Constructor.json` -> `Component_Instruction_RetrievingItems_Constructor.json`
  (update internal log messages)
- `Colonist_Constructor.json` Modify: `RetrievingBlocksComponent` -> `RetrievingItemsComponent`

---

### Step 2 -- Add `ClaimWork` sub-state (additive)

New state only. No existing behavior removed. All jobs get a NoOp initially.

**Java**
- Add `JobState.ClaimWork` (Group.Working, npcSubState `"ClaimWork"`)
- ECS: working systems route `WaitingForWork` -> `ClaimWork` -> `RetrievingItems`
  (currently `WaitingForWork` -> `TravelingToWorkSite`; tool-check-driven `TravelingToWorkSite`
  triggers remain, just routed through the new state)
- Block-claiming logic currently scattered in job check systems begins migrating here in a
  follow-up; for now a flag on `JobComponent` (`claimsPending`) can signal ECS to advance

**JSON**
- `Template_Colonist.json`: add `ClaimWorkComponent` parameter (default =
  `Component_Instruction_ClaimWork_NoOp`); add `Continue:true` sensor block inside Working
- New `Component_Instruction_ClaimWork_NoOp.json`: fires `SetEcsJobState RetrievingItems`
  immediately (pass-through until real claiming is wired)

---

### Step 3 -- Add `WaitingForClaimRelease` sub-state (additive)

New state only. No existing flow broken. Future-facing for interactable-block jobs.

**Java**
- Add `JobState.WaitingForClaimRelease` (Group.Working, npcSubState `"WaitingForClaimRelease"`)
- ECS: on arrival at interactable target that is already claimed by another colonist,
  transition to `WaitingForClaimRelease`; on claim-released event, transition to `PerformWork`

**JSON**
- `Template_Colonist.json`: add `WaitingForClaimReleaseComponent` parameter (default =
  `Component_Instruction_WaitingForClaimRelease_Default`); add sensor block
- New `Component_Instruction_WaitingForClaimRelease_Default.json`: wander in small circle
  near target while waiting; no transition action (ECS drives the exit)

---

### Step 4 -- Add `DefaultRequiredItems` to workstation JSON config (data model)

No behavior change yet. Establishes the shared data source for Steps 5 and 6.

**Java**
- Add `List<String> defaultRequiredItems` (item ID patterns) to `WorkStationComponent` (or
  to each per-job workstation component if requirements differ significantly per job)
- Codec: `BuilderCodec` field with `KeyedCodec` + `ArrayCodec<String>`

**JSON (workstation config examples)**
- Miner workstation: `"DefaultRequiredItems": ["Tool_Pickaxe_*", "Tool_Shovel_*"]`
- Woodsman workstation: `"DefaultRequiredItems": ["Tool_Hatchet_*"]`
- Constructor workstation: `"DefaultRequiredItems": ["Tool_Pickaxe_*", "Tool_Shovel_*", "Tool_Hatchet_*"]`
  (build blocks are dynamic -- resolved at planning time, not listed here)

**Both** `RetrieveConstructionBlocks` and `DepositItems` NPC actions are updated to read from
this field so they share the same definition.

---

### Step 5 -- Generalize `RetrievingItems` for all jobs

Extends the existing state so every job uses it. Moves tool checks out of `WaitingForWork`.

**Java**
- New `ActionRetrieveJobItems` NPC action (shared):
  - Reads `defaultRequiredItems` from workstation component
  - Constructor: also appends build-queue block types to the required list
  - Checks colonist inventory for each required item pattern
  - If missing: seeks linked container, retrieves from it
  - If container cannot satisfy: stays in `RetrievingItems`, sets a `waitingForItems` flag
    on `JobComponent` (player-facing signal)
  - On success: fires `SetEcsJobState TravelingToWorkSite`
- `JobComponent`: add transient `boolean waitingForItems` notification field

**JSON**
- New `Component_Instruction_RetrievingItems_Default.json` (shared): seek container,
  call `RetrieveJobItems`, wait loop with `WanderInCircle` while `waitingForItems`
- Miner and Woodsman roles override `RetrievingItemsComponent` with this shared component
- Tool-check confused-particle blocks removed from `WaitingForWork_Miner.json`,
  `WaitingForWork_Woodsman.json`, `WaitingForWork_Constructor.json`
- `WaitingForWork` components simplified to: wander + scan + transition to `ClaimWork`

---

### Step 6 -- Unify `PerformWork` (biggest refactor)

Consolidates `Working` (Harvesting), `WorkingClearing`, and `WorkingConstructing` into one
sub-state. Affects template, all role JSONs, and ECS working systems.

**Java**
- `JobState`: remove `Working` (npcSubState `"Harvesting"`), `WorkingClearing`, `WorkingConstructing`
- `JobState`: rename/add `PerformWork` (Group.Working, npcSubState `"PerformWork"`)
- All working systems: replace references to the three removed states with `PerformWork`
- `ConstructorWorkingSystem`: single `PerformWork` state handles both clearing and building
  sub-cycles (ECS flag on `ConstructorJobComponent` determines which sub-action to advance)
- `ConstructorJobComponent`: rename `clearingBlockBrokenNotification` ->
  `blockRemovedNotification`; unify with construction flag under `PerformWork`

**JSON -- Template_Colonist.json**
- Remove parameters: `HarvestingComponent`, `ClearingComponent`, `ConstructingComponent`
- Remove sub-state blocks: `Harvesting #1`, `Clearing #1+#2`, `Constructing` (5 blocks removed)
- Add parameter: `PerformWorkComponent` (default = `Component_Instruction_PerformWork_NoOp`)
- Add single sub-state block for `PerformWork` (with parallel notification block if needed)
- Total Working sub-state blocks after: 7 (WaitingForWork, ClaimWork, RetrievingItems,
  TravelingToWorkSite, PerformWork, WaitingForClaimRelease, DeliveringItems) + Any fallback

**JSON -- Per-job components (new)**
- `Component_Instruction_PerformWork_Miner.json` (current Harvesting_Miner body, unchanged logic)
- `Component_Instruction_PerformWork_Woodsman.json` (current Harvesting_Woodsman body)
- `Component_Instruction_PerformWork_Constructor.json` (current Clearing body; build blocks
  are placed via ECS-driven transitions within `PerformWork` without a separate sub-state)
- `Component_Instruction_PerformWork_NoOp.json` (for Jobless and future jobs)

**JSON -- Role files**
- `Colonist_Miner.json`: replace `HarvestingComponent` with `PerformWorkComponent`
- `Colonist_Woodsman.json`: replace `HarvestingComponent` with `PerformWorkComponent`
- `Colonist_Constructor.json`: replace `ClearingComponent` + `ConstructingComponent` with
  `PerformWorkComponent`

**JSON -- Delete old components**
- `Component_Instruction_Harvesting_Miner.json`
- `Component_Instruction_Harvesting_Woodsman.json`
- `Component_Instruction_Harvesting_NoOp.json`
- `Component_Instruction_Clearing_Constructor.json`
- `Component_Instruction_Constructing_Constructor.json`

---

## Final Template Parameter Summary (after all steps)

| Parameter | Default | Purpose |
|---|---|---|
| `NameTranslationKey` | Colonist name key | Display name |
| `Appearance` | `Colonist` | Model |
| `MaxHealth` | 20 | HP |
| `MaxSpeed` | 3 | Walk speed |
| `DebugCategory` | `COLONIST_JOB` | Log category |
| `DefaultIdleComponent` | `Idle_Default_Worker` | Off-shift wander body |
| `WaitingForWorkComponent` | `WaitingForWork_Default` | Wander + scan body (no tool checks) |
| `ClaimWorkComponent` | `ClaimWork_NoOp` | Batch-claim body |
| `RetrievingItemsComponent` | `RetrievingItems_Default` | Fetch required items body |
| `TravelingToWorkSiteComponent` | `TravelingToWorkSite_Harvester` | Seek + arrive body |
| `PerformWorkComponent` | `PerformWork_NoOp` | Unified work loop body |
| `WaitingForClaimReleaseComponent` | `WaitingForClaimRelease_Default` | Wait-for-interactable body |

## Final JobState Enum (after all steps)

```java
// Idle group
Idle, Sleeping, TravelingToWorkstation, TravelingToHome

// Working group
WaitingForWork, ClaimWork, RetrievingItems, TravelingToWorkSite,
PerformWork, WaitingForClaimRelease, DeliveringItems

// Recharging group
Recharging  // reserved
```
