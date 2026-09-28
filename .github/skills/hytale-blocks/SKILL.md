---
name: hytale-blocks
description: Documents how to create custom blocks in Hytale plugins using asset packs and JSON definitions. Use when creating blocks, defining block JSON, configuring block textures, materials, gathering, block types, or setting up block asset folder structure. Triggers - block, create block, custom block, BlockType, block JSON, block definition, block texture, block material, DrawType, Gathering, block creation, asset pack, IncludesAssetPack, block item, Cube block, block sound, block particle.
references:
  - animated-textures.md
---

# Hytale Custom Blocks

Reference for creating custom blocks in Hytale plugins via asset packs and JSON item definitions with `BlockType` configuration.

> **Source:** <https://hytalemodding.dev/en/docs/guides/plugin/creating-block>
> **Related skills:** For block *components* and ECS ticking behavior, see `hytale-ecs`. For items and interactions, see `hytale-items`.

---

## Quick Reference

| Task | Approach |
|------|----------|
| Enable asset packs | Set `"IncludesAssetPack": true` in `manifest.json` |
| Define a block | Create `Server/Item/Items/<name>.json` with a `BlockType` section |
| Set block texture | `"Textures": [{ "All": "BlockTextures/<name>.png" }]` |
| Set block material | `"Material": "Solid"` (or `Liquid`, `NonSolid`, etc.) |
| Set draw type | `"DrawType": "Cube"` (or `Cross`, `Slab`, `Model`) |
| Add localized name | `Server/Languages/en-US/items.lang` → `<name>.name = Display Name` |
| Set gathering/breaking | `"Gathering": { "Breaking": { "GatherType": "...", "ItemId": "..." } }` |
| Set block icon | `"Icon": "Icons/ItemsGenerated/<name>.png"` |
| Create an animated block | Use `DrawType: "Model"` and specify `CustomModel`, `CustomModelAnimation`, and `CustomModelTexture`. See the [Animated Textures](references/animated-textures.md) guide. |

---

## Prerequisites

### Enable Asset Packs

Your plugin's `manifest.json` must declare asset pack inclusion:

```json
{
  "IncludesAssetPack": true,
  "dependencies": ["Hytale:EntityModule", "Hytale:BlockModule"]
}
```

### Folder Structure

```
src/main/resources/
├── manifest.json
├── Server/
│   ├── Item/
│   │   └── Items/
│   │       └── my_new_block.json       # Block definition
│   └── Languages/
│       └── en-US/
│           └── items.lang              # Translations
└── Common/
    ├── Icons/                          # Item icons
    ├── Blocks/
    │   └── my_new_block/
    │       └── model.blockymodel       # Block model
    └── BlockTextures/
        └── my_new_block.png            # Block texture
```

---

## Block Entities vs. Plain Blocks

Not every block has a "block entity" (a `ChunkStore` ECS entity with components). **Plain blocks are just block IDs in the chunk data.** A block entity only exists when one has been explicitly created for that position \u2014 either by the engine (for blocks with engine-managed state) or by a plugin.

- **`BlockModule.getBlockEntity(world, x, y, z)`** \u2014 lookup only. Returns `null` for plain blocks. **Never auto-creates**.
- To attach custom data to a plain block you must create the entity manually. See the `hytale-ecs` skill \u2014 "Block Entity Lookup and Creation".
- To react to a block component's lifecycle (add/remove), use `RefChangeSystem<ChunkStore, YourComponent>`.

---

## Translations

Create `Server/Languages/en-US/items.lang`:

```
my_new_block.name = My New Block
my_new_block.description = My Description
```

> The filename `items` becomes the translation key prefix, so `"items.my_new_block.name"` resolves to `My New Block`.

---

## Block JSON Definition

Create `Server/Item/Items/my_new_block.json`:

```json
{
  "TranslationProperties": {
    "Name": "items.my_new_block.name",
    "Description": "items.my_new_block.description"
  },
  "Id": "My_New_Block",
  "MaxStack": 100,
  "Icon": "Icons/ItemsGenerated/my_new_block.png",
  "Categories": [
    "Blocks.Rocks"
  ],
  "PlayerAnimationsId": "Block",
  "Set": "Rock_Stone",
  "BlockType": {
    "Material": "Solid",
    "DrawType": "Cube",
    "Group": "Stone",
    "Flags": {},
    "Gathering": {
      "Breaking": {
        "GatherType": "Rocks",
        "ItemId": "my_new_block"
      }
    },
    "BlockParticleSetId": "Stone",
    "Textures": [
      {
        "All": "BlockTextures/my_new_block.png"
      }
    ],
    "ParticleColor": "#aeae8c",
    "BlockSoundSetId": "Stone",
    "BlockBreakingDecalId": "Breaking_Decals_Rock"
  },
  "ResourceTypes": [
    {
      "Id": "Rock"
    }
  ]
}
```

---

## Animated Blocks

To create blocks with animated textures, you use a custom model and an animation file instead of static textures. This involves setting `DrawType` to `"Model"` and providing paths to your assets.

For a complete guide, see the [**Animated Block Textures**](references/animated-textures.md) reference.

---

## BlockType Properties

| Property | Description | Examples |
|----------|-------------|---------|
| `Material` | Physics material type | `"Solid"`, `"Liquid"`, `"NonSolid"` |
| `DrawType` | How the block is rendered | `"Cube"`, `"Cross"`, `"Slab"`, `"Model"` |
| `Group` | Block category group | `"Stone"`, `"Wood"`, `"Sand"` |
| `Flags` | Additional block flags | `{}` (empty object for defaults) |
| `Gathering.Breaking.GatherType` | Tool type needed to break | `"Rocks"`, `"Wood"`, `"Sand"` |
| `Gathering.Breaking.Quality` | Minimum tool quality tier (int, default 0). Tool spec quality must be ≥ this. | `0`, `1`, `2` |
| `Gathering.Breaking.ItemId` | Item dropped when broken | ID string matching the block's `Id` |
| `BlockParticleSetId` | Particle effect when breaking | `"Stone"`, `"Wood"`, `"Sand"` |
| `Textures` | Array of texture definitions | See Texture Configuration below |
| `ParticleColor` | Break particle color | Hex color string `"#aeae8c"` |
| `BlockSoundSetId` | Sound set for interactions | `"Stone"`, `"Wood"`, `"Sand"` |
| `BlockBreakingDecalId` | Breaking animation decal | `"Breaking_Decals_Rock"` |
| `CustomModel` | Path to a `.blockymodel` file (used with `DrawType: "Model"`) | `"VFX/Fire/Fire.blockymodel"` |
| `CustomModelAnimation` | Path to a `.blockyanim` file for the model | `"Blocks/Animations/Fire/Fire_Burn.blockyanim"` |
| `CustomModelTexture` | Texture for the custom model | `[{ "Texture": "VFX/Fire/Fire.png", "Weight": 1 }]` |
| `Looping` | Whether a `CustomModelAnimation` should loop | `true` |

### Texture Configuration

For `DrawType: "Cube"`, textures are defined as an array of objects. Use `"All"` to apply one texture to all faces, or specify per-face:

```json
"Textures": [
  {
    "All": "BlockTextures/my_block.png"
  }
]
```

Per-face texturing (when supported):

```json
"Textures": [
  {
    "Top": "BlockTextures/my_block_top.png",
    "Bottom": "BlockTextures/my_block_bottom.png",
    "Side": "BlockTextures/my_block_side.png"
  }
]
```

---

## Item Properties (Top-Level)

These properties are standard item fields that the block also uses:

| Property | Description |
|----------|-------------|
| `TranslationProperties` | `Name` and `Description` translation keys |
| `Id` | Unique identifier for the item/block |
| `MaxStack` | Maximum stack size in inventory |
| `Icon` | Path to inventory icon image |
| `Categories` | Array of category tags (e.g., `"Blocks.Rocks"`) |
| `PlayerAnimationsId` | Animation set when held (e.g., `"Block"`) |
| `Set` | Visual set grouping (e.g., `"Rock_Stone"`) |
| `ResourceTypes` | Array of resource type objects with `Id` field |

---

## Querying Block Data at Runtime

`BlockChunk` (obtained via `chunkStore.getComponent(chunkRef, BlockChunk.getComponentType())`) provides palette-based query methods that are O(unique block types), not O(chunk volume):

```java
// Count all blocks of a specific type in this chunk (palette-based, cheap)
int count = blockChunk.count(blockTypeIndex);

// Map of blockTypeIndex → count for every type present (iterate unique types only)
Int2IntMap counts = blockChunk.blockCounts();

// Set of all block type indices present in the chunk
IntSet presentTypes = blockChunk.blocks();

// Fast existence check — no iteration
boolean hasWood = blockChunk.contains(blockTypeIndex);
```

Use these to cheaply detect whether a chunk's content has changed (e.g. compare a cached count against the current value) before doing an expensive per-block scan.

---

## Edge Cases & Gotchas

- All referenced files (textures, models, icons) must exist at the specified paths or the block will fail to load
- The `Id` field is case-sensitive and must be unique across all items and blocks
- Translation keys follow the pattern `<lang-filename>.<key>.name` — the `.lang` filename is the prefix
- `IncludesAssetPack` must be `true` in manifest — without it, `Common/` assets are ignored
- Block textures go in `Common/BlockTextures/`, not `Common/Textures/`
- The `ItemId` in `Gathering.Breaking` should match the block's `Id` for the block to drop itself when broken
- Check `lib/Server/` for existing block definitions to see all available property values

```

## Official Javadoc References

- [`BlockHarvestUtils`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/modules/interaction/BlockHarvestUtils.html) — utility for damaging, breaking and harvesting blocks
- [`BreakBlockEvent`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/core/event/events/ecs/BreakBlockEvent.html) — fired when a block is broken
- Source: [Creating Blocks Guide](https://hytalemodding.dev/en/docs/guides/plugin/creating-block)

---

## Programmatic Block Access

### Deprecated: World Chunk Accessors

`IWorldChunks` and all `World.getChunk*()` methods are `@Deprecated`. The Javadoc reads: *"Chunk data should be accessed as components from the chunk ref directly."*

**Do not use:** `world.getChunkIfInMemory(...)`, `world.getChunkIfLoaded(...)`, `world.getChunkIfNonTicking(...)`, `world.getChunkAsync(...)`, or `world.getNonTickingChunkAsync(...)`.

Also `@Deprecated(forRemoval = false)`: `BlockChunk.getSectionAtBlockY()`, `BlockChunk.getSectionAtIndex()`, `BlockChunk.getChunkSections()`. Prefer `BlockChunk.getBlock(x, y, z)` instead.

---

### Reading Block Types at Runtime

There is no `world.getBlock(x, y, z)` method. The correct path is:

```java
// 1. Get the chunk store and locate the chunk ref
long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(chunkIndex);
if (chunkRef == null || !chunkRef.isValid()) return; // chunk not loaded

// 2. Get BlockChunk from the chunk entity
BlockChunk blockChunk = chunkStore.getComponent(chunkRef, BlockChunk.getComponentType());
if (blockChunk == null) return;

// 3. Read block id and resolve the asset (pass world X, Y, Z directly)
int blockId = blockChunk.getBlock(x, y, z);
BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
String blockKey = blockType != null ? blockType.getId() : null; // e.g. "Hytale:OakLog_1"
```

`blockChunk.getBlock(x, y, z)` accepts world coordinates. Internally it masks x/z with `SIZE_MASK` so chunk-local offsets are handled automatically.

For just the `BlockType` asset without the int id, `WorldChunk` (a different component on the same chunk entity) exposes `worldChunk.getBlockType(x, y, z)`.

---

### Filler Block Resolution

Large blocks (multi-block structures) occupy filler cells. The filler value is stored in a **separate layer** of `BlockSection`, not as a special block type id. When a target position hits a filler cell, resolve it to the canonical base position:

```java
BlockSection blockSection = blockChunk.getSectionAtBlockY(y); // @Deprecated but still present
BlockBoundingBoxes hitbox = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());

