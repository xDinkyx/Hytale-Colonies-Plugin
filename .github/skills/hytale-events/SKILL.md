---
name: hytale-events
description: Documents Hytale's event system for handling game events in plugins. Covers IEvent (global events), IAsyncEvent (async events), EcsEvent (ECS entity/block events), and custom plugin events (IEvent<KeyType>, EventBus dispatch, EventRegistration unsubscribe). Use when listening to player join/disconnect, chat, crafting, damage, block break/place, entity death, player leaving world, or any server event. Also use when creating your own plugin events to decouple systems. Triggers - event, IEvent, IAsyncEvent, EcsEvent, CancellableEcsEvent, EntityEventSystem, EventRegistry, registerGlobal, registerAsync, PlayerReadyEvent, PlayerDisconnectEvent, PlayerChatEvent, BreakBlockEvent, PlaceBlockEvent, Damage, CraftRecipeEvent, DropItemEvent, DeathSystems, OnDeathSystem, PlayerRemovedFromWorldEvent, player leave world, event handler, event listener, custom event, plugin event, EventBus, EventRegistration, dispatchFor, IEvent<Vector3i>, keyed event, unregister.
---

# Hytale Events Skill

Use this skill when working with events in Hytale plugins. This covers the three event categories: **IEvent** (global synchronous events), **IAsyncEvent** (asynchronous events), and **EcsEvent** (ECS-based entity/block events).

> **Related skills:** For chat-specific event handling, see `hytale-chat-formatting`. For ECS system fundamentals, see `hytale-ecs`. For entity effects triggered by events, see `hytale-entity-effects`.

---

## Quick Reference

| Task | Approach |
|------|----------|
| Listen to player join | `registerGlobal(PlayerReadyEvent.class, handler)` in `setup()` |
| Listen to player disconnect | `registerGlobal(PlayerDisconnectEvent.class, handler)` in `setup()` |
| Listen to player leaving world | `registerGlobal(PlayerRemovedFromWorldEvent.class, handler)` in `setup()` |
| Listen to chat messages | `registerAsync(PlayerChatEvent.class, handler)` in `setup()` |
| Cancel block break | Extend `EntityEventSystem<EntityStore, BreakBlockEvent>`, call `setCancelled(true)` |
| Cancel crafting | Extend `EntityEventSystem<EntityStore, CraftRecipeEvent.Pre>` |
| Handle entity damage | Extend `EntityEventSystem<EntityStore, Damage>` |
| Handle player death | Extend `DeathSystems.OnDeathSystem` |
| Handle block placement | Extend `EntityEventSystem<EntityStore, PlaceBlockEvent>` |
| Register global event | `this.getEventRegistry().registerGlobal(EventClass.class, Handler::method)` |
| Register async event | `this.getEventRegistry().registerAsync(EventClass.class, Handler::method)` |
| Register ECS event system | `this.getEntityStoreRegistry().registerSystem(new MyEventSystem())` in `start()` |
| Create custom plugin event | Implement `IEvent<KeyType>` (or `IEvent<Void>` for global) |
| Dispatch custom event (keyed) | `HytaleServer.get().getEventBus().dispatchFor(MyEvent.class, key).dispatch(event)` |
| Subscribe to custom event (keyed) | `HytaleServer.get().getEventBus().register(MyEvent.class, key, handler)` → `EventRegistration` |
| Unsubscribe | `eventRegistration.unregister()` |

---

## Event Categories

Hytale has three distinct event types, each with different registration patterns:

| Category | Interface | Registration | Cancellable | Use Case |
|----------|-----------|--------------|-------------|----------|
| **IEvent** | `IEvent` | `registerGlobal()` | No | Player join, disconnect, world events, plugin lifecycle |
| **IAsyncEvent** | `IAsyncEvent` | `registerAsync()` | Yes (some) | Chat messages, asset loading |
| **EcsEvent** | `EcsEvent` / `CancellableEcsEvent` | `registerSystem()` | Yes (Cancellable) | Block break/place, damage, crafting, item drops |

---

## IEvent — Global Events

Global events are fired for server-wide occurrences. Register them in your plugin's `setup()` method.

### Registration Pattern

