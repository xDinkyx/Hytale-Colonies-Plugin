---
name: hytale-items
description: Documents Hytale's item system including the Item Registry API, custom item JSON definitions, crafting recipes, and the powerful data-driven Interaction system for creating custom item behaviors. Covers custom Java interactions (SimpleInstantInteraction), linking interactions to items, and chaining interactions using flow control (Condition, Serial), charging, and more. Use when creating custom items, querying the item registry, defining crafting recipes, building item interactions, or working with ItemStack. Triggers - item, custom item, item registry, Item.getAssetMap, DefaultAssetMap, ItemStack, item JSON, item definition, crafting recipe, interaction, SimpleInstantInteraction, InteractionContext, InteractionType, item interaction, Charging, Condition, Serial, Replace, item properties, MaxStack, Categories, item ID.
references:
  - ./references/interaction-reference.md
---

# Hytale Items & Interactions

Comprehensive reference for creating custom items, querying the item registry, defining crafting recipes, and building custom behaviors using Hytale's data-driven Interaction system.

> **Related skills:** For persistent data/Codec patterns, see `hytale-persistent-data`. For ECS fundamentals, see `hytale-ecs`. For inventory management, see `hytale-inventory`. For entity effects applied by items, see `hytale-entity-effects`.

## Quick Reference

| Task | Approach |
|------|----------|
| Get item registry | `Item.getAssetMap()` returns `DefaultAssetMap<String, Item>` |
| Check if item exists | `assetMap.getAsset(id)` — check for `null` and `Item.UNKNOWN` |
| Get item properties | `item.getId()`, `item.getMaxStack()`, `item.hasBlockType()`, `item.isConsumable()` |
| List all items | `Item.getAssetMap().getAssetMap().entrySet()` |
| Define custom item | JSON in `Server/Item/Items/<name>.json` |
| Define crafting recipe | `"Recipe"` block inside item JSON |
| Create custom Java interaction | Extend `SimpleInstantInteraction`, add `BuilderCodec`, register in `setup()` |
| Link interaction to item | `"Interactions"` block in item JSON with interaction type ID |
| Chain interactions | Use flow control interactions like `Serial`, `Condition`, `Parallel`. |
| Create charging ability | Use the `Charging` interaction. |
| Damage an entity | Use the `DamageEntity` interaction. |
| Apply a status effect | Use the `ApplyEffect` interaction. |

---

## Item Registry API

### Required Imports

```java
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
```

### Getting the Registry

```java
DefaultAssetMap<String, Item> itemMap = Item.getAssetMap();
```

### Listing All Items

```java
DefaultAssetMap<String, Item> itemMap = Item.getAssetMap();
var map = itemMap.getAssetMap();

for (var entry : map.entrySet()) {
    String itemId = String.valueOf(entry.getKey());
    Item item = entry.getValue();
    LOGGER.atInfo().log("Item ID: " + itemId);
}

int totalItems = map.size();
```

### Checking if an Item Exists

```java
public boolean itemExists(String itemId) {
    var assetMap = Item.getAssetMap();
    if (assetMap != null) {
        Item item = assetMap.getAsset(itemId);
        return item != null && item != Item.UNKNOWN;
    }
    return false;
}
```

> **Important:** `Item.UNKNOWN` represents an invalid or unrecognized item. Always check for both `null` and `Item.UNKNOWN` when validating items.

### Accessing Item Properties

```java
var assetMap = Item.getAssetMap();
Item item = assetMap.getAsset("Soil_Grass");

if (item != null && item != Item.UNKNOWN) {
    String id = item.getId();              // "Soil_Grass"
    int maxStack = item.getMaxStack();     // 100
    boolean isBlock = item.hasBlockType(); // true
    boolean isConsumable = item.isConsumable(); // false
}
```

---

## Custom Item Definition

### Folder Structure

Enable asset packs in `manifest.json` by setting `IncludesAssetPack` to `true`, then create:

```
resources/
├── Server/
│   └── Item/
│       └── Items/
│           └── my_new_item.json          # Item definition
└── Common/
    ├── Icons/
    │   └── ItemsGenerated/
    │       └── my_new_item_icon.png      # Inventory icon
    └── Items/
        └── my_new_item/
            ├── model.blockymodel         # 3D model
            └── model_texture.png         # Model texture
```

| File | Location | Purpose |
|------|----------|---------|
| `my_new_item.json` | `Server/Item/Items` | Defines item properties and behavior |
| `my_new_item_icon.png` | `Common/Icons/ItemsGenerated` | Icon for the item in inventory |
| `model.blockymodel` | `Common/Items/my_new_item` | 3D model of the item |
| `model_texture.png` | `Common/Items/my_new_item` | Texture for the item model |

