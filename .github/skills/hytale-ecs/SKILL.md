---
name: hytale-ecs
description: Core Hytale ECS (Entity Component System) architecture and patterns for plugin development. Covers Store, EntityStore, ChunkStore, Holder, Ref, Components, Systems (EntityTickingSystem, TickingSystem, DelayedEntitySystem, RefSystem, RefChangeSystem), Queries, SystemGroups, CommandBuffer, block components, and plugin registration. Use when creating components, systems, queries, or working with entity/block data. Triggers - ECS, entity component system, Store, EntityStore, ChunkStore, Holder, Ref, Component, System, Query, CommandBuffer, SystemGroup, ArchetypeChunk, ComponentType, registerComponent, registerSystem, block component, block tick, RefSystem, RefChangeSystem, EntityTickingSystem, TickingSystem, DelayedEntitySystem, onEntityRemove, entity removal, entity death, entity despawn.
---

# Hytale ECS (Entity Component System)

Comprehensive reference for Hytale's ECS architecture. This is the foundation of all plugin development.

> **Related skills:** For Codec/BuilderCodec serialization details, see `hytale-persistent-data`. For entity effects using ECS, see `hytale-entity-effects`.

## Quick Reference

| Task | Approach |
|------|----------|
| Access entity data | `store.getComponent(ref, ComponentType)` |
| Queue component change | `commandBuffer.addComponent(ref, componentType, instance)` |
| Build new entity | Create `Holder<EntityStore>`, add components, call `store.addEntity(holder, reason)` |
| Get entity handle | `archetypeChunk.getReferenceTo(index)` returns `Ref<EntityStore>` |
| Per-entity tick logic | Extend `EntityTickingSystem<EntityStore>` |
| Global tick logic | Extend `TickingSystem<EntityStore>` |
| Interval-based logic | Extend `DelayedEntitySystem<EntityStore>` |
| React to entity add/remove (death, despawn) | Extend `RefSystem<EntityStore>`, implement `onEntityRemove` (skip `UNLOAD`) |
| React to component changes | Extend `RefChangeSystem<EntityStore, T>` — fires on explicit API mutations only, NOT on entity deletion |
| Filter entities | `Query.and(componentTypes...)`, `Query.not(componentType)` |
| Register component | `getEntityStoreRegistry().registerComponent(Class, factory)` in `setup()` |
| Register system | `getEntityStoreRegistry().registerSystem(system)` in `start()` |
| Register block component | `getChunkStoreRegistry().registerComponent(Class, name, CODEC)` in `setup()` |
| Register block system | `getChunkStoreRegistry().registerSystem(system)` in `start()` |
| Look up block entity | `BlockModule.getBlockEntity(world, x, y, z)` → `Ref<ChunkStore>` or `null` (never creates) |
| Get entity ref from chunk | `blockComponentChunk.getEntityReference(blockIndex)` → `Ref<ChunkStore>` or `null` |
| Create block entity on-demand | `ChunkStore.REGISTRY.newHolder()` + add `BlockStateInfo` + call `chunkStore.addEntity(holder, AddReason.SPAWN)` |
| Inspect entity component count | `store.getArchetype(ref).length()` — total number of component types on the entity |
| Atomic world-thread operation | `world.execute(() -> { ... })` — use when you need read-check-then-write across stores |

---

## Core Architecture

ECS follows **composition over inheritance**: Entities are identifiers, Components are pure data, Systems contain logic.

### Store

The `Store` class is the core of Hytale's ECS. It stores entities using **archetypes** — entities with the same set of components are chunked together for fast retrieval.

```
Store
├── EntityStore   — entities in a World (players, mobs, NPCs, projectiles)
└── ChunkStore    — block data in a World (chunks, block sections, block components)
```

### EntityStore

`EntityStore` extends `Store` and implements `WorldProvider`, giving access to a specific Hytale `World`. It maintains internal lookups:

- `entitiesByUuid` — find entity by persistent UUID
- `networkIdToRef` — find entity by networking ID

Every entity has a `UUIDComponent` and `NetworkId` for these lookups.

### ChunkStore

`ChunkStore` manages block/chunk components. Contains `WorldChunk` components (which hold `EntityChunk` for entities in the chunk and `BlockChunk` with `BlockSection`s). Use for block systems and ticking blocks.

### Holder (Entity Blueprint)

A `Holder` is a staging cart / blueprint for an entity. Collect all components, then "check out" at the Store:

```java
// Conceptual flow (see Universe.addPlayer for real example):
// 1. Create Holder and add components
// 2. store.addEntity(holder, AddReason.LOAD) → returns Ref
```

`PlayerStorage#load` returns `CompletableFuture<Holder<EntityStore>>` — async loading that eventually adds to the store.

### Ref (Reference Handle)

A **safe handle/pointer** to an entity. **Never store direct references to entity objects** — use `Ref` instead.

```java
Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);

// Validate before use (throws if entity deleted)
ref.validate();
```

---

## Components

Components are **pure data containers** — no logic. They must implement `Component<StoreType>` and provide:

1. **Default constructor** — required for registration factory
2. **Copy constructor** — used by `clone()`
3. **`clone()` method** — ECS calls this internally to duplicate data

### Entity Component Template

```java
public class PoisonComponent implements Component<EntityStore> {
    private float damagePerTick;
    private float tickInterval;
    private int remainingTicks;
    private float elapsedTime;

    // Static ComponentType holder for convenient access
    private static ComponentType<EntityStore, PoisonComponent> type;

    public static ComponentType<EntityStore, PoisonComponent> getComponentType() {
        return type;
    }

    public static void setComponentType(ComponentType<EntityStore, PoisonComponent> type) {
        PoisonComponent.type = type;
    }

    // BuilderCodec for serialization (see hytale-persistent-data skill for full Codec reference)
    public static final BuilderCodec<PoisonComponent> CODEC = BuilderCodec
        .builder(PoisonComponent.class, PoisonComponent::new)
        .append(
            new KeyedCodec<>("DamagePerTick", Codec.FLOAT),
            (data, value) -> data.damagePerTick = value,
            data -> data.damagePerTick
        ).add()
        .append(
            new KeyedCodec<>("TickInterval", Codec.FLOAT),
            (data, value) -> data.tickInterval = value,
            data -> data.tickInterval
        ).add()
        .append(
            new KeyedCodec<>("RemainingTicks", Codec.INTEGER),
            (data, value) -> data.remainingTicks = value,
            data -> data.remainingTicks
        ).add()
        .append(
            new KeyedCodec<>("ElapsedTime", Codec.FLOAT),
            (data, value) -> data.elapsedTime = value,
            data -> data.elapsedTime
        ).add()
        .build();

    // Default constructor (required for factory)
    public PoisonComponent() {
        this(5f, 1.0f, 10);
    }

    // Parameterized constructor
    public PoisonComponent(float damagePerTick, float tickInterval, int totalTicks) {
        this.damagePerTick = damagePerTick;
        this.tickInterval = tickInterval;
        this.remainingTicks = totalTicks;
        this.elapsedTime = 0f;
    }

    // Copy constructor (required for clone)
    public PoisonComponent(PoisonComponent other) {
        this.damagePerTick = other.damagePerTick;
        this.tickInterval = other.tickInterval;
        this.remainingTicks = other.remainingTicks;
        this.elapsedTime = other.elapsedTime;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new PoisonComponent(this);
    }

    // Getters, setters, utility methods...
}
```

> **Important:** KeyedCodec identifier strings must be **Uppercase** and **globally unique across your entire mod**. See `hytale-persistent-data` skill for full Codec reference including validators, MapCodec, and complex types.

### Block Component Template

Block components use `Component<ChunkStore>` instead of `Component<EntityStore>`:

```java
public class ExampleBlock implements Component<ChunkStore> {
    public static final BuilderCodec CODEC = BuilderCodec
        .builder(ExampleBlock.class, ExampleBlock::new)
        .build();

    public ExampleBlock() { }

    public static ComponentType getComponentType() {
        return ExamplePlugin.get().getExampleBlockComponentType();
    }

    @Nullable
    public Component<ChunkStore> clone() {
        return new ExampleBlock();
    }
}
```

### Accessing Components

Always use `Store` to access component data — never call methods directly on entity objects:

```java
// In a command, system, or event handler with store + ref:
Player player = store.getComponent(ref, Player.getComponentType());
UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());

player.sendMessage(Message.raw("Position: " + transform.getPosition()));
```

### Player Components

Players are composed of two key components:

| Component | Lifetime | Purpose |
|-----------|----------|---------|
| `PlayerRef` | While connected to server (survives world switches) | Connection identity: username, UUID, language, packet handler |
| `Player` | While spawned in a world (per-world) | Physical presence, gameplay-specific data |

---

## Systems

Systems contain **all logic**. They operate on entities matching component queries. The ECS scheduler runs systems each tick.

### EntityTickingSystem

Most common type. Runs every tick, processes each matching entity individually.

```java
public class PoisonSystem extends EntityTickingSystem<EntityStore> {
    private final ComponentType<EntityStore, PoisonComponent> poisonComponentType;

    public PoisonSystem(ComponentType<EntityStore, PoisonComponent> poisonComponentType) {
        this.poisonComponentType = poisonComponentType;
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        PoisonComponent poison = archetypeChunk.getComponent(index, poisonComponentType);
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);

        poison.addElapsedTime(dt);
        if (poison.getElapsedTime() >= poison.getTickInterval()) {
            poison.resetElapsedTime();
            Damage damage = new Damage(Damage.NULL_SOURCE, DamageCause.OUT_OF_WORLD, poison.getDamagePerTick());
            DamageSystems.executeDamage(ref, commandBuffer, damage);
            poison.decrementRemainingTicks();
        }
        if (poison.isExpired()) {
            commandBuffer.removeComponent(ref, poisonComponentType);
        }
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getGatherDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(this.poisonComponentType);
    }
}
```

**Key parameters:**
- `dt` — delta time since last tick (use for time accumulation, not tick counting)
- `index` — position in the archetype chunk
- `archetypeChunk` — access entity components via index
- `commandBuffer` — queue changes (thread-safe)

### TickingSystem

Runs once per tick **globally**, not per-entity. Use for world-wide logic.

```java
public class GlobalUpdateSystem extends TickingSystem<EntityStore> {
    @Override
    public void tick(float dt, int index, Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        // Global logic here
    }
}
```

### DelayedEntitySystem

Like `EntityTickingSystem` but with a built-in interval. Constructor takes seconds between executions.

```java
public class HealthRegenSystem extends DelayedEntitySystem<EntityStore> {
    public HealthRegenSystem() {
        super(1.0f); // Runs every 1 second
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Runs every 1 second per matching entity
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }
}
```

#### Batch-start hook

`DelayedEntitySystem` has a second `tick` overload — `tick(float dt, int systemIndex, Store store)` — that fires **once per cadence cycle**, before any entity is processed. Override it to run one-time setup per cycle (e.g. clearing a set that must be shared across all entities in the batch), then call `super.tick()` to continue normal entity processing:

```java
@Override
public void tick(float dt, int systemIndex, @Nonnull Store<ChunkStore> store) {
    perCycleState.clear(); // reset once before any entity tick runs
    super.tick(dt, systemIndex, store);
}
```

This is useful when `tick()` is called multiple times per cycle (once per matching entity/archetype chunk) and you need state that is shared across all those calls but reset between cycles.

### RefSystem (Entity Add/Remove)

Reacts when an **entity** matching the query is added to or removed from the store. Use when you need to react to entity death, despawn, or removal — not just to individual component changes.

> **Critical distinction:** `RefSystem` fires on entity lifecycle events (entity added/removed). `RefChangeSystem` fires only when a component is explicitly added/removed via the API (e.g. `commandBuffer.removeComponent`). `RefChangeSystem.onComponentRemoved` does **NOT** fire when an entity is deleted from the store — use `RefSystem.onEntityRemove` for that.