```java
@Override
protected void setup() {
    this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, MyEventHandler::onPlayerReady);
    this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, MyEventHandler::onPlayerDisconnect);
    this.getEventRegistry().registerGlobal(ShutdownEvent.class, MyEventHandler::onShutdown);
}
```

### Handler Class

```java
public class MyEventHandler {
    public static void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(Message.raw("Welcome " + player.getDisplayName()));
    }

    public static void onPlayerDisconnect(PlayerDisconnectEvent event) {
        // event.getRef() returns a Ref<EntityStore> for the disconnecting player
    }

    public static void onShutdown(ShutdownEvent event) {
        // Clean up resources on server shutdown
    }
}
```

### Required Imports (IEvent)

```java
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.event.player.PlayerReadyEvent;
import com.hypixel.hytale.server.event.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.event.ShutdownEvent;
```

### Available IEvent Types

#### Player Events
| Event | Description | Key Methods |
|-------|-------------|-------------|
| `PlayerReadyEvent` | Player finished joining (fully loaded) | `getPlayer()` |
| `PlayerDisconnectEvent` | Player disconnecting | `getRef()` → `Ref<EntityStore>` |
| `PlayerConnectEvent` | Player beginning connection | - |
| `PlayerSetupConnectEvent` | Player setup phase start | - |
| `PlayerSetupDisconnectEvent` | Player setup phase disconnect | - |
| `PlayerMouseButtonEvent` | Player mouse button input | - |
| `PlayerMouseMotionEvent` | Player mouse motion | - |

#### World Events
| Event | Description |
|-------|-------------|
| `AddWorldEvent` | A world is being added |
| `RemoveWorldEvent` | A world is being removed |
| `StartWorldEvent` | A world is starting |
| `AddPlayerToWorldEvent` | Player added to a world |
| `DrainPlayerFromWorldEvent` | Player removed from a world |
| `PlayerRemovedFromWorldEvent` | Player left a world — customizable broadcast message |
| `AllWorldsLoadedEvent` | All worlds finished loading |

#### Lifecycle Events
| Event | Description |
|-------|-------------|
| `BootEvent` | Server boot |
| `ShutdownEvent` | Server shutting down |
| `PluginSetupEvent` | Plugin setup phase |
| `AllNPCsLoadedEvent` | All NPCs finished loading |
| `LoadedNPCEvent` | Individual NPC loaded |

#### Asset Events
| Event | Description |
|-------|-------------|
| `AssetPackRegisterEvent` | Asset pack registered |
| `AssetPackUnregisterEvent` | Asset pack unregistered |
| `RegisterAssetStoreEvent` | Asset store registered |
| `RemoveAssetStoreEvent` | Asset store removed |
| `GenerateAssetsEvent` | Assets being generated |
| `LoadedAssetsEvent` | Assets finished loading |
| `RemovedAssetsEvent` | Assets removed |
| `LoadAssetEvent` | Individual asset loading |

#### Other Events
| Event | Description |
|-------|-------------|
| `EntityRemoveEvent` | Entity removed from world |
| `LivingEntityInventoryChangeEvent` | Entity inventory changed |
| `ItemContainerChangeEvent` | Item container changed |
| `GenerateDefaultLanguageEvent` | Default language generation |
| `GenerateSchemaEvent` | Schema generation |
| `GenerateServerStateEvent` | Server state generation |
| `ChunkPreLoadProcessEvent` | Chunk pre-load processing |
| `TreasureChestOpeningEvent` | Treasure chest opened |
| `WindowCloseEvent` | Window closed |
| `WorldPathChangedEvent` | World path changed |
| `MessagesUpdated` | Messages updated |

---

## IAsyncEvent — Asynchronous Events

Async events run off the main tick thread. Register in `setup()` using `registerAsync()`.

### Registration Pattern

```java
@Override
protected void setup() {
    this.getEventRegistry().registerAsync(PlayerChatEvent.class, MyEventHandler::onPlayerChat);
}
```

### Handler Example

```java
public static void onPlayerChat(PlayerChatEvent event) {
    PlayerRef sender = event.getSender();
    String content = event.getContent();

    // Cancel the message
    if (content.contains("badword")) {
        event.setCancelled(true);
        sender.sendMessage(Message.raw("That word is not allowed!"));
        return;
    }

    // Modify content
    event.setContent(content.toUpperCase());

    // Set custom formatter
    event.setFormatter((playerRef, message) ->
        Message.join(
            Message.raw("[Server] ").color(Color.GOLD),
            Message.raw(sender.getUsername()).color(Color.WHITE),
            Message.raw(": " + message).color(Color.GRAY)
        ));
}
```

### Available IAsyncEvent Types

| Event | Description | Cancellable |
|-------|-------------|-------------|
| `PlayerChatEvent` | Player sends a chat message | Yes |
| `SendCommonAssetsEvent` | Common assets being sent | No |
| `AssetEditorFetchAutoCompleteDataEvent` | Editor autocomplete data | No |
| `AssetEditorRequestDataSetEvent` | Editor data set request | No |

---

## EcsEvent — ECS Entity/Block Events

ECS events are fired within the ECS tick loop and operate on entities matching a query. They use `EntityEventSystem` and are registered as systems.

### Key Difference from IEvent

- **IEvent**: Simple handler function, registered in `setup()`
- **EcsEvent**: Full ECS system class extending `EntityEventSystem`, registered in `start()` via `registerSystem()`
- **EcsEvent** receives `Store`, `CommandBuffer`, and `ArchetypeChunk` — giving full ECS access

### EntityEventSystem Pattern

```java
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Archetype;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.ecs.system.EntityEventSystem;
import javax.annotation.Nonnull;

class MyCraftHandler extends EntityEventSystem<EntityStore, CraftRecipeEvent.Pre> {

    public MyCraftHandler() {
        super(CraftRecipeEvent.Pre.class);
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull CraftRecipeEvent.Pre event) {
        // Access entity ref
        var ref = archetypeChunk.getReferenceTo(index);

        // Access event data
        CraftingRecipe recipe = event.getCraftedRecipe();

        // Cancel if needed (CancellableEcsEvent only)
        event.setCancelled(true);
    }

    @Override
    public Query<EntityStore> getQuery() {
        // Return which entities this system processes
        // Archetype.empty() = all entities that receive this event
        return Archetype.empty();
    }
}
```

### Registration

```java
@Override
protected void start() {
    this.getEntityStoreRegistry().registerSystem(new MyCraftHandler());
}
```

### Required Imports (EcsEvent)

```java
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Archetype;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.ecs.system.EntityEventSystem;
import javax.annotation.Nonnull;
```

### CancellableEcsEvent Types

These events extend `CancellableEcsEvent` and support `setCancelled(true)`:

| Event | Sub-Events | Description |
|-------|------------|-------------|
| `BreakBlockEvent` | — | Block broken by any entity (player or NPC) via `BlockHarvestUtils` |
| `PlaceBlockEvent` | — | Block placed by any entity via `BlockPlaceUtils` — fires **before** placement, block is not yet in the world |
| `ChangeGameModeEvent` | — | Game mode change |
| `ChunkSaveEvent` | — | Chunk saving |
| `ChunkUnloadEvent` | — | Chunk unloading |
| `CraftRecipeEvent` | `.Pre`, `.Post` | Recipe crafted (Pre = before, Post = after) |
| `Damage` | — | Entity taking damage |
| `DamageBlockEvent` | — | Block being damaged |
| `DropItemEvent` | `.Drop`, `.PlayerRequest` | Item dropped |
| `InteractivelyPickupItemEvent` | — | Player picking up an item |
| `PrefabPasteEvent` | — | Prefab being pasted |
| `SwitchActiveSlotEvent` | — | Active hotbar slot switch |

### Non-Cancellable EcsEvent Types

| Event | Sub-Events | Description |
|-------|------------|-------------|
| `DiscoverInstanceEvent` | `.Display` | Instance discovered |
| `DiscoverZoneEvent` | `.Display` | Zone discovered |
| `MoonPhaseChangeEvent` | — | Moon phase changed |
| `UseBlockEvent` | `.Pre`, `.Post` | Block used (Pre = before, Post = after) |

---

## Special Pattern: Death Events

Player/entity death uses a specialized system extending `DeathSystems.OnDeathSystem` (a `RefChangeSystem` under the hood).