### Item JSON Schema

```json
{
  "TranslationProperties": {
    "Name": "My New Item",
    "Description": "My New Item Description"
  },
  "Id": "My_New_Item",
  "Icon": "Icons/ItemsGenerated/my_new_item_icon.png",
  "Model": "Items/my_new_item/model.blockymodel",
  "Texture": "Items/my_new_item/model_texture.png",
  "Quality": "Common",
  "MaxStack": 1,
  "Categories": [
    "Items.Example"
  ]
}
```

### Key Item Properties

| Property | Type | Description |
|----------|------|-------------|
| `Id` | `String` | Unique identifier for the item (used in registry lookups) |
| `TranslationProperties.Name` | `String` | Display name (or localization key) |
| `TranslationProperties.Description` | `String` | Item description (or localization key) |
| `Icon` | `String` | Path to inventory icon (relative to `Common/`) |
| `Model` | `String` | Path to 3D model file (relative to `Common/`) |
| `Texture` | `String` | Path to model texture (relative to `Common/`) |
| `Quality` | `String` | Item quality/rarity tier (e.g., `"Common"`) |
| `MaxStack` | `int` | Maximum stack size |
| `Categories` | `String[]` | Tags/categories for the item |

---

## Crafting Recipes

Add a `"Recipe"` block to the item JSON to make it craftable:

```json
{
  "TranslationProperties": { "Name": "My New Item" },
  "Id": "My_New_Item",
  "Icon": "Icons/ItemsGenerated/my_new_item_icon.png",
  "Model": "Items/my_new_item/model.blockymodel",
  "Texture": "Items/my_new_item/model_texture.png",
  "Quality": "Common",
  "MaxStack": 1,
  "Categories": ["Items.Example"],

  "Recipe": {
    "TimeSeconds": 3.5,
    "Input": [
      { "ItemId": "Ingredient_1", "Quantity": 15 },
      { "ItemId": "Ingredient_2", "Quantity": 15 },
      { "ItemId": "Ingredient_3", "Quantity": 15 }
    ],
    "BenchRequirement": [
      {
        "Id": "Workbench",
        "Type": "Crafting",
        "Categories": ["Workbench_Survival"]
      }
    ]
  }
}
```

### Recipe Properties

| Property | Type | Description |
|----------|------|-------------|
| `TimeSeconds` | `float` | Time in seconds to craft the item |
| `Input` | `Array` | List of ingredient items with `ItemId` and `Quantity` |
| `BenchRequirement` | `Array` | Required crafting station(s) |
| `BenchRequirement[].Id` | `String` | Bench identifier |
| `BenchRequirement[].Type` | `String` | Bench type (e.g., `"Crafting"`) |
| `BenchRequirement[].Categories` | `String[]` | Which bench tab/category |

---

## Interaction System

Hytale features a powerful, data-driven Interaction system that allows you to define complex item behaviors entirely in JSON. While you can create custom interactions in Java, most behaviors can be achieved by combining the built-in interaction types.

### Interaction Reference

For a complete list of all available interaction types, their fields, and how they work, see the **[Interaction Reference](./references/interaction-reference.md)**.

This reference covers:
*   **Flow Control**: `Condition`, `Serial`, `Parallel`, `Selector`, etc.
*   **Combo Chains**: `Chaining`, `CancelChain`.
*   **Charging**: `Charging`, `Wielding`.
*   **Block Interactions**: `PlaceBlock`, `BreakBlock`, `ChangeState`.
*   **Item Interactions**: `ModifyInventory`, `EquipItem`.
*   **Entity Interactions**: `DamageEntity`, `Projectile`, `ApplyEffect`.
*   **And many more.**

### Linking Interactions to Items

To make an item do something, you link it to a `RootInteraction` in its JSON file. The key (`"Secondary"`, `"Primary"`, `"Ability1"`, etc.) determines which player action triggers the interaction chain.

```json
{
  "Id": "My_New_Item",
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "SendMessage",
          "Message": "You used the secondary action!"
        }
      ]
    }
  }
}
```

### Chaining Interactions

You can create complex behaviors by nesting interactions. For example, to require a player to be crouching before sending a message:

```json
{
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "Condition",
          "Crouching": true,
          "Next": {
            "Type": "SendMessage",
            "Message": "You used the item while crouching!"
          },
          "Failed": {
            "Type": "SendMessage",
            "Message": "You must be crouching to use this."
          }
        }
      ]
    }
  }
}
```

---

## Custom Java Interactions

For behaviors that cannot be achieved with the built-in JSON interactions, you can create your own in Java.

### Step 1: Create the Interaction Class

