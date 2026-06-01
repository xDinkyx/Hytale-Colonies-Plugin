---
name: hytale-npc-pathfinding
version: 1
source: https://hytalemodding.com/official-documentation/npc/
authors:
  - name: "HytaleModding"
    url: "https://github.com/HytaleModding"
  - name: "Hypixel Studios"
    url: "https://hytale.com/"
tags: [hytale, npc, pathfinding, navigation, ReadPosition, Seek]
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

## Related Skills

- `hytale-npc-templates` — Core template structure, states
- `hytale-npc-sensors` — ReadPosition sensor, Nav sensor, block sensors
- `hytale-npc-actions` — Seek body motion, MakePath action
- `hytale-ecs` — ECS patterns (RefChangeSystem, ComponentType)