### Death System Example

```java
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

public class PlayerDeathHandler extends DeathSystems.OnDeathSystem {

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // Only process player deaths
        return Query.and(Player.getComponentType());
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref ref,
            @Nonnull DeathComponent component,
            @Nonnull Store store,
            @Nonnull CommandBuffer commandBuffer) {

        Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
        assert playerComponent != null;

        Universe.get().sendMessage(
            Message.raw("Player died: " + playerComponent.getDisplayName()));

        // Access death damage info
        Damage deathInfo = component.getDeathInfo();
        if (deathInfo != null) {
            Universe.get().sendMessage(
                Message.raw("Damage amount: " + deathInfo.getAmount()));
        }
    }
}
```

### Death System Registration

```java
@Override
protected void start() {
    this.getEntityStoreRegistry().registerSystem(new PlayerDeathHandler());
}
```

---

## Complete Plugin Example

A full plugin demonstrating all three event categories:

```java
import com.hypixel.hytale.server.plugin.JavaPlugin;
import com.hypixel.hytale.server.plugin.JavaPluginInit;
import com.hypixel.hytale.server.event.player.PlayerReadyEvent;
import com.hypixel.hytale.server.event.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.event.PlayerChatEvent;
import javax.annotation.Nonnull;

public class MyPlugin extends JavaPlugin {

    public MyPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // IEvent — global events
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class,
            EventHandlers::onPlayerReady);
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class,
            EventHandlers::onPlayerDisconnect);

        // IAsyncEvent — async events
        this.getEventRegistry().registerAsync(PlayerChatEvent.class,
            EventHandlers::onPlayerChat);
    }

    @Override
    protected void start() {
        // EcsEvent — ECS event systems
        this.getEntityStoreRegistry().registerSystem(new CraftBlocker());
        this.getEntityStoreRegistry().registerSystem(new PlayerDeathHandler());
    }
}
```

---

## Block Event Caveats

- `BreakBlockEvent` and `PlaceBlockEvent` **only fire when a non-null entity `Ref` is passed** to `BlockHarvestUtils.performBlockBreak` / `BlockPlaceUtils`. This covers players and NPCs using interactions (e.g. `DestroyBlockInteraction`). It does **not** cover:
  - Physics-based removal (`naturallyRemoveBlockByPhysics`) — no entity ref, no event
  - World-generated block changes (`world.setBlock()`) — e.g. sapling growth via `BasicChanceBlockGrowthProcedure`
  - There is no global block-change listener in the API; poll periodically to catch growth events
- `PlaceBlockEvent` fires **before** the block is written to the world. Use `world.execute(() -> { ... })` to schedule logic that needs to read the newly placed block.
- Both event systems are registered on `EntityStore` (not `ChunkStore`) — use `Archetype.empty()` as the query to receive all events regardless of entity type.

---

## Common Patterns

### Cancel an Event Conditionally

```java
// CancellableEcsEvent pattern
@Override
public void handle(int index,
                   @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                   @Nonnull Store<EntityStore> store,
                   @Nonnull CommandBuffer<EntityStore> commandBuffer,
                   @Nonnull BreakBlockEvent event) {
    // Check condition, then cancel
    if (shouldPreventBreak(event)) {
        event.setCancelled(true);
    }
}
```

### Block Crafting by Ingredient

```java
class BlockFibreCrafting extends EntityEventSystem<EntityStore, CraftRecipeEvent.Pre> {

    public BlockFibreCrafting() {
        super(CraftRecipeEvent.Pre.class);
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull CraftRecipeEvent.Pre event) {
        CraftingRecipe recipe = event.getCraftedRecipe();
        if (recipe.getInput() != null) {
            for (MaterialQuantity mq : recipe.getInput()) {
                if (Objects.equals(mq.getItemId(), "Ingredient_Fibre")) {
                    event.setCancelled(true);
                    break;
                }
            }
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
```

### Filter Events by Entity Type

```java
@Override
public Query<EntityStore> getQuery() {
    // Only process for entities with the Player component
    return Query.and(Player.getComponentType());
}
```

### Access Entity Ref from EcsEvent