```java
public class MyEntityRemovalSystem extends RefSystem<EntityStore> {
    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref,
                               @Nonnull AddReason reason,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Entity matching query was added to the store
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref,
                                @Nonnull RemoveReason reason,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (reason == RemoveReason.UNLOAD)
            return; // Chunk unload is not the same as death/despawn -- skip it

        // Entity was truly removed (died, despawned, etc.)
        // All components are still readable from the store at this point
        MyComponent comp = store.getComponent(ref, MyComponent.getComponentType());
        // ...
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        return Query.and(MyComponent.getComponentType());
    }
}
```

Register with: `getEntityStoreRegistry().registerSystem(new MyEntityRemovalSystem());` in `start()`.

---

### RefChangeSystem

Reacts to component add/set/remove events **via the API**. Use for caching, side effects, and initialization logic triggered by explicit component mutations.

> **Does NOT fire on entity deletion.** If an entity is deleted from the store (e.g. NPC dies), `onComponentRemoved` is not called. Use `RefSystem.onEntityRemove` for entity lifecycle events instead.

Works with **both** `EntityStore` (entities) and `ChunkStore` (block components) — just swap the generic type parameter.

```java
public class MyRefSystem extends RefChangeSystem<EntityStore, MyComponent> {
    @Nonnull
    @Override
    public ComponentType<EntityStore, MyComponent> componentType() {
        return MyComponent.getComponentType();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
                                  @Nonnull MyComponent component,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Component was added to entity
    }

    @Override
    public void onComponentSet(@Nonnull Ref<EntityStore> ref,
                                @Nullable MyComponent oldComponent,
                                @Nonnull MyComponent newComponent,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Component was updated via replaceComponent or putComponent
    }

    @Override
    public void onComponentRemoved(@Nonnull Ref<EntityStore> ref,
                                    @Nonnull MyComponent component,
                                    @Nonnull Store<EntityStore> store,
                                    @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Component was removed from entity
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return MyComponent.getComponentType();
    }
}
```

#### RefChangeSystem on Block Components (ChunkStore)

To react to a block component's lifecycle, swap the generic to `ChunkStore` and register with `getChunkStoreRegistry()`:

```java
public class MyBlockComponentCleanupSystem extends RefChangeSystem<ChunkStore, MyBlockComponent> {

    @Override
    public ComponentType<ChunkStore, MyBlockComponent> componentType() {
        return MyBlockComponent.getComponentType();
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return MyBlockComponent.getComponentType();
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull MyBlockComponent component,
                                  @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {}

    @Override
    public void onComponentSet(@Nonnull Ref<ChunkStore> ref, @Nonnull MyBlockComponent old,
                                @Nonnull MyBlockComponent updated, @Nonnull Store<ChunkStore> store,
                                @Nonnull CommandBuffer<ChunkStore> commandBuffer) {}

    @Override
    public void onComponentRemoved(@Nonnull Ref<ChunkStore> ref, @Nonnull MyBlockComponent removed,
                                    @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        // CRITICAL: When a block is broken, the engine calls removeEntity on the block entity,
        // which cascades onComponentRemoved for ALL components — including yours.
        // At that point, ref is already invalid. Always guard before any store/commandBuffer ops:
        if (!ref.isValid()) return;

        // Safe to inspect the entity further here.
        // E.g. check how many components remain — useful to decide whether to destroy the entity:
        Archetype<ChunkStore> archetype = store.getArchetype(ref);
        if (archetype.length() <= 1) {  // only BookkeepingStateInfo (BlockStateInfo) left
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
        }
    }
}
```

> **Block-break cascade**: Breaking a block triggers `removeEntity` on the block entity, which fires `onComponentRemoved` for every component. The `ref.isValid()` guard is **mandatory** in `onComponentRemoved` for block components to prevent a double-remove.

> **`Archetype.length()`**: Returns the total number of component types attached to the entity at the time of the call. Useful for detecting "empty" placeholder entities that only hold a `BlockStateInfo`.

---

## Queries

Queries filter which entities a system processes. Only matching entities reach `tick()`.

```java
// Single component — any entity with PoisonComponent
Query.and(poisonComponentType)

// Multiple components — entities with BOTH
Query.and(poisonComponentType, Player.getComponentType())

// Exclusion — players that aren't dead
Query.and(Player.getComponentType(), Query.not(DeathComponent.getComponentType()))
```

---

## CommandBuffer

Queues changes instead of mutating the Store directly. **Always use CommandBuffer** for thread safety and proper ordering.

```java
// Add a component
commandBuffer.addComponent(ref, componentType, new MyComponent());

// Remove a component
commandBuffer.removeComponent(ref, componentType);

// Read a component (safe within system tick)
MyComponent comp = commandBuffer.getComponent(ref, componentType);
```

### CommandBuffer as ComponentAccessor

`CommandBuffer<EntityStore>` implements `ComponentAccessor<EntityStore>`. Pass it wherever a server API accepts `ComponentAccessor<EntityStore>` to defer any internal `store.addEntities()` calls to end-of-tick instead of executing them mid-tick (which throws `IllegalStateException: Store is currently processing!`).

```java
// WRONG — passes Store directly; crashes when the block break spawns drop entities mid-tick
BlockHarvestUtils.performBlockDamage(entity, ref, pos, item, tool, null, false, 1f, 0,
        chunkRef, store, world.getChunkStore().getStore());

// CORRECT — pass commandBuffer; drop entity spawning is deferred safely
BlockHarvestUtils.performBlockDamage(entity, ref, pos, item, tool, null, false, 1f, 0,
        chunkRef, commandBuffer, world.getChunkStore().getStore());
```

---

## SystemGroups and Dependencies

Controls execution order. Critical for systems that interact (e.g., damage pipeline).

### Declaring a Group

```java
@Nullable
@Override
public SystemGroup<EntityStore> getGroup() {
    return DamageModule.get().getGatherDamageGroup();
}
```

### Declaring Dependencies

```java
@Nonnull
public Set<Dependency<EntityStore>> getDependencies() {
    return Set.of(
        new SystemGroupDependency(Order.AFTER, DamageModule.get().getFilterDamageGroup()),
        new SystemDependency(Order.BEFORE, PlayerSystems.ProcessPlayerInput.class)
    );
}
```

### Damage Pipeline Stages (Example)

Hytale's damage system demonstrates why ordering matters:

1. **GatherDamageGroup** — Collects damage sources
2. **FilterDamageGroup** — Applies reductions, cancellations (armor, invulnerability)
3. **Apply** — Damage applied to health
4. **InspectDamageGroup** — Side effects (particles, sounds, death animations)

Wrong order = death animations before entity dies, or armor applied after health subtracted.

---

## Block Components (ChunkStore)

Block components use `ChunkStore` instead of `EntityStore`. They require a different registration path and additional setup for ticking.

### Block Entity Lookup and Creation

**Block entities do NOT exist automatically for plain blocks.** `BlockModule.getBlockEntity()` is a **lookup only** — it returns `null` if no entity has been explicitly created for that block. It never auto-creates one.

```java
// Lookup — returns null for plain blocks that have never had a component attached:
Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, x, y, z);
```

Alternatively, get the entity reference directly from a `BlockComponentChunk` (useful inside systems):

```java
// Access the chunk's block-component index:
BlockComponentChunk blockComponentChunk = ...;
int blockIndex = ChunkUtil.indexBlockInColumn(localX, localY, localZ);
Ref<ChunkStore> blockRef = blockComponentChunk.getEntityReference(blockIndex); // null if no entity
```

When you need to attach a component to a plain block that has no entity, **create one on demand**:

```java
// 1. Find the chunk's BlockComponentChunk to get the chunk ref:
BlockComponentChunk bcc = ...; // from block ticking system context
Ref<ChunkStore> chunkRef = bcc.getChunkRef(); // or from BlockStateInfo.getChunkRef()
int blockIndex = ChunkUtil.indexBlockInColumn(localX, localY, localZ);

// 2. Check whether an entity already exists:
Ref<ChunkStore> existing = bcc.getEntityReference(blockIndex);
if (existing != null && existing.isValid()) {
    // Entity exists — just add your component to it:
    chunkStore.putComponent(existing, MyBlockComponent.getComponentType(), new MyBlockComponent());
} else {
    // No entity — create a minimal one:
    Holder<ChunkStore> holder = ChunkStore.REGISTRY.newHolder();
    holder.putComponent(BlockModule.BlockStateInfo.getComponentType(),
            new BlockModule.BlockStateInfo(blockIndex, chunkRef));
    holder.putComponent(MyBlockComponent.getComponentType(), new MyBlockComponent());
    chunkStore.addEntity(holder, AddReason.SPAWN);
}
```

> **Important:** Any entity you create for a plain block is your responsibility to destroy. Consider using a `RefChangeSystem<ChunkStore, MyBlockComponent>.onComponentRemoved` to clean it up reactively (see the RefChangeSystem section above).

### Block RefSystem (Block Initializer)

Reacts when block entities with your component are added. Use to mark blocks as ticking:

```java
public class ExampleInitializer extends RefSystem {
    @Override
    public void onEntityAdded(@Nonnull Ref ref, @Nonnull AddReason reason,
                               @Nonnull Store store, @Nonnull CommandBuffer commandBuffer) {
        BlockModule.BlockStateInfo info = (BlockModule.BlockStateInfo) commandBuffer
            .getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        if (info == null) return;

        ExampleBlock generator = (ExampleBlock) commandBuffer
            .getComponent(ref, ExamplePlugin.get().getExampleBlockComponentType());
        if (generator != null) {
            int x = ChunkUtil.xFromBlockInColumn(info.getIndex());
            int y = ChunkUtil.yFromBlockInColumn(info.getIndex());
            int z = ChunkUtil.zFromBlockInColumn(info.getIndex());

            WorldChunk worldChunk = (WorldChunk) commandBuffer
                .getComponent(info.getChunkRef(), WorldChunk.getComponentType());
            if (worldChunk != null) {
                worldChunk.setTicking(x, y, z, true);
            }
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref ref, @Nonnull RemoveReason reason,
                                @Nonnull Store store, @Nonnull CommandBuffer commandBuffer) { }

    @Override
    public Query getQuery() {
        return Query.and(BlockModule.BlockStateInfo.getComponentType(),
                         ExamplePlugin.get().getExampleBlockComponentType());
    }
}
```

### Block Ticking System

Processes ticking blocks each tick:

```java
public class ExampleSystem extends EntityTickingSystem {
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk archetypeChunk,
                     @Nonnull Store store,
                     @Nonnull CommandBuffer commandBuffer) {
        BlockSection blocks = (BlockSection) archetypeChunk
            .getComponent(index, BlockSection.getComponentType());
        if (blocks.getTickingBlocksCountCopy() != 0) {
            ChunkSection section = (ChunkSection) archetypeChunk
                .getComponent(index, ChunkSection.getComponentType());
            BlockComponentChunk blockComponentChunk = (BlockComponentChunk) commandBuffer
                .getComponent(section.getChunkColumnReference(), BlockComponentChunk.getComponentType());

            blocks.forEachTicking(blockComponentChunk, commandBuffer, section.getY(),
                (bcc, cb, localX, localY, localZ, blockId) -> {
                    Ref<ChunkStore> blockRef = bcc
                        .getEntityReference(ChunkUtil.indexBlockInColumn(localX, localY, localZ));
                    if (blockRef == null) return BlockTickStrategy.IGNORED;

                    ExampleBlock exampleBlock = (ExampleBlock) cb
                        .getComponent(blockRef, ExampleBlock.getComponentType());
                    if (exampleBlock != null) {
                        WorldChunk worldChunk = (WorldChunk) commandBuffer
                            .getComponent(section.getChunkColumnReference(), WorldChunk.getComponentType());
                        World world = worldChunk.getWorld();
                        int globalX = localX + (worldChunk.getX() * 32);
                        int globalZ = localZ + (worldChunk.getZ() * 32);

                        // Must execute setBlock on world thread
                        world.execute(() -> {
                            world.setBlock(globalX + 1, localY, globalZ, "Rock_Ice");
                        });
                        return BlockTickStrategy.CONTINUE;
                    }
                    return BlockTickStrategy.IGNORED;
                });
        }
    }

    @Nullable
    public Query getQuery() {
        return Query.and(BlockSection.getComponentType(), ChunkSection.getComponentType());
    }
}
```

