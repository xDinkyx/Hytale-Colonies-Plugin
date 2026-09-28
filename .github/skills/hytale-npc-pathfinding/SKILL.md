---
name: hytale-npc-pathfinding
version: 2
source: https://hytalemodding.com/official-documentation/npc/
authors:
  - name: "HytaleModding"
    url: "https://github.com/HytaleModding"
  - name: "Hypixel Studios"
    url: "https://hytale.com/"
tags: [hytale, npc, pathfinding, navigation, ReadPosition, Seek, NavState, SensorNav, SensorLeash, SensorPath, SensorMotionController, BodyMotion, HeadMotion, MovementMode, ActionRecomputePath, ActionMakePath, ActionResetPath, ActionOverrideAltitude, ActionSetLeashPosition, MoveToTargetComponent, PathFindingSystem]
---

# Hytale NPC Plugin-Driven Pathfinding

The correct way to navigate an NPC to a plugin-specified position with full A* obstacle avoidance via the `ReadPosition` sensor and `Seek` body motion.

## Triggers

- pathfinding
- NPC navigation
- A* pathfinding
- plugin pathfinding
- ReadPosition
- StoredPosition
- NavTarget
- navigate NPC
- Seek motion
- BodyMotionFind
- getStoredPosition
- getMarkedEntitySupport
- MoveToTargetComponent
- PathFindingSystem
- RefChangeSystem pathfinding
- setTransientPath
- PathManager
- NavState
- SensorNav
- BLOCKED
- AT_GOAL
- PROGRESSING
- MovementMode
- body motion types
- HeadMotion
- SensorLeash
- leash sensor
- SensorPath
- SensorMotionController
- ActionRecomputePath
- ActionMakePath
- ActionResetPath
- ActionOverrideAltitude
- ActionSetLeashPosition
- BodyMotionWander
- BodyMotionSequence

---

## Plugin-Driven A* Pathfinding (ReadPosition + Seek)

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

## Navigation State — SensorNav

`SensorNav` (`com.hypixel.hytale.server.npc.corecomponents.movement.SensorNav`) queries the A* pathfinder state. Use it to branch logic when the NPC is blocked or has arrived.

### NavState enum (`com.hypixel.hytale.server.npc.movement.NavState`)

| Value | Description |
|---|---|
| `INIT` | Not yet started |
| `PROGRESSING` | Moving or computing a path |
| `BLOCKED` | Cannot advance any further |
| `DEFER` | Delaying / unable to advance this tick |
| `AT_GOAL` | Reached destination |
| `ABORTED` | Search stopped, target not reached |

### SensorNav JSON fields

| Field | Default | Description |
|---|---|---|
| `NavStates` | (all) | Array of `NavState` values to match; empty matches any state |
| `ThrottleDuration` | `0` | Minimum seconds the pathfinder must be in the state before firing (0 = immediate) |
| `TargetDelta` | `0` | Minimum distance the target must have moved since path was computed (0 = ignore) |

```json
{
  "Sensor": {
    "Type": "Nav",
    "NavStates": ["BLOCKED", "ABORTED"],
    "ThrottleDuration": 2.0
  },
  "Actions": [
    { "Type": "RecomputePath" }
  ]
}
```

---

## Movement Modes

`MovementMode` enum (`com.hypixel.hytale.server.npc.movement.MovementMode`) declares what locomotion controller the NPC uses. Assigned in NPC JSON role definitions.

| Value | MotionController |
|---|---|
| `WALK` | `MotionControllerWalk` |
| `WADE` | `MotionControllerWalk` (wading variant) |
| `UNDERWATER_WALK` | `MotionControllerWalk` (underwater variant) |
| `DIVE` | `MotionControllerDive` |
| `FLY` | `MotionControllerFly` |

---

## Body Motion Types

Used in role JSON `BodyMotion` fields. All types are NPCOnlyInstructions.

| JSON `Type` | Builder class | Description |
|---|---|---|
| `Find` / `Seek` | `BuilderBodyMotionFind` | A* walk to a sensor-provided position |
| `FindWithTarget` | `BuilderBodyMotionFindWithTarget` | A* walk to a position relative to the marked target |
| `Wander` | `BuilderBodyMotionWander` | Wander randomly |
| `WanderInCircle` | `BuilderBodyMotionWanderInCircle` | Wander in a circle around the origin |
| `WanderInRect` | `BuilderBodyMotionWanderInRect` | Wander within a rectangular area |
| `Path` | `BuilderBodyMotionPath` | Follow a defined patrol path |
| `MaintainDistance` | `BuilderBodyMotionMaintainDistance` | Maintain a set distance from target |
| `MoveAway` | `BuilderBodyMotionMoveAway` | Move away from something |
| `MatchLook` | `BuilderBodyMotionMatchLook` | Match heading/look of a target |
| `Teleport` | `BuilderBodyMotionTeleport` | Teleport movement (no walking animation) |
| `Land` | `BuilderBodyMotionLand` | Land from air (used after flying) |
| `TakeOff` | `BuilderBodyMotionTakeOff` | Take off to fly |
| `Leave` | `BuilderBodyMotionLeave` | Leave current location |
| `Timer` | `BuilderBodyMotionTimer` | Hold position for a timed duration |
| `Nothing` | `BuilderBodyMotionNothing` | No movement |
| `Sequence` | `BuilderBodyMotionSequence` | Execute a sequence of body motions |

---

## Head Motion Types

Used in role JSON `HeadMotion` fields.

| JSON `Type` | Builder class | Description |
|---|---|---|
| `Watch` | `BuilderHeadMotionWatch` | Watch / track a target entity |
| `Observe` | `BuilderHeadMotionObserve` | Look at a world position |
| `Aim` | `BuilderHeadMotionAim` | Aim at a target (combat use) |
| `Timer` | `BuilderHeadMotionTimer` | Hold head direction for a timed duration |
| `Nothing` | `BuilderHeadMotionNothing` | No head movement |
| `Sequence` | `BuilderHeadMotionSequence` | Execute a sequence of head motions |

---

## Movement-Control Actions

### ActionRecomputePath

Forces the motion controller to recompute the A* path on the next tick. No parameters.

**Builder state:** Stable

```json
{ "Type": "RecomputePath" }
```

Use when `SensorNav` detects `BLOCKED` or `ABORTED` to trigger a fresh pathfinding attempt.

---

### ActionMakePath

Constructs a transient waypoint path from a `TransientPathDefinition` object (rotation + distance series). Does **not** use A* — creates straight-line waypoints. Use `SensorPath` with `PathType: "TransientPath"` to follow it.

**Builder state:** WorkInProgress (subject to change)

```json
{
  "Type": "MakePath",
  "Path": { ... }
}
```

---

### ActionResetPath

Resets the current patrol path the NPC is following. No parameters.

**Builder state:** Stable

```json
{ "Type": "ResetPath" }
```

---

### ActionOverrideAltitude

Temporarily overrides the preferred altitude range for a flying NPC. Must be refreshed each tick (set it in a continuous instruction).

**Builder state:** Stable

```json
{
  "Type": "OverrideAltitude",
  "DesiredAltitudeRange": [10.0, 20.0]
}
```

| Field | Required | Description |
|---|---|---|
| `DesiredAltitudeRange` | Yes | `[min, max]` altitude in world units; must be weakly monotonic ≥ 0 |

---

### ActionSetLeashPosition

Sets the NPC's leash anchor to its current position or to its target's position. Required before `SensorLeash` can function. Use `ToCurrent` when spawning and `ToTarget` to anchor to a followed entity.

**Builder state:** Stable

```json
{ "Type": "SetLeashPosition", "ToCurrent": true }
```