```java
@Override
public void handle(int index,
                   @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                   @Nonnull Store<EntityStore> store,
                   @Nonnull CommandBuffer<EntityStore> commandBuffer,
                   @Nonnull Damage damageEvent) {
    Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
    // Use ref with store to access components
    Player player = (Player) store.getComponent(ref, Player.getComponentType());
}
```

---

## Custom Plugin Events

Use `IEvent<KeyType>` to define your own plugin events and dispatch them via the global `EventBus`. This replaces any need for custom observer/callback registries.

### When to Use Custom Plugin Events
- Decoupling systems that need to react to plugin-internal state changes (e.g. a UI page refreshing when a colonist is hired/fired)
- Any time you'd otherwise reach for a static map of callbacks

### Two Key Patterns

| Pattern | Key Type | Dispatch key | Use when |
|---------|----------|--------------|----------|
| Global (no key) | `Void` | `null` | Server-wide event, no scoping needed |
| Keyed | e.g. `Vector3i`, `UUID` | a position, UUID, etc. | Only interested listeners receive it |

**Prefer the keyed pattern** — it avoids broadcasting to all listeners and lets each subscriber scope to exactly what they care about (e.g. a specific block position).

### Step 1 — Define the Event

```java
package com.yourplugin.events;

import java.util.UUID;
import javax.annotation.Nonnull;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.math.vector.Vector3i;

/** Keyed by workstation block position so only the relevant UI page reacts. */
public class ColonistHiredEvent implements IEvent<Vector3i> {

    private final UUID colonistUuid;
    private final Vector3i workstationPos;

    public ColonistHiredEvent(@Nonnull UUID colonistUuid, @Nonnull Vector3i workstationPos) {
        this.colonistUuid = colonistUuid;
        this.workstationPos = workstationPos;
    }

    public UUID getColonistUuid() { return colonistUuid; }
    public Vector3i getWorkstationPos() { return workstationPos; }
}
```

- `IEvent<Vector3i>` — keyed by block position; only listeners registered with that key receive the event
- `IEvent<Void>` — for global (un-keyed) events; dispatch with key `null`
- No registration in plugin `setup()` needed — the `EventBus` auto-creates registries on first use

### Step 2 — Dispatch

```java
import com.hypixel.hytale.server.core.HytaleServer;

// Keyed dispatch (pos is the key, only listeners registered for this pos receive it)
HytaleServer.get().getEventBus()
        .dispatchFor(ColonistHiredEvent.class, workstationPos)
        .dispatch(new ColonistHiredEvent(uuid, workstationPos));

// Global dispatch (Void key)
HytaleServer.get().getEventBus()
        .dispatchFor(MyGlobalEvent.class, null)
        .dispatch(new MyGlobalEvent(...));
```

> **Note:** `dispatchFor` returns an `IEventDispatcher`. Call `hasListener()` first only if dispatch is expensive and there are no listeners most of the time.

### Step 3 — Subscribe and Unsubscribe

```java
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.server.core.HytaleServer;

// Subscribe (keyed — only events for this exact blockPos arrive here)
EventRegistration<?, ?> hiredReg = HytaleServer.get().getEventBus()
        .register(ColonistHiredEvent.class, blockPos, event -> {
            // handle event
        });

// Unsubscribe (call when the listener's lifetime ends, e.g. UI page dismissed)
hiredReg.unregister();
```

For global (`Void`-keyed) events, use `registerGlobal()`:

```java
EventRegistration<?, ?> reg = HytaleServer.get().getEventBus()
        .registerGlobal(MyGlobalEvent.class, event -> handle(event));
reg.unregister(); // when done
```

### Lifecycle Pattern — UI Page / Short-Lived Subscriber

Hold `EventRegistration` fields and clean up on dismiss:

```java
private EventRegistration<?, ?> hiredReg;
private EventRegistration<?, ?> firedReg;

@Override
public void build(...) {
    unregisterEventListeners(); // re-register safely if build() called twice
    hiredReg = HytaleServer.get().getEventBus()
            .register(ColonistHiredEvent.class, blockPos, e -> scheduleRefresh());
    firedReg = HytaleServer.get().getEventBus()
            .register(ColonistFiredEvent.class, blockPos, e -> scheduleRefresh());
}

@Override
public void onDismiss(...) {
    unregisterEventListeners();
}

private void unregisterEventListeners() {
    if (hiredReg != null) { hiredReg.unregister(); hiredReg = null; }
    if (firedReg != null) { firedReg.unregister(); firedReg = null; }
}
```