**Key points:**
- `worldChunk.setTicking(x, y, z, true)` marks a block for ticking
- `BlockTickStrategy.CONTINUE` keeps it ticking next tick; `IGNORED` skips
- `world.execute(() -> ...)` schedules work on the world thread (cannot call store functions from a system directly)
- Coordinate conversion: `globalX = localX + (worldChunk.getX() * 32)`

---

## Plugin Registration

Components and systems must be registered during the plugin lifecycle.

### EntityStore Registration (Entities)

```java
public final class ExamplePlugin extends JavaPlugin {
    private static ExamplePlugin instance;
    private ComponentType<EntityStore, PoisonComponent> poisonComponent;

    public ExamplePlugin(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;
    }

    @Override
    protected void setup() {
        // Register components in setup() — returns ComponentType handle
        this.poisonComponent = this.getEntityStoreRegistry()
            .registerComponent(PoisonComponent.class, PoisonComponent::new);
        PoisonComponent.setComponentType(this.poisonComponent);

        // Register commands, events, etc.
        this.getCommandRegistry().registerCommand(new ExampleCommand());
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, ExampleEvent::onPlayerReady);
    }

    @Override
    protected void start() {
        // Register systems in start()
        this.getEntityStoreRegistry().registerSystem(new PoisonSystem(PoisonComponent.getComponentType()));
    }

    public ComponentType<EntityStore, PoisonComponent> getPoisonComponentType() {
        return poisonComponent;
    }

    public static ExamplePlugin get() { return instance; }
}
```

### ChunkStore Registration (Blocks)

```java
@Override
protected void setup() {
    this.exampleBlockComponentType = this.getChunkStoreRegistry()
        .registerComponent(ExampleBlock.class, "ExampleBlock", ExampleBlock.CODEC);
}

@Override
protected void start() {
    this.getChunkStoreRegistry().registerSystem(new ExampleSystem());
    this.getChunkStoreRegistry().registerSystem(new ExampleInitializer());
}
```

### Block Module Dependencies

If working with block components, add dependencies in `manifest.json` to ensure proper load order:

```json
{
  "Dependencies": {
    "Hytale:EntityModule": "*",
    "Hytale:BlockModule": "*"
  }
}
```

Without these, you'll get `NullPointerException: Cannot invoke "Query.validateRegistry"` on startup.

---

## Best Practices

1. **Never store direct entity references** — always use `Ref<EntityStore>` handles
2. **Use CommandBuffer** for all entity/component mutations (thread safety)
3. **Keep components as pure data** — no logic in components
4. **Store ComponentType as a static field** on the component class for easy access
5. **Use queries to filter** — don't check component existence inside `tick()`
6. **Use SystemGroups/Dependencies** to control execution order
7. **Use `dt` (delta time)** for time-based logic — don't count ticks
8. **Register components in `setup()`** and systems in `start()`
9. **Use `world.execute(() -> ...)`** when calling world/store functions from block systems, AND when you need atomic check-then-mutate operations spanning multiple stores (entity + chunk) — e.g. claim-then-assign
10. **Reference `FarmingSystems.Ticking`** in Hytale source for block ticking patterns
11. **Block entities are not auto-created** — `BlockModule.getBlockEntity()` is lookup-only; create manually with `ChunkStore.REGISTRY.newHolder()` when needed
12. **Guard `ref.isValid()` in `onComponentRemoved`** — block break fires `removeEntity` which cascades `onComponentRemoved` on all components; the ref is dead before your callback runs
13. **Use `store.getArchetype(ref).length()`** to inspect how many component types an entity currently has — useful for detecting placeholder entities with no meaningful data

## External References

- [ECS Introduction](https://hytalemodding.dev/en/docs/guides/ecs/entity-component-system)
- [Hytale ECS Theory](https://hytalemodding.dev/en/docs/guides/ecs/hytale-ecs-theory)
- [Systems Guide](https://hytalemodding.dev/en/docs/guides/ecs/systems)
- [Example ECS Plugin](https://hytalemodding.dev/en/docs/guides/ecs/example-ecs-plugin)
- [Block Components](https://hytalemodding.dev/en/docs/guides/ecs/block-components)
```