Extend `SimpleInstantInteraction` and override `firstRun`:

```java
import com.hypixel.hytale.server.core.asset.type.item.interaction.SimpleInstantInteraction;
// ... other imports

public class MyCustomInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<MyCustomInteraction> CODEC = BuilderCodec.builder(
            MyCustomInteraction.class, MyCustomInteraction::new, SimpleInstantInteraction.CODEC
    ).build();

    @Override
    protected void firstRun(
            @Nonnull InteractionType interactionType,
            @Nonnull InteractionContext interactionContext,
            @Nonnull CooldownHandler cooldownHandler) {
        // Custom behavior when the item is used
        Player player = interactionContext.getCommandBuffer().getComponent(interactionContext.getEntity(), Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw("Custom Java interaction executed!"));
        }
    }
}
```

### Step 2: Register the Interaction

Register your custom interaction in your plugin's `setup()` method. This makes the `Type` available in JSON.

```java
public class MyPlugin extends JavaPlugin {
    // ...
    @Override
    protected void setup() {
        this.getCodecRegistry(Interaction.CODEC)
            .register("my_custom_interaction_id", MyCustomInteraction.class, MyCustomInteraction.CODEC);
    }
}
```

### Step 3: Link to Item JSON

Use the registered ID in your item's interaction block:

```json
{
  "Id": "My_New_Item",
  "Interactions": {
    "Secondary": {
      "Interactions": [
        {
          "Type": "my_custom_interaction_id"
        }
      ]
    }
  }
}
```

### Custom Fields in Java Interactions

To pass data from JSON to your Java interaction, add fields to the `BuilderCodec`.

```java
public class MyParameterizedInteraction extends SimpleInstantInteraction {

    private float radius;
    private String effectId;

    public static final BuilderCodec<MyParameterizedInteraction> CODEC = BuilderCodec.builder(
            MyParameterizedInteraction.class, MyParameterizedInteraction::new, SimpleInstantInteraction.CODEC
    )
    .add("Radius", KeyedCodec.FLOAT, i -> i.radius, (i, v) -> i.radius = v)
    .add("EffectId", KeyedCodec.STRING, i -> i.effectId, (i, v) -> i.effectId = v)
    .build();

    // ... firstRun implementation can now use this.radius and this.effectId
}
```

JSON usage:
```json
{
  "Type": "my_parameterized_interaction",
  "Radius": 5.0,
  "EffectId": "Burn"
}
```

> **See also:** `hytale-persistent-data` skill for a full `BuilderCodec`/`KeyedCodec` reference.

---

## Common Patterns

### Defensive Null Checks in Interactions

Always validate `CommandBuffer`, `Player`, and `ItemStack` before using them in Java interactions:

```java
@Override
protected void firstRun(@Nonnull InteractionType type,
                         @Nonnull InteractionContext ctx,
                         @Nonnull CooldownHandler cooldown) {
    CommandBuffer<EntityStore> cb = ctx.getCommandBuffer();
    if (cb == null) {
        ctx.getState().state = InteractionState.Failed;
        return;
    }

    Player player = cb.getComponent(ctx.getEntity(), Player.getComponentType());
    if (player == null) {
        ctx.getState().state = InteractionState.Failed;
        return;
    }

    ItemStack held = ctx.getHeldItem();
    if (held == null) {
        ctx.getState().state = InteractionState.Failed;
        return;
    }

    // Safe to proceed
}
```

### Looking Up Items at Runtime

```java
// Validate an item ID string at runtime
public Item resolveItem(String itemId) {
    var assetMap = Item.getAssetMap();
    if (assetMap == null) return null;
    Item item = assetMap.getAsset(itemId);
    if (item == null || item == Item.UNKNOWN) return null;
    return item;
}
```

---

## Checklist: Creating a Custom Item

1. [ ] Set `IncludesAssetPack: true` in `manifest.json`
2. [ ] Create item JSON in `Server/Item/Items/<name>.json`
3. [ ] Create inventory icon in `Common/Icons/ItemsGenerated/`
4. [ ] Create 3D model in `Common/Items/<name>/model.blockymodel`
5. [ ] Create model texture in `Common/Items/<name>/model_texture.png`
6. [ ] (Optional) Add `"Recipe"` block for crafting
7. [ ] (Optional) Add `"Interactions"` block using built-in JSON interactions.
8. [ ] (Optional) For advanced use cases, create a Java interaction class extending `SimpleInstantInteraction`.
9. [ ] (Optional) If using Java, register the interaction in plugin `setup()` via `getCodecRegistry(Interaction.CODEC).register(...)`.
10. [ ] (Optional) If using Java, link the interaction in item JSON via its registered ID.