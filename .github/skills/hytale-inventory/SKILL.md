---
name: hytale-inventory
description: Manages player inventories in Hytale plugins using InventoryComponent, ItemStack, ItemContainer, ItemContainerBlock, and PageManager APIs. Use when accessing player inventory, creating items, adding/removing items from slots, opening inventory pages, setting durability, attaching custom metadata to items, or accessing block-based container inventories. Triggers - inventory, ItemStack, ItemContainer, InventoryComponent, Hotbar, Storage, Armor, Inventory, getInventory, addItemStack, removeItemStack, moveItemStackFromSlot, countItemStacks, ItemStackTransaction, PageManager, Page, ContainerBlockWindow, BlockModule, ItemContainerBlock, spatial, getItemContainer, durability, item metadata, BsonDocument, slot, inventory page.
---

# Hytale Inventory Management

Use this skill when managing player inventories, creating items, opening inventory pages, or manipulating item containers in Hytale plugins.

> **Related skills:** For persistent player data, see `hytale-persistent-data`. For notifications with item icons, see `hytale-notifications`. For hotbar key actions, see `hytale-hotbar-actions`. For inventory change events, see `hytale-events`.

---

## Quick Reference

| Task | Approach |
|------|----------|
| Get player hotbar container | `store.getComponent(ref, InventoryComponent.Hotbar.getComponentType()).getInventory()` |
| Get player storage container | `store.getComponent(ref, InventoryComponent.Storage.getComponentType()).getInventory()` |
| Get combined hotbar+storage | `InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST)` |
| Get item in hand | `InventoryComponent.getItemInHand(store, ref)` |
| Create an item | `new ItemStack("Stone")` or `new ItemStack("Stone", 64)` |
| Create item with metadata | `new ItemStack("Stone", 64, bsonDocument)` |
| Create item with durability | `new ItemStack(itemId, qty, durability, maxDurability, metadata)` |
| Add item to container | `container.addItemStack(itemStack)` — returns `ItemStackTransaction` |
| Add item to specific slot | `container.addItemStackToSlot((short) slot, itemStack)` |
| Remove item from slot | `container.removeItemStackFromSlot((short) slot)` |
| Get item at slot | `container.getItemStack((short) slot)` |
| Move item to another container | `sourceContainer.moveItemStackFromSlot(slot, destContainer)` |
| Count items matching predicate | `container.countItemStacks(stack -> ...)` — returns `int` |
| Open an inventory page | `pageManager.setPage(ref, store, Page.Inventory)` |
| Open container UI for player | `pageManager.setPageWithWindows(ref, store, Page.Bench, false, containerBlockWindow)` |
| Access block container | `ItemContainerBlock.getComponentType()` via `BlockModule.get()` |

---

## Required Imports

```java
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerBlockWindow;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Ref;
import org.bson.BsonDocument;
import org.bson.BsonString;

// Legacy (migration only)
import com.hypixel.hytale.server.core.inventory.Inventory; // @Deprecated(forRemoval = true)
```

---

> **CRITICAL DEPRECATION:** `Inventory` (`com.hypixel.hytale.server.core.inventory.Inventory`) is `@Deprecated(forRemoval = true)`. Do NOT use `player.getInventory()`, `Inventory.getItemInHand()`, or `Inventory.setActiveHotbarSlot()`. Use the `InventoryComponent` API below.

---

## InventoryComponent API (Preferred)

`InventoryComponent` is an abstract ECS component with concrete subclasses for each inventory section. Each subclass has a static `getComponentType()` and an instance `getInventory()` that returns the `ItemContainer`.

### Getting Inventory Containers

```java
Store<EntityStore> store = world.getEntityStore().getStore();
Ref<EntityStore> ref = player.getReference();

// Get a single container
InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
ItemContainer hotbarContainer = hotbar.getInventory(); // null-check hotbar first

InventoryComponent.Storage storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
ItemContainer storageContainer = storage.getInventory();

InventoryComponent.Armor armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType());
ItemContainer armorContainer = armor.getInventory();
```

### Combined Containers

Use `InventoryComponent.getCombined(...)` (static) for operations spanning multiple inventory sections. The static arrays define priority order (e.g. `HOTBAR_FIRST` tries hotbar slots before storage).

```java
// Combined view: hotbar first, then storage
CombinedItemContainer combined = InventoryComponent.getCombined(store, ref, InventoryComponent.HOTBAR_FIRST);

// Other pre-defined orderings:
// InventoryComponent.STORAGE_FIRST
// InventoryComponent.EVERYTHING   (armor + hotbar + utility + storage + backpack)
// InventoryComponent.ARMOR_HOTBAR_UTILITY_STORAGE
// InventoryComponent.HOTBAR_STORAGE_BACKPACK
```

### Item In Hand

```java
// Returns active tool-slot item if in tool mode, otherwise active hotbar item
ItemStack held = InventoryComponent.getItemInHand(store, ref); // nullable
```

---

## Accessing the Player Inventory (Legacy)

> This section documents the deprecated `Inventory` wrapper for reference when migrating existing code. Do not use in new code.

```java
// DEPRECATED - do not use in new code
Inventory inventory = player.getInventory();
```

---

## ItemStack

`ItemStack` represents a stack of items with a material type, quantity, optional metadata, and optional durability.

### Creating an ItemStack

```java
// Basic item (quantity defaults to 1)
ItemStack item = new ItemStack("Stone");

// Item with quantity
ItemStack stack = new ItemStack("Stone", 64);
```

### ItemStack with Custom Metadata

Attach arbitrary BSON metadata to items:

```java
BsonDocument metadata = new BsonDocument();
metadata.append("customData", new BsonString("value"));

ItemStack item = new ItemStack("Stone", 64, metadata);
```

### ItemStack with Durability

Create items that have durability (e.g., tools, weapons):

```java
ItemStack sword = new ItemStack(
    "DiamondSword",  // itemId
    1,               // quantity
    100.0,           // durability
    100.0,           // maxDurability
    metadata         // metadata (optional, can be null)
);
```

| Constructor Parameter | Type | Description |
|----------------------|------|-------------|
| `itemId` | `String` | The item identifier (e.g., `"Stone"`, `"DiamondSword"`) |
| `quantity` | `int` | Number of items in the stack |
| `durability` | `double` | Current durability value |
| `maxDurability` | `double` | Maximum durability value |
| `metadata` | `BsonDocument` | Optional custom metadata (nullable) |

---

## ItemContainer

`ItemContainer` represents a specific section of inventory (storage, hotbar, armor, etc.). All add/remove operations are performed on an `ItemContainer`.

### Getting an ItemContainer

Use `InventoryComponent` subclass instances (preferred). The legacy `Inventory` wrapper is deprecated.

| InventoryComponent subclass | `getComponentType()` call | Description |
|-----------------------------|--------------------------|-------------|
| `InventoryComponent.Hotbar` | `Hotbar.getComponentType()` | Hotbar slots |
| `InventoryComponent.Storage` | `Storage.getComponentType()` | Main storage grid |
| `InventoryComponent.Armor` | `Armor.getComponentType()` | Armor equipment slots |
| `InventoryComponent.Utility` | `Utility.getComponentType()` | Utility slots |
| `InventoryComponent.Backpack` | `Backpack.getComponentType()` | Backpack slots |
| `InventoryComponent.Tool` | `Tool.getComponentType()` | Tool slots |

### Combined Containers

Use the static `InventoryComponent.getCombined(store, ref, types[])` with a pre-defined type array:

| Array constant | Combines (priority order) |
|----------------|---------------------------|
| `HOTBAR_FIRST` | Hotbar → Storage |
| `STORAGE_FIRST` | Storage → Hotbar |
| `HOTBAR_STORAGE_BACKPACK` | Hotbar → Storage → Backpack |
| `BACKPACK_STORAGE_HOTBAR` | Backpack → Storage → Hotbar |
| `ARMOR_HOTBAR_UTILITY_STORAGE` | Armor → Hotbar → Utility → Storage |
| `HOTBAR_UTILITY_CONSUMABLE_STORAGE` | Hotbar → Utility → Storage |
| `EVERYTHING` | Armor → Hotbar → Utility → Storage → Backpack |

---

## Adding Items

`addItemStack` returns an `ItemStackTransaction`. Check `getRemainder()` to see how many items could not fit.

### Add to First Available Slot

```java
InventoryComponent.Storage storageComp = store.getComponent(ref, InventoryComponent.Storage.getComponentType());
ItemContainer storage = storageComp.getInventory();

ItemStack item = new ItemStack("Stone", 64);
ItemStackTransaction tx = storage.addItemStack(item);
if (!ItemStack.isEmpty(tx.getRemainder())) {
    // some items did not fit — handle remainder (e.g. drop to world)
}
```

### Add to Specific Slot

```java
storage.addItemStackToSlot((short) 4, new ItemStack("Stone", 32));
```

---

## Removing Items

### Remove from Specific Slot

```java
ItemContainer storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType()).getInventory();
storage.removeItemStackFromSlot((short) 4);
```

---

## Iterating and Querying

### Iterate All Slots

```java
ItemContainer container = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType()).getInventory();
for (short i = 0; i < container.getCapacity(); i++) {
    ItemStack stack = container.getItemStack(i); // returns null for empty slots
    if (!ItemStack.isEmpty(stack)) {
        // process stack
    }
}
```

### Count Items Matching a Predicate

```java
int woodCount = container.countItemStacks(stack -> stack.getItemId().equals("Wood"));
```

### Move Item to Another Container

`moveItemStackFromSlot` takes the source slot and destination container. Any remainder that does not fit is returned to the source slot automatically.

```java
// Move item at slot 0 in hotbar to storage
ItemContainer hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType()).getInventory();
ItemContainer storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType()).getInventory();
hotbar.moveItemStackFromSlot((short) 0, storage);
```

---

## Block Containers (ItemContainerBlock)

Block-based containers (chests, crates, storage blocks) use `ItemContainerBlock` on `ChunkStore`. Use the spatial resource to find nearby containers.

### Find Nearby Container Blocks

```java
// Get the spatial resource for item-container blocks
Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
SpatialResource<Ref<ChunkStore>, ChunkStore> spatialResource =
    chunkStore.getResource(BlockModule.get().getItemContainerSpatialResourceType());

// collect() fills a thread-local list ordered by insertion — use for unordered scan
List<Ref<ChunkStore>> results = SpatialResource.getThreadLocalReferenceList();
spatialResource.getSpatialStructure().collect(center, radius, results);

// ordered3DAxis() fills ordered by axis distance — use when order matters
// spatialResource.getSpatialStructure().ordered3DAxis(center, xRadius, yRadius, zRadius, results);

for (Ref<ChunkStore> containerRef : results) {
    if (!containerRef.isValid()) continue;
    ItemContainerBlock containerBlock = chunkStore.getComponent(
        containerRef, ItemContainerBlock.getComponentType()
    );
    if (containerBlock == null) continue;
    // getItemContainer() creates the container if not yet initialized
    SimpleItemContainer container = containerBlock.getItemContainer();
    // use container ...
}
```

### Open a Block Container UI for a Player

`ContainerBlockWindow` requires the block's world position, rotation index, block type, and container.

```java
// Requires: block position (x, y, z), rotation index, BlockType, and the ItemContainer
ContainerBlockWindow window = new ContainerBlockWindow(
    x, y, z,
    rotationIndex,
    blockType,      // from block's BlockTypeComponent
    containerBlock.getItemContainer()
);

PageManager pageManager = player.getPageManager();
pageManager.setPageWithWindows(
    player.getReference(),
    store,
    Page.Bench,
    false,          // canCloseThroughInteraction
    window
);
```

---

## Opening Inventory Pages

Use `PageManager` and the `Page` enum to open inventory UI screens for a player.

### Available Pages

| Page | Description |
|------|-------------|
| `Page.None` | Close any open page |
| `Page.Bench` | Crafting bench |
| `Page.Inventory` | Player inventory screen |
| `Page.ToolsSettings` | Tools/settings page |
| `Page.Map` | Map page |
| `Page.MachinimaEditor` | Machinima editor |
| `Page.ContentCreation` | Content creation page |
| `Page.Custom` | Custom page (for plugin UIs) |
| `Page.Serverside` | Server-side page (value 8) |

### Opening a Page

```java
PageManager pageManager = player.getPageManager();
Store<EntityStore> store = player.getWorld().getEntityStore().getStore();

pageManager.setPage(player.getReference(), store, Page.Inventory);
```

### Closing a Page

```java
PageManager pageManager = player.getPageManager();
Store<EntityStore> store = player.getWorld().getEntityStore().getStore();

pageManager.setPage(player.getReference(), store, Page.None);
```

---

## Common Patterns

### Give Items on Event

```java
public void onPlayerReady(PlayerReadyEvent event) {
    var player = event.getPlayer();
    Store<EntityStore> store = player.getWorld().getEntityStore().getStore();
    Ref<EntityStore> ref = player.getReference();

    ItemContainer hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType()).getInventory();
    hotbar.addItemStackToSlot((short) 0, new ItemStack("Weapon_Sword_Iron", 1));
    hotbar.addItemStackToSlot((short) 1, new ItemStack("Tool_Pickaxe_Iron", 1));
    hotbar.addItemStackToSlot((short) 2, new ItemStack("Food_Apple", 16));
}
```

### Clear Inventory

```java
public void clearPlayerInventory(Player player, Store<EntityStore> store) {
    Ref<EntityStore> ref = player.getReference();
    ItemContainer storage = store.getComponent(ref, InventoryComponent.Storage.getComponentType()).getInventory();
    ItemContainer hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType()).getInventory();
    ItemContainer armor = store.getComponent(ref, InventoryComponent.Armor.getComponentType()).getInventory();

    for (short i = 0; i < storage.getCapacity(); i++) storage.removeItemStackFromSlot(i);
    for (short i = 0; i < hotbar.getCapacity(); i++) hotbar.removeItemStackFromSlot(i);
    for (short i = 0; i < armor.getCapacity(); i++) armor.removeItemStackFromSlot(i);
}
```

### Give Item with Metadata

```java
public void giveCustomItem(Player player, Store<EntityStore> store, String itemId, String customTag, String value) {
    BsonDocument metadata = new BsonDocument();
    metadata.append(customTag, new BsonString(value));

    ItemStack item = new ItemStack(itemId, 1, metadata);
    ItemContainer storage = store.getComponent(player.getReference(), InventoryComponent.Storage.getComponentType()).getInventory();
    storage.addItemStack(item);
}
```

### Give Durable Tool

```java
public void giveTool(Player player, Store<EntityStore> store, String toolId, double maxDurability) {
    ItemStack tool = new ItemStack(toolId, 1, maxDurability, maxDurability, null);
    ItemContainer hotbar = store.getComponent(player.getReference(), InventoryComponent.Hotbar.getComponentType()).getInventory();
    hotbar.addItemStack(tool);
}
```

---

## Related Events

| Event | Fires When |
|-------|------------|
| `LivingEntityInventoryChangeEvent` | An entity's inventory changes |
| `ItemContainerChangeEvent` | An item container is modified |

---

## Best Practices

1. **Prefer `InventoryComponent` over `Inventory`** — `Inventory` is `@Deprecated(forRemoval = true)`; all new code must use the ECS component API.
2. **Use the correct container** — Add items to the appropriate container (hotbar for tools, armor for equipment, storage for general items).
3. **Use combined containers for searches** — Use `InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING)` to search across all containers.
4. **Cast slot indices to `short`** — `getItemStack`, `addItemStackToSlot`, `removeItemStackFromSlot` all require `(short)` cast.
5. **Check `ItemStackTransaction.getRemainder()`** — After `addItemStack`, any remainder items did not fit and must be handled (drop, return, etc.).
6. **Use `SpatialResource.getThreadLocalReferenceList()`** — Block container spatial searches reuse a thread-local list; call it once per search and iterate immediately before any other spatial call.
7. **Null-check metadata** — The `BsonDocument` metadata parameter is optional and can be `null`.
8. **Use `Page.None` to close** — Always close pages with `Page.None` when done.
9. **Localize item names** — Use translation keys for any user-facing item text.

---

## References

- [Inventory Management Guide](https://hytalemodding.dev/en/docs/guides/plugin/inventory-management)
- [Hotbar Actions Skill](../hytale-hotbar-actions/SKILL.md) — Custom hotbar key handling
- [Notifications Skill](../hytale-notifications/SKILL.md) — Item icons in notifications
- [UI Modding Skill](../hytale-ui-modding/SKILL.md) — Custom pages and UI

## Official Javadoc References

- [`InventoryComponent`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/inventory/InventoryComponent.html) — preferred inventory API (replaces deprecated `Inventory`)
- [`ItemStack`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/inventory/ItemStack.html) — quantified item with optional metadata
- [`ItemContainer`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/inventory/container/ItemContainer.html) — slot-based container operations
- [`ItemContainerBlock`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/modules/block/components/ItemContainerBlock.html) — block-based container component
- [`BlockModule`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/modules/block/BlockModule.html) — provides block component types and spatial resource types
- [`SpatialResource`](https://release.server.docs.hytale.com/com/hypixel/hytale/component/spatial/SpatialResource.html) — thread-local spatial queries
- [`PageManager`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/entity/entities/player/pages/PageManager.html) — open/close UI pages for a player
- [`ContainerBlockWindow`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/entity/entities/player/windows/ContainerBlockWindow.html) — window for displaying block container UI
- [`CustomUIPage`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/entity/entities/player/pages/CustomUIPage.html)
- [`InteractiveCustomUIPage`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/entity/entities/player/pages/InteractiveCustomUIPage.html)

```
