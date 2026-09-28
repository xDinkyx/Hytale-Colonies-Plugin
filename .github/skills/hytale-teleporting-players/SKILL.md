---
name: hytale-teleporting-players
description: Documents how to teleport players in Hytale plugins using the Teleport component and Transform. Use when teleporting a player to a position, changing player world, setting player rotation, or building teleport utilities. Triggers - teleport, Teleport, teleport player, Transform, Rotation, createForPlayer, Teleport component, move player, warp, position, Vector3d.
---

# Hytale Teleporting Players

Use this skill when teleporting players to a position or world in Hytale plugins. Teleportation works by attaching a `Teleport` component to the player entity with the desired target world and transform. You can only trigger teleports from the world where the player is currently present.

> **Source:** <https://hytalemodding.dev/en/docs/guides/plugin/teleporting-players>

---

## Quick Reference

| Concept | Description |
|---------|-------------|
| **Teleport** | ECS component that triggers player teleportation when added to an entity |
| **Transform** | Represents a position (`Vector3d`) and rotation (`Rotation3f`) |
| **Rotation3f** | Represents pitch, yaw, and roll — constructor is `new Rotation3f(pitch, yaw, roll)` |
| **createForPlayer** | Factory method on `Teleport`; automatically sets yaw-only body rotation and full head rotation |
| **createExact** | Factory for correction teleports — preserves exact body and head rotation as given |
| **withoutVelocityReset()** | Chainable builder option; prevents velocity being zeroed on teleport |
| **setOnComplete / getOnComplete** | `CompletableFuture<Void>` callback fired when the teleport finishes |
| **getComponentType** | Returns the `Teleport` component type for store operations |

---

## Key Concepts

### Teleport Component

`Teleport` is an ECS component. Adding it to a player entity triggers the teleport. It provides factory methods (`createForPlayer`) to configure the destination world and transform.

### Transform

A `Transform` encapsulates a position and rotation:
- **Position**: `Vector3d(x, y, z)` — world coordinates
- **Rotation**: `Rotation(yaw, pitch, 0)` — the last value (roll) should always be `0`

### Constraints

- You can **only trigger teleports from the world where the player is currently present**.
- Requires a `Ref<EntityStore>` and `Store<EntityStore>` for the player entity.

---

## Required Imports

```java
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
```

---

## Basic Teleport (Position Only)

Teleport a player to x/y/z coordinates in a target world:

```java
public static void teleportPlayer(Ref<EntityStore> ref, Store<EntityStore> store,
                                   World targetWorld, double x, double y, double z) {
    Transform transform = new Transform(x, y, z);
    Teleport teleport = Teleport.createForPlayer(targetWorld, transform);
    store.addComponent(ref, Teleport.getComponentType(), teleport);
}
```

### Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `ref` | `Ref<EntityStore>` | Reference to the player entity |
| `store` | `Store<EntityStore>` | The entity store for the player's current world |
| `targetWorld` | `World` | The world to teleport the player into |
| `x`, `y`, `z` | `double` | Target position coordinates |

---

## Teleport with Rotation

Teleport a player to a position and set their facing direction:

```java
public static void teleportPlayer(Ref<EntityStore> ref, Store<EntityStore> store,
                                   World targetWorld, double x, double y, double z,
                                   float yaw, float pitch) {
    Transform transform = new Transform(
        new Vector3d(x, y, z),
        new Rotation3f(pitch, yaw, 0)  // (pitch, yaw, roll) — roll is always 0
    );
    Teleport teleport = Teleport.createForPlayer(targetWorld, transform);
    store.addComponent(ref, Teleport.getComponentType(), teleport);
}
```

### Rotation Values (`Rotation3f` constructor order: pitch, yaw, roll)

| Field | Type | Description |
|-------|------|-------------|
| `pitch` | `float` | Vertical rotation (up/down) — constructor position 0 |
| `yaw` | `float` | Horizontal rotation (left/right) — constructor position 1 |
| `roll` | `float` | Always `0` — constructor position 2 |

---

## Transform Construction

### Position-Only Transform

```java
Transform transform = new Transform(x, y, z);
```

### Position + Rotation Transform

```java
Transform transform = new Transform(
    new Vector3d(x, y, z),          // Position
    new Rotation3f(pitch, yaw, 0)   // Rotation3f(pitch, yaw, roll) — roll always 0
);
```

---

## Usage Notes

- **Thread safety**: Execute teleport operations within the world's execution context (`world.execute(() -> { ... })`) if called from outside the world tick.
- **Same-world teleport**: Pass the player's current world as `targetWorld` to teleport within the same world.
- **Cross-world teleport**: Pass a different `World` object to move the player between worlds.
- **Instance teleportation**: For teleporting into instanced worlds, see the `hytale-instances` skill which provides higher-level APIs via `InstancesPlugin`.

---

## Complete Example: Teleport Command Utility

```java
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

public class TeleportUtil {

    /**
     * Teleport a player to coordinates in a target world.
     */
    public static void teleport(Ref<EntityStore> ref, Store<EntityStore> store,
                                World targetWorld, double x, double y, double z) {
        Transform transform = new Transform(x, y, z);
        Teleport teleport = Teleport.createForPlayer(targetWorld, transform);
        store.addComponent(ref, Teleport.getComponentType(), teleport);
    }

    /**
     * Teleport a player to coordinates with a specific facing direction.
     */
    public static void teleport(Ref<EntityStore> ref, Store<EntityStore> store,
                                World targetWorld, double x, double y, double z,
                                float pitch, float yaw) {
        Transform transform = new Transform(
            new Vector3d(x, y, z),
            new Rotation3f(pitch, yaw, 0)
        );
        Teleport teleport = Teleport.createForPlayer(targetWorld, transform);
        store.addComponent(ref, Teleport.getComponentType(), teleport);
    }

    /**
     * Teleport a player to a pre-built Transform in a target world.
     */
    public static void teleport(Ref<EntityStore> ref, Store<EntityStore> store,
                                World targetWorld, Transform transform) {
        Teleport teleport = Teleport.createForPlayer(targetWorld, transform);
        store.addComponent(ref, Teleport.getComponentType(), teleport);
    }
}
```

---

## Official Javadoc References

- [`TeleportSystems`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/modules/entity/teleport/TeleportSystems.html) — entity and player teleport move systems
- Source: [Teleporting Players Guide](https://hytalemodding.dev/en/docs/guides/plugin/teleporting-players)