if (blockSection != null && hitbox != null && hitbox.protrudesUnitBox()) {
    int idx = ChunkUtil.indexBlock(x, y, z); // chunk-local index
    int filler = blockSection.getFiller(idx);
    if (filler != FillerBlockUtil.NO_FILLER) {
        int offsetX = FillerBlockUtil.unpackX(filler);
        int offsetY = FillerBlockUtil.unpackY(filler);
        int offsetZ = FillerBlockUtil.unpackZ(filler);
        // Subtract offset to get canonical base position
        Vector3i basePos = new Vector3i(x - offsetX, y - offsetY, z - offsetZ);
    }
}
```

The plugin's `ClaimBlockUtil.resolveCanonicalPosition(world, position)` is a ready-made helper that encapsulates this logic. Prefer it over reimplementing filler resolution.

---

### Block Entity Access via BlockModule

Block entities (ECS entities attached to block positions in `ChunkStore`) are accessed with:

```java
// Returns null if the chunk is not loaded or no entity exists at that position
Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, x, y, z);
if (blockRef != null && blockRef.isValid()) {
    Store<ChunkStore> cs = blockRef.getStore();
    SomeBlockComponent comp = cs.getComponent(blockRef, SomeBlockComponent.getComponentType());
}
```

`BlockModule.getBlockEntity` signature: `@Nullable static Ref<ChunkStore> getBlockEntity(World world, int x, int y, int z)`.

The plugin wraps this in `BlockEntityUtil.getBlockEntityAt(world, pos)` (takes `Vector3i`). Prefer the wrapper to avoid scattering `.x/.y/.z` unpacking.

There is also a typed convenience method: `BlockModule.getComponent(ComponentType, World, int, int, int)` which combines the entity lookup and component fetch in one call.

---

### Creating a New Block Entity

When a plain block needs a new `ChunkStore` entity (e.g., to attach a claim component):

```java
long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(chunkIndex);
Store<ChunkStore> chunkStore = world.getChunkStore().getStore();

BlockComponentChunk bcc = chunkStore.getComponent(chunkRef, BlockComponentChunk.getComponentType());
int blockIndex = ChunkUtil.indexBlockInColumn(x, y, z);
Ref<ChunkStore> blockRef = bcc.getEntityReference(blockIndex);

if (blockRef == null || !blockRef.isValid()) {
    // Plain block -- create a new entity and attach BlockStateInfo as position anchor
    Holder<ChunkStore> holder = ChunkStore.REGISTRY.newHolder();
    holder.put(
        blockStateInfoComponentType,
        new BlockModule.BlockStateInfo(blockIndex, chunkRef)
    );
    chunkStore.addEntity(holder, AddReason.SPAWN);
    blockRef = holder.getRef();
}

// Safe to add components now
commandBuffer.putComponent(blockRef, new ClaimedBlockComponent(uuid, claimType));
```

See `ClaimBlockUtil.claimBlock` in this plugin for the full pattern including guards and debug logging.

---

### BreakBlockEvent and PlaceBlockEvent

Both are `CancellableEcsEvent` subclasses. Subscribe via `EntityEventSystem<EntityStore, BreakBlockEvent>`.

```java
public class OnBlockBreak extends EntityEventSystem<EntityStore, BreakBlockEvent> {
    @Override
    protected void handle(Ref<EntityStore> playerRef, BreakBlockEvent event,
                          Store<EntityStore> store, CommandBuffer<EntityStore> cb) {
        Vector3i pos = event.getTargetBlock();    // NOT getBlockPosition()
        BlockType type = event.getBlockType();     // returns BlockType, not int
        String key = type.getId();                 // e.g. "Hytale:OakLog_1"
        // event.cancel() prevents the break
    }
}
```

`PlaceBlockEvent` follows the same pattern with `PlaceBlockEvent.getTargetBlock()`. It fires **before** placement, so the block is not yet in the world when the handler runs — use `world.execute(...)` if you need to read the new block state.

> **API note:** `BreakBlockEvent.getTargetBlock()` returns the raw hit position, which may be a filler cell for multi-block structures. Call `resolveCanonicalPosition` if you need the base block.