### Key Types

`Vector3i` has `equals`/`hashCode` and works correctly as an event key. Other good key types: `UUID`, `String` (world name — used by built-in events), `Integer`.

### Required Imports

```java
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.server.core.HytaleServer;
```

---

## Choosing the Right Event Type

| Need | Event Category | Registration |
|------|---------------|--------------|
| Player joins / leaves | IEvent | `registerGlobal()` in `setup()` |
| Chat messages | IAsyncEvent | `registerAsync()` in `setup()` |
| Block break / place | EcsEvent | `registerSystem()` in `start()` |
| Damage / combat | EcsEvent | `registerSystem()` in `start()` |
| Crafting | EcsEvent | `registerSystem()` in `start()` |
| Item drops / pickups | EcsEvent | `registerSystem()` in `start()` |
| Entity death | Special (DeathSystems) | `registerSystem()` in `start()` |
| Server shutdown | IEvent | `registerGlobal()` in `setup()` |
| World lifecycle | IEvent | `registerGlobal()` in `setup()` |
| Plugin-internal state change | Custom `IEvent<KeyType>` | `dispatchFor().dispatch()` + `register()` on `HytaleServer.get().getEventBus()` |

---

## Best Practices

1. **Register IEvent/IAsyncEvent in `setup()`** and EcsEvent systems in `start()`
2. **Use method references** for cleaner registration: `EventHandler::onPlayerReady`
3. **Keep event handler classes separate** from the main plugin class for organization
4. **Use `Archetype.empty()`** for the query when you want to process all entities receiving the event
5. **Use `Query.and()`** to filter which entities your EcsEvent system processes
6. **Check `setCancelled()`** only on `CancellableEcsEvent` subclasses — non-cancellable events will not have this method
7. **Use Pre/Post sub-events** when available (e.g., `CraftRecipeEvent.Pre` vs `.Post`) to choose timing
8. **Access entity data via `Store` and `Ref`** in EcsEvent handlers — never cache entity references
9. **Use `CommandBuffer`** for mutations inside EcsEvent handlers (add/remove components)
10. **Death handling** uses `DeathSystems.OnDeathSystem` (a `RefChangeSystem`), not `EntityEventSystem`
11. **Custom plugin events** — prefer `IEvent<KeyType>` on the global `EventBus` over static observer maps or callback registries. Use a keyed type (e.g. `Vector3i`, `UUID`) to scope delivery to only interested listeners. Store the returned `EventRegistration` and call `unregister()` when the subscriber's lifetime ends.

---

## Resources

- [Creating Events Guide](https://hytalemodding.dev/en/docs/guides/plugin/creating-events)
- [Events List](https://hytalemodding.dev/en/docs/server/events)
- [Player Death Event Guide](https://hytalemodding.dev/en/docs/guides/plugin/player-death-event)
- [ECS Systems Guide](https://hytalemodding.dev/en/docs/guides/ecs/systems)

## Official Javadoc References

- [`IAsyncEvent`](https://release.server.docs.hytale.com/com/hypixel/hytale/event/IAsyncEvent.html) — async event interface
- [`EcsEvent`](https://release.server.docs.hytale.com/com/hypixel/hytale/component/system/EcsEvent.html) — ECS-scoped event base
- [`EventPriority`](https://release.server.docs.hytale.com/com/hypixel/hytale/event/EventPriority.html) — listener priority levels
- [`PlayerReadyEvent`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/event/events/player/PlayerReadyEvent.html) — player fully ready in world
- [`PlayerDisconnectEvent`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/event/events/player/PlayerDisconnectEvent.html)
- [`AddPlayerToWorldEvent`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/event/events/player/AddPlayerToWorldEvent.html) — player added to world entity store
- [`BreakBlockEvent`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/event/events/ecs/BreakBlockEvent.html)
- [`DeathSystems.OnDeathSystem`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/modules/entity/damage/DeathSystems.OnDeathSystem.html) — death hook (`RefChangeSystem` pattern)