| Field | Default | Description |
|---|---|---|
| `ToCurrent` | `false` | Anchor to the NPC's current position |
| `ToTarget` | `false` | Anchor to the current target's position (requires an entity feature) |

Exactly one of `ToCurrent` or `ToTarget` must be `true`.

---

## Navigation Sensors

### SensorLeash

Fires when the NPC is farther than `Range` from its leash anchor. Use with `ActionSetLeashPosition` to establish the anchor on spawn, then detect when the NPC wanders too far.

**Builder state:** Stable

```json
{
  "Sensor": {
    "Type": "Leash",
    "Range": 15.0
  }
}
```

| Field | Required | Description |
|---|---|---|
| `Range` | Yes | Maximum allowed distance from leash anchor (must be > 0) |

Provides a `Position` feature (the leash anchor position) usable by a follow-up body motion.

---

### SensorMotionController

Matches when a specific motion controller is currently active on the NPC. Useful for gating behavior that only applies to flying, diving, or walking modes.

**Builder state:** Experimental

```json
{
  "Sensor": {
    "Type": "MotionController",
    "MotionController": "Fly"
  }
}
```

| Field | Required | Description |
|---|---|---|
| `MotionController` | Yes | Name of the motion controller to test for (e.g., `"Walk"`, `"Fly"`, `"Dive"`) |

---

### SensorPath

Finds the nearest path matching criteria and provides its nearest waypoint position.

**Builder state:** Stable

| Field | Default | Description |
|---|---|---|
| `Path` | (empty) | Named path; required only for `WorldPath` type |
| `Range` | `10` | Detection radius in blocks (must be > 0) |
| `PathType` | `AnyPrefabPath` | `WorldPath`, `CurrentPrefabPath`, `AnyPrefabPath`, `TransientPath` |

Provides `Position` and `Path` features for downstream body motions.

---

## Plugin Navigation Bridge Pattern

This is the canonical pattern used in HytaleColonies to drive NPC navigation from ECS plugin code.

### Overview

```
ECS code                     ECS system                     NPC JSON role
─────────────────────        ──────────────────────         ──────────────────────────
commandBuffer.addComponent   PathFindingSystem               ReadPosition sensor
  (ref, MoveToTarget)   -->  onComponentAdded           -->  Slot: "NavTarget"
                             writes slot 0 = target          BodyMotion: Seek
                             removes MoveToTarget
```

### Why this works

- `RefChangeSystem` fires **before** the NPC tick reads the slot — no one-tick stale data.
- The component is transient (no codec needed) and removed immediately after being consumed.
- The NPC JSON drives movement declaratively; the plugin only signals *where to go*.

### MoveToTargetComponent (transient, no codec)

```java
public class MoveToTargetComponent implements Component<EntityStore> {
    public Vector3d target;

    public MoveToTargetComponent() {}

    public MoveToTargetComponent(Vector3d target) {
        this.target = target;
    }

    @Override
    public MoveToTargetComponent clone() {
        return new MoveToTargetComponent(target);
    }

    // No BuilderCodec — transient component, never persisted
}
```

### PathFindingSystem

```java
public class PathFindingSystem extends RefChangeSystem<EntityStore, MoveToTargetComponent> {

    private static final int NAV_TARGET_SLOT = 0; // slot 0 = first ReadPosition slot in role

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

### Trigger from anywhere

```java
store.addComponent(npcRef, MoveToTargetComponent.getComponentType(), new MoveToTargetComponent(targetPos));
```

### Role JSON

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

> Slot index 0 is the first `ReadPosition` slot declared in the role. Multiple slots increment from 0 in declaration order.

---

## Related Skills

- `hytale-npc-templates` — Core template structure, states
- `hytale-npc-sensors` — ReadPosition sensor, Nav sensor, block sensors
- `hytale-npc-actions` — Seek body motion, MakePath action
- `hytale-ecs` — ECS patterns (RefChangeSystem, ComponentType)
