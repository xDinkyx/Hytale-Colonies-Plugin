---
name: update-hytale-skills
description: Updates existing hytale-* skills and detects new documentation pages on the HytaleModding site. Checks the GitHub source repo (HytaleModding/site) for content changes, fetches updated MDX source files, and reconciles skill content. Cross-references official server source (hytale-shared-source) as the primary authority for package paths and API correctness. Use after server updates, periodically, or when new modding docs are published. Triggers - update skills, refresh skills, sync docs, check for skill updates, new documentation, skill maintenance, hytalemodding site changes, update modding docs.
---

# Update Hytale Skills

Procedural skill for keeping all `hytale-*` skills in `.github/skills/` synchronized with the **official Hytale server source** (primary authority) and the [HytaleModding community site](https://hytalemodding.dev/en/docs) (secondary reference).

> **Related skills:** `update-server-lib` handles updating `lib/` with the latest Hytale server JAR and source. Run that skill **first** if a new server version is available, then run this skill to update knowledge skills.

> **Authority hierarchy:** Official source in `lib/hytale-shared-source/HytaleServer/` is the **ground truth** for package paths, method signatures, and class names. Community docs may lag behind or contain outdated examples. When they conflict, the official source wins.

---

## When to Use This Skill

- After running `update-server-lib` (new server version deployed) — run a full source audit
- When the HytaleModding documentation site has been updated
- Periodically (e.g., weekly) to catch community doc improvements
- When you notice a skill's code examples or API references are outdated
- When a new guide or doc page appears on hytalemodding.dev that has no matching skill

---

## CRITICAL: Official Source is Now Available

As of 2026, Hypixel Studios released the **official Hytale server shared source** at:
`https://github.com/HypixelStudios/hytale-shared-source`

This is a multi-module Maven project available locally at `lib/hytale-shared-source/HytaleServer/`. It contains full source code with comments and documentation for all server APIs.

**This completely changes the skill update workflow.** The official source is the primary reference — community docs are now a supplementary discovery tool only.

### Verified Correct Package Paths (from official source, 2026-06-25 audit)

These were confirmed during the first full source audit. Overrides any earlier skill content that said otherwise:

| Class | Correct Package |
|-------|----------------|
| `Store` | `com.hypixel.hytale.component.Store` |
| `Ref` | `com.hypixel.hytale.component.Ref` |
| `Holder` | `com.hypixel.hytale.component.Holder` |
| `CommandBuffer` | `com.hypixel.hytale.component.CommandBuffer` |
| `Component` | `com.hypixel.hytale.component.Component` |
| `Query` | `com.hypixel.hytale.component.query.Query` |
| `SystemGroup` | `com.hypixel.hytale.component.SystemGroup` |
| `EntityTickingSystem` | `com.hypixel.hytale.component.system.tick.EntityTickingSystem` |
| `TickingSystem` | `com.hypixel.hytale.component.system.tick.TickingSystem` |
| `DelayedEntitySystem` | `com.hypixel.hytale.component.system.tick.DelayedEntitySystem` |
| `RefSystem` | `com.hypixel.hytale.component.system.RefSystem` |
| `RefChangeSystem` | `com.hypixel.hytale.component.system.RefChangeSystem` |
| `EcsEvent` | `com.hypixel.hytale.component.system.EcsEvent` |
| `CancellableEcsEvent` | `com.hypixel.hytale.component.system.CancellableEcsEvent` |
| `EntityStore` | `com.hypixel.hytale.server.core.universe.world.storage.EntityStore` |
| `ChunkStore` | `com.hypixel.hytale.server.core.universe.world.storage.ChunkStore` |
| `Codec` | `com.hypixel.hytale.codec.Codec` |
| `KeyedCodec` | `com.hypixel.hytale.codec.KeyedCodec` |
| `BuilderCodec` | `com.hypixel.hytale.codec.builder.BuilderCodec` |
| `MapCodec` | `com.hypixel.hytale.codec.codecs.map.MapCodec` |
| `Validators` | `com.hypixel.hytale.codec.validation.Validators` |
| `Player` | `com.hypixel.hytale.server.core.entity.entities.Player` |
| `EntityStatMap` | `com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap` |
| `DefaultEntityStatTypes` | `com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes` |
| `Teleport` | `com.hypixel.hytale.server.core.modules.entity.teleport.Teleport` |
| `DeathSystems` | `com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems` |
| `DeathComponent` | `com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent` |
| `InventoryComponent` | `com.hypixel.hytale.server.core.inventory.InventoryComponent` |
| `Inventory` | `com.hypixel.hytale.server.core.inventory.Inventory` **(DEPRECATED forRemoval)** |
| `ItemStack` | `com.hypixel.hytale.server.core.inventory.ItemStack` |
| `ItemContainer` | `com.hypixel.hytale.server.core.inventory.container.ItemContainer` |
| `Page` (enum) | `com.hypixel.hytale.protocol.packets.interface_.Page` |
| `IEvent` | `com.hypixel.hytale.event.IEvent` |
| `IAsyncEvent` | `com.hypixel.hytale.event.IAsyncEvent` |
| `EventRegistry` | `com.hypixel.hytale.event.EventRegistry` |
| `EventBus` | `com.hypixel.hytale.event.EventBus` |
| `PlayerChatEvent` | `com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent` |
| `HudManager` | `com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager` |
| `CustomUIHud` | `com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud` |
| `PageManager` | `com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager` |
| `CustomUIPage` | `com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage` |
| `BasicCustomUIPage` | `com.hypixel.hytale.server.core.entity.entities.player.pages.BasicCustomUIPage` |
| `InteractiveCustomUIPage` | `com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage` |
| `NotificationUtil` | `com.hypixel.hytale.server.core.util.NotificationUtil` |
| `SoundUtil` | `com.hypixel.hytale.server.core.universe.world.SoundUtil` |
| `Config` | `com.hypixel.hytale.server.core.util.Config` |
| `JavaPlugin` | `com.hypixel.hytale.server.core.plugin.JavaPlugin` |
| `PluginBase` | `com.hypixel.hytale.server.core.plugin.PluginBase` |
| `PermissionsModule` | `com.hypixel.hytale.server.core.permissions.PermissionsModule` |
| `InteractionContext` | `com.hypixel.hytale.server.core.entity.InteractionContext` |
| `SimpleInstantInteraction` | `com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction` |
| `InstancesPlugin` | `com.hypixel.hytale.builtin.instances.InstancesPlugin` |
| `NPCPlugin` | `com.hypixel.hytale.server.npc.NPCPlugin` |
| `NPCEntity` | `com.hypixel.hytale.server.npc.entities.NPCEntity` |
| `INonPlayerCharacter` | `com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter` |
| `ZonePatternGenerator` | `com.hypixel.hytale.server.worldgen.zone.ZonePatternGenerator` |
| `BiomePatternGenerator` | `com.hypixel.hytale.server.worldgen.biome.BiomePatternGenerator` |
| `HytaleLogger` | `com.hypixel.hytale.logger.HytaleLogger` |
| `AssetRegistry` | `com.hypixel.hytale.assetstore.AssetRegistry` |

**Known deprecated APIs:**
- `Inventory` — `@Deprecated(forRemoval = true)`; prefer `InventoryComponent`

---

## Source Repository

All documentation lives in the **HytaleModding/site** GitHub repository:

- **Repo:** `https://github.com/HytaleModding/site`
- **Content root:** `content/docs/en/`
- **Raw content base URL:** `https://raw.githubusercontent.com/HytaleModding/site/main/content/docs/en/`

### Content Directory Structure

```
content/docs/en/
├── guides/
│   ├── ecs/                          # ECS guides
│   │   ├── entity-component-system.mdx
│   │   ├── hytale-ecs-theory.mdx
│   │   ├── systems.mdx
│   │   ├── example-ecs-plugin.mdx
│   │   └── block-components.mdx
│   ├── java-basics/                  # Java tutorial series (skip — not skill material)
│   │   ├── 00-introduction.mdx ... 13-inheritance.mdx
│   ├── plugin/                       # Plugin guides (most skills map here)
│   │   ├── chat-formatting.mdx
│   │   ├── creating-commands.mdx
│   │   ├── creating-configuration-file.mdx
│   │   ├── creating-events.mdx
│   │   ├── customizing-camera-controls.mdx
│   │   ├── customizing-hotbar-actions.mdx
│   │   ├── instances.mdx
│   │   ├── inventory-management.mdx
│   │   ├── item-interaction.mdx
│   │   ├── item-registry.mdx
│   │   ├── listening-to-packets.mdx
│   │   ├── logging.mdx
│   │   ├── permission-management.mdx
│   │   ├── player-death-event.mdx
│   │   ├── player-input-guide.mdx
│   │   ├── player-stats.mdx
│   │   ├── playing-sounds.mdx
│   │   ├── send-notifications.mdx
│   │   ├── spawning-entities.mdx
│   │   ├── spawning-npcs.mdx
│   │   ├── store-persistent-data.mdx
│   │   ├── teleporting-players.mdx
│   │   ├── text-hologram.mdx
│   │   ├── ui.mdx
│   │   ├── world-gen.mdx
│   │   ├── build-and-test.mdx
│   │   ├── browsing-serverjar.mdx
│   │   ├── client-inputs-reference.mdx
│   │   └── creating-block.mdx
│   └── prefabs.mdx
├── official-documentation/
│   ├── custom-ui/                    # Official UI docs
│   │   ├── common-styling.mdx
│   │   ├── layout.mdx
│   │   ├── markup.mdx
│   │   └── type-documentation/       # (subdirectory with type docs)
│   ├── npc/                          # Official NPC template docs (12 chapters)
│   │   ├── 1-know-your-enemy.mdx
│   │   ├── 2-getting-started-with-templates.mdx
│   │   ├── ...through 12-appendix.mdx
│   └── worldgen/                     # Official world gen docs
│       ├── pack-tutorial/
│       ├── technical-hytale-generator/
│       └── worldgen-tutorial/
├── server/                           # Server reference docs
│   ├── entities.mdx
│   ├── events.mdx
│   └── sounds.mdx
└── index.mdx
```

---

## Skill-to-Source Mapping

The table below maps each `hytale-*` skill to its upstream documentation source(s). Use this to know exactly which files to check for updates.

### Skills with Upstream Doc URLs

| Skill | GitHub Source Path(s) | Live URL(s) |
|-------|----------------------|-------------|
| `hytale-camera-controls` | `guides/plugin/customizing-camera-controls.mdx` | [customizing-camera-controls](https://hytalemodding.dev/en/docs/guides/plugin/customizing-camera-controls) |
| `hytale-chat-formatting` | `guides/plugin/chat-formatting.mdx` | [chat-formatting](https://hytalemodding.dev/en/docs/guides/plugin/chat-formatting) |
| `hytale-config-files` | `guides/plugin/creating-configuration-file.mdx` | [creating-configuration-file](https://hytalemodding.dev/en/docs/guides/plugin/creating-configuration-file) |
| `hytale-ecs` | `guides/ecs/entity-component-system.mdx`, `guides/ecs/hytale-ecs-theory.mdx`, `guides/ecs/systems.mdx`, `guides/ecs/example-ecs-plugin.mdx`, `guides/ecs/block-components.mdx` | [entity-component-system](https://hytalemodding.dev/en/docs/guides/ecs/entity-component-system), [hytale-ecs-theory](https://hytalemodding.dev/en/docs/guides/ecs/hytale-ecs-theory), [systems](https://hytalemodding.dev/en/docs/guides/ecs/systems), [example-ecs-plugin](https://hytalemodding.dev/en/docs/guides/ecs/example-ecs-plugin), [block-components](https://hytalemodding.dev/en/docs/guides/ecs/block-components) |
| `hytale-events` | `guides/plugin/creating-events.mdx`, `server/events.mdx` | [creating-events](https://hytalemodding.dev/en/docs/guides/plugin/creating-events), [events](https://hytalemodding.dev/en/docs/server/events) |
| `hytale-hotbar-actions` | `guides/plugin/customizing-hotbar-actions.mdx`, `guides/plugin/listening-to-packets.mdx` | [customizing-hotbar-actions](https://hytalemodding.dev/en/docs/guides/plugin/customizing-hotbar-actions), [listening-to-packets](https://hytalemodding.dev/en/docs/guides/plugin/listening-to-packets) |
| `hytale-instances` | `guides/plugin/instances.mdx` | [instances](https://hytalemodding.dev/en/docs/guides/plugin/instances) |
| `hytale-inventory` | `guides/plugin/inventory-management.mdx` | [inventory-management](https://hytalemodding.dev/en/docs/guides/plugin/inventory-management) |
| `hytale-notifications` | `guides/plugin/send-notifications.mdx`, `server/entities.mdx` | [send-notifications](https://hytalemodding.dev/en/docs/guides/plugin/send-notifications), [entities](https://hytalemodding.dev/en/docs/server/entities) |
| `hytale-npc-templates` | `official-documentation/npc/` (all 12 chapters) | [npc](https://hytalemodding.dev/en/docs/official-documentation/npc) |
| `hytale-persistent-data` | `guides/plugin/store-persistent-data.mdx`, `guides/ecs/hytale-ecs-theory.mdx`, `guides/ecs/entity-component-system.mdx`, `guides/ecs/systems.mdx` | [store-persistent-data](https://hytalemodding.dev/en/docs/guides/plugin/store-persistent-data) |
| `hytale-player-death-event` | `guides/plugin/player-death-event.mdx` | [player-death-event](https://hytalemodding.dev/en/docs/guides/plugin/player-death-event) |
| `hytale-player-stats` | `guides/plugin/player-stats.mdx` | [player-stats](https://hytalemodding.dev/en/docs/guides/plugin/player-stats) |
| `hytale-playing-sounds` | `guides/plugin/playing-sounds.mdx`, `server/sounds.mdx` | [playing-sounds](https://hytalemodding.dev/en/docs/guides/plugin/playing-sounds), [sounds](https://hytalemodding.dev/en/docs/server/sounds) |
| `hytale-spawning-entities` | `guides/plugin/spawning-entities.mdx`, `server/entities.mdx` | [spawning-entities](https://hytalemodding.dev/en/docs/guides/plugin/spawning-entities), [entities](https://hytalemodding.dev/en/docs/server/entities) |
| `hytale-spawning-npcs` | `guides/plugin/spawning-npcs.mdx` | [spawning-npcs](https://hytalemodding.dev/en/docs/guides/plugin/spawning-npcs) |
| `hytale-teleporting-players` | `guides/plugin/teleporting-players.mdx` | [teleporting-players](https://hytalemodding.dev/en/docs/guides/plugin/teleporting-players) |
| `hytale-text-holograms` | `guides/plugin/text-hologram.mdx` | [text-hologram](https://hytalemodding.dev/en/docs/guides/plugin/text-hologram) |
| `hytale-ui-modding` | `official-documentation/custom-ui/common-styling.mdx`, `official-documentation/custom-ui/layout.mdx`, `official-documentation/custom-ui/markup.mdx`, `official-documentation/custom-ui/type-documentation/`, `guides/plugin/ui.mdx` | [custom-ui](https://hytalemodding.dev/en/docs/official-documentation/custom-ui) |
| `hytale-world-gen` | `guides/plugin/world-gen.mdx`, `official-documentation/worldgen/` (all subdirs) | [world-gen](https://hytalemodding.dev/en/docs/guides/plugin/world-gen) |
| `hytale-blocks` | `guides/plugin/creating-block.mdx` | [creating-block](https://hytalemodding.dev/en/docs/guides/plugin/creating-block) |
| `hytale-prefabs` | `guides/prefabs.mdx` | [prefabs](https://hytalemodding.dev/en/docs/guides/prefabs) |
| `hytale-commands` | `guides/plugin/creating-commands.mdx` | [creating-commands](https://hytalemodding.dev/en/docs/guides/plugin/creating-commands) |
| `hytale-items` | `guides/plugin/item-interaction.mdx`, `guides/plugin/item-registry.mdx` | [item-interaction](https://hytalemodding.dev/en/docs/guides/plugin/item-interaction), [item-registry](https://hytalemodding.dev/en/docs/guides/plugin/item-registry) |

### Skills Without Upstream Doc URLs (Official Server Source Only)

These skills were built from the official server source in `lib/hytale-shared-source/HytaleServer/`. Update them by reviewing changes in the relevant module packages.

| Skill | Official Source Module | Primary Packages |
|-------|----------------------|-----------------|
| `hytale-entity-effects` | `CoreServer` | `com.hypixel.hytale.server.core.entity.effect` |
| `hytale-logging` | `Logger` | `com.hypixel.hytale.logger` |
| `hytale-permissions` | `CoreServer` | `com.hypixel.hytale.server.core.permissions` |
| `hytale-player-input` | `Protocol`, `CoreServer` | `com.hypixel.hytale.protocol.packets.*`, `com.hypixel.hytale.server.core.io` |
| `hytale-plugin-config` | `CoreServer` | `com.hypixel.hytale.server.core.plugin`, `com.hypixel.hytale.server.core.util` |
| `hytale-tag-system` | `AssetStore` | `com.hypixel.hytale.assetstore` |
| `hytale-npc-sensors` | `NPC` | `com.hypixel.hytale.server.npc.corecomponents` |
| `hytale-npc-actions` | `NPC` | `com.hypixel.hytale.server.npc.corecomponents` |
| `hytale-npc-combat` | `NPC` | `com.hypixel.hytale.server.npc.corecomponents` |
| `hytale-npc-pathfinding` | `NPC` | `com.hypixel.hytale.server.npc.navigation`, `com.hypixel.hytale.server.npc.movement` |
| `hytale-npc-components` | `NPC` | `com.hypixel.hytale.server.npc.instructions` |
| `hytale-npc-custom-components` | `NPC` | `com.hypixel.hytale.server.npc.instructions` |

---

## Update Procedure

### Step 0: Verify Official Source Against Compiled JAR (ALWAYS DO THIS FIRST)

All skill updates must start with the source, not docs.

1. Search `lib/hytale-shared-source/HytaleServer/` for the relevant classes (sources under `<Module>/src/main/java/com/hypixel/`)
2. Read the actual class files to get exact package paths, method signatures, constructor signatures
3. Compare against what the skill documents — fix any discrepancies immediately
4. The verified package table in the **CRITICAL: Official Source is Now Available** section above is authoritative

### Step 1: Check for Upstream Doc Changes

Use the GitHub API to check recent commits affecting the docs content directory. Fetch the commit history for the content path:

```
https://api.github.com/repos/HytaleModding/site/commits?path=content/docs/en&since=YYYY-MM-DDTHH:MM:SSZ
```

Or check specific files using the raw content URL pattern:

```
https://raw.githubusercontent.com/HytaleModding/site/main/content/docs/en/{path}
```

**Practical approach using fetch_webpage:**

1. Fetch the GitHub commits page for the content directory to see recent changes:
   ```
   https://github.com/HytaleModding/site/commits/main/content/docs/en
   ```

2. For each skill in the mapping table, fetch the raw MDX source for its mapped files:
   ```
   https://raw.githubusercontent.com/HytaleModding/site/main/content/docs/en/guides/plugin/{filename}.mdx
   ```

3. Compare the fetched content against the current skill's SKILL.md to identify:
   - New API methods or classes documented
   - Changed method signatures or parameters
   - New code examples or updated examples
   - Deprecated or removed functionality
   - New sections or reorganized content

> **Important:** When community docs conflict with official source, the official source wins. Update skills to match the source, not the community docs.

### Step 2: Discover New Documentation Pages & Skills

This is the most important step for keeping skills comprehensive. The HytaleModding site is community-driven and frequently adds new guides and documentation.

#### 2a. Scan All Content Directories

Fetch the directory listings for every content folder on GitHub to build a complete inventory of `.mdx` files:

**Directories to scan:**
- `https://github.com/HytaleModding/site/tree/main/content/docs/en/guides/plugin`
- `https://github.com/HytaleModding/site/tree/main/content/docs/en/guides/ecs`
- `https://github.com/HytaleModding/site/tree/main/content/docs/en/guides` (top-level guides like prefabs.mdx)
- `https://github.com/HytaleModding/site/tree/main/content/docs/en/official-documentation`
- `https://github.com/HytaleModding/site/tree/main/content/docs/en/official-documentation/custom-ui`
- `https://github.com/HytaleModding/site/tree/main/content/docs/en/official-documentation/npc`
- `https://github.com/HytaleModding/site/tree/main/content/docs/en/official-documentation/worldgen`
- `https://github.com/HytaleModding/site/tree/main/content/docs/en/server`

Also scan for **new subdirectories** that may have been added since the last check — these often indicate major new documentation categories.

#### 2b. Classify Each File

For every `.mdx` file found, classify it into one of these categories:

| Category | Action | Examples |
|----------|--------|----------|
| **Already mapped** | Check for updates (Step 3) | Files in the Skill-to-Source Mapping table |
| **Meta/setup guide** | Skip — not skill material | `setting-up-env.mdx`, `build-and-test.mdx`, `browsing-serverjar.mdx` |
| **Extends existing skill** | Merge content into the existing skill | A new `item-registry.mdx` extending `hytale-items` |
| **New standalone topic** | **Create a new skill** (Step 2c) | A brand new guide on a topic with no skill |
| **New official docs section** | **Create a new skill** (Step 2c) | A new folder under `official-documentation/` |

**How to decide "extends existing" vs "new standalone":**
- If the doc covers a sub-feature of something an existing skill already handles → extend existing
- If the doc introduces a fundamentally new system, API, or workflow → create new standalone skill
- When in doubt, check the existing skills' `description` field — if the new doc's trigger keywords overlap heavily, it probably extends an existing skill

#### 2c. New Skill Discovery Procedure

When you find an `.mdx` file (or new subdirectory) that doesn't map to any existing skill:

**1. Fetch the raw MDX content:**
```
https://raw.githubusercontent.com/HytaleModding/site/main/content/docs/en/{path-to-file}.mdx
```

**2. Analyze the content for skill viability:**

A doc page is a good skill candidate if it has **at least 2** of these:
- Java code examples with specific API classes/methods
- JSON configuration examples
- A distinct system/feature not covered by existing skills
- Step-by-step procedures developers would need to reference
- API classes or methods that a developer would search for by name

A doc page is NOT a good skill candidate if:
- It's purely conceptual with no actionable code/config
- It's a meta-guide about tooling setup (IDE, build, etc.)
- It duplicates content already in an existing skill
- It's a changelog or release notes page

**3. Extract the skill scaffold from the MDX:**

From the fetched MDX content, extract:
- **Title and topic** → becomes the skill `name` (prefixed with `hytale-`)
- **Key API classes and methods** → becomes `description` trigger keywords
- **Code examples** → becomes the skill's code reference sections
- **JSON examples** → becomes data-driven configuration sections
- **Concepts and terminology** → becomes Quick Reference table entries
- **Related upstream URLs** → becomes source references in the skill

**4. Cross-reference with decompiled server source:**

Search `lib/hytale-shared-source/HytaleServer/` for the classes mentioned in the doc (sources are under `<Module>/src/main/java/com/hypixel/`, e.g. `NPC/`, `CoreServer/`, `Common/`). This often reveals:
- Additional methods not documented yet
- Constructor signatures for proper usage
- Related classes the doc doesn't mention
- Package paths needed for import statements

**5. Create the new skill following the template below (Step 5).**

#### 2d. Skills Currently Identified as Without Matching Docs (Known Gaps)

**Doc pages without skills (potential new skills):**

| Doc File | Status | Recommendation |
|----------|--------|----------------|
| `guides/plugin/creating-block.mdx` | **DONE** — `hytale-blocks` | Block creation, asset packs, block JSON, textures, materials |
| `guides/prefabs.mdx` | **DONE** — `hytale-prefabs` | Prefab system, commands, reusable structures |
| `guides/plugin/item-interaction.mdx` | **DONE** — merged into `hytale-items` | Interaction content in `hytale-items` skill |
| `guides/plugin/item-registry.mdx` | **DONE** — merged into `hytale-items` | Registry content in `hytale-items` skill |
| `guides/plugin/client-inputs-reference.mdx` | Extends `hytale-player-input` | Merge reference content into `hytale-player-input` skill |
| `guides/plugin/listening-to-packets.mdx` | Extends `hytale-player-input` | Merge packet content into `hytale-player-input` skill |
| `guides/plugin/browsing-serverjar.mdx` | Meta-guide | Skip — not skill material |
| `guides/plugin/build-and-test.mdx` | Meta-guide | Skip — not skill material |
| `guides/plugin/setting-up-env.mdx` | Meta-guide | Skip — not skill material |

**Update this table** each time you run the discovery process. Remove entries that have been addressed and add new ones found.

### Step 3: Update Existing Skills

For each skill with detected changes:

1. **Fetch the raw MDX source** for all mapped files using:
   ```
   https://raw.githubusercontent.com/HytaleModding/site/main/content/docs/en/{path}
   ```

2. **Read the current SKILL.md** for the skill being updated.

3. **Identify deltas** between the upstream content and the skill:
   - New API methods, classes, or components → Add to skill
   - Updated code examples → Replace stale examples
   - Changed method signatures → Update references
   - New sections or concepts → Incorporate into skill
   - Removed/deprecated content → Remove or mark as deprecated

4. **Cross-reference with official server source** in `lib/hytale-shared-source/HytaleServer/`:
   - Verify that code examples in the updated docs are correct against the actual server JAR
   - Check for API changes that the docs may not yet reflect
   - Look for new classes or methods that supplement the doc content

5. **Update the SKILL.md** following these rules:
   - Preserve the skill's existing structure and organization style
   - Keep the frontmatter `description` and triggers updated with any new keywords
   - Ensure code examples compile against the current server version
   - Update Quick Reference tables with new methods/approaches
   - Maintain cross-references to related skills (`> **Related skills:** ...`)
   - Do NOT remove project-specific guidance that was added beyond the upstream docs

### Step 4: Update Server-Source-Only Skills

For skills without upstream doc URLs:

1. **Check `lib/` for changes** in the relevant packages (see mapping table above).

2. **Look for new classes, methods, or changed signatures** in the decompiled source.

3. **Update the skill** with:
   - New API methods or classes
   - Changed constructor or method parameters
   - New component types or system types
   - Updated code patterns

### Step 5: Create New Skills

When a new doc page is detected that warrants its own skill (see Step 2c), follow this complete procedure.

#### 5a. Gather All Source Material

1. **Fetch the raw MDX content** for the new page(s):
   ```
   https://raw.githubusercontent.com/HytaleModding/site/main/content/docs/en/{path}
   ```

2. **Search the decompiled server source** for key classes mentioned in the doc:
   ```
   lib/hytale-shared-source/HytaleServer/
   ```
   Use grep/search to find:
   - The main API classes referenced in the doc
   - Related classes in the same package
   - Method signatures, constructors, and public fields
   - Enum values or constants relevant to the topic

3. **Check `lib/Server/` JSON files** for any data-driven definitions related to the topic:
   - Entity definitions, block definitions, item definitions, etc.
   - Configuration schemas and default values

4. **Review existing skills** that are related to identify:
   - Content boundaries (what this skill covers vs what existing skills cover)
   - Cross-reference opportunities
   - Shared patterns to maintain consistency

#### 5b. Choose a Name

Follow the naming convention: `hytale-{topic}` where `{topic}` is:
- Lowercase, hyphen-separated
- Describes the primary system or feature
- Matches what a developer would search for

Examples: `hytale-blocks`, `hytale-prefabs`, `hytale-crafting`, `hytale-particles`

#### 5c. Create the Skill Using the Template

Create the directory and `SKILL.md`:
```
.github/skills/hytale-{topic}/
└── SKILL.md
```

**Use this template for the SKILL.md content:**

`````markdown
---
name: hytale-{topic}
description: {One sentence describing what this skill covers}. Use when {common use cases}. Triggers - {keyword1}, {keyword2}, {ClassName1}, {ClassName2}, {method1}, {concept1}.
---

# Hytale {Topic Title}

{1-2 sentence summary of when and why to use this skill.}

> **Source:** <{upstream doc URL}>
> **Related skills:** For {related concept}, see `{related-skill-name}`.

---

## Quick Reference

| Task | Approach |
|------|----------|
| {Common task 1} | `{method or approach}` |
| {Common task 2} | `{method or approach}` |
| {Common task 3} | `{method or approach}` |

---

## Key Concepts

### {Concept 1}

{Explanation of the concept and how it fits into the system.}

### {Concept 2}

{Explanation with relevant details.}

---

## Required Imports

```java
import com.hypixel.{...};
```

---

## Code Examples

### {Example 1 Title}

```java
// Full working example with imports context
```

### {Example 2 Title}

```java
// Full working example
```

---

## JSON Configuration (if applicable)

```json
{
  "example": "configuration"
}
```

---

## Edge Cases & Gotchas

- {Important caveat 1}
- {Important caveat 2}
- {Thread safety note if applicable — use `world.execute()` pattern}
```
`````

#### 5d. Write the Description (Critical)

The `description` field in the frontmatter is what triggers skill selection. It must contain:

1. **What it does** — First sentence summarizes the skill's purpose
2. **When to use it** — "Use when..." clause with common scenarios  
3. **Trigger keywords** — "Triggers - " followed by ALL relevant keywords, including:
   - Plain English terms developers would search for (e.g., "block", "create block")
   - Java class names (e.g., `BlockComponent`, `ChunkStore`)
   - Method names (e.g., `registerBlock`, `setBlock`)
   - JSON-related terms if applicable (e.g., "block JSON", "block definition")

**Good example:**
```
description: Documents how to create custom blocks in Hytale plugins using BlockComponent and ChunkStore. Use when creating blocks, defining block properties, registering block components, or working with block JSON definitions. Triggers - block, create block, custom block, BlockComponent, ChunkStore, block JSON, block definition, registerBlock, block properties, block tick, setTicking.
```

**Bad example (too vague, missing triggers):**
```
description: Information about blocks in Hytale.
```

#### 5e. Fill in Content from Source Material

Working through the template sections:

1. **Quick Reference** — Extract the most common tasks from the doc and provide one-liner solutions
2. **Key Concepts** — Summarize the core ideas, focusing on what's unique to this system
3. **Required Imports** — List ALL imports needed for the code examples (full package paths from decompiled source)
4. **Code Examples** — Adapt examples from the MDX, verify against decompiled source, add context comments
5. **JSON Configuration** — Include any JSON definitions from the doc or from `lib/Server/` examples
6. **Edge Cases** — Extract warnings, caveats, and thread-safety notes from the doc and your server source review

#### 5f. Register the Skill

After creating the SKILL.md:

1. **Add to `.github/copilot-instructions.md`** in copilot-instructions:
   - Add a `<skill>` entry in the `<skills>` section following the existing pattern
   - Include the `name`, `description` (matching frontmatter), and `file` path

2. **Update this skill's mapping table** — Add the new skill to the "Skills with Upstream Doc URLs" table in this file (update-hytale-skills SKILL.md)

3. **Update the known gaps table** (Step 2d) — Move the entry from "NEW SKILL CANDIDATE" to the mapping table

#### 5g. Validation

After creating and registering:

- [ ] Skill directory name matches the `name` field in frontmatter
- [ ] Description contains meaningful trigger keywords (at least 8-10 keywords)
- [ ] Code examples use correct import paths verified against `lib/`
- [ ] Quick Reference table has at least 3 entries
- [ ] At least one complete, working code example
- [ ] Related skills cross-references are bidirectional (update the related skills too)
- [ ] Skill registered in `.github/copilot-instructions.md`
- [ ] Mapping table in this skill updated

---

## Post-Update Checklist

After updating any skills, verify:

- [ ] **Description updated** — New trigger keywords added if the upstream added new concepts
- [ ] **Code examples compile** — Run the build task to verify no compilation errors from example code
- [ ] **Cross-references valid** — Related skill references still point to existing skills
- [ ] **URLs updated** — Any referenced doc URLs still resolve correctly
- [ ] **No duplicate content** — Changes didn't introduce overlap with other skills
- [ ] **Server source alignment** — Code examples match the decompiled server API in `lib/`
- [ ] **copilot-instructions.md updated** — New skills added to the skill list with correct description and trigger info

---

## Automation Tips

### Batch Checking All Skills

To check all skills at once, iterate through the mapping table and fetch each source file. Compare modification dates or content hashes to quickly identify which skills need attention.

### Prioritizing Updates

1. **High priority:** Skills where the upstream API or method signatures changed (breaking changes)
2. **Medium priority:** Skills where new features or methods were added
3. **Low priority:** Skills where only prose, typos, or formatting changed in the docs

### Change Detection Heuristic

When comparing upstream MDX to the current SKILL.md, focus on:
- Java code blocks (` ```java ... ``` `) — These contain API examples most likely to change
- Class and method names mentioned in prose
- JSON configuration examples
- New headings or sections that indicate new features

---

## Full Discovery & Update Workflow (Quick-Start)

Use this as a single checklist when running the full update cycle:

### Phase 1: Scan
- [ ] Fetch `https://github.com/HytaleModding/site/commits/main/content/docs/en` — note which files changed recently
- [ ] Fetch directory listings for all content directories (Step 2a)
- [ ] List all `.mdx` files found across all directories
- [ ] Compare against the Skill-to-Source Mapping table
- [ ] Classify each new/unmapped file (Step 2b)

### Phase 2: Discover New Skills
- [ ] For each "NEW SKILL CANDIDATE" file, fetch raw MDX content
- [ ] Evaluate viability using the criteria in Step 2c
- [ ] For viable candidates, search decompiled source for related classes
- [ ] Create new skills using the template (Step 5c)
- [ ] Write descriptions with proper trigger keywords (Step 5d)
- [ ] Register new skills and update mapping tables (Step 5f)

### Phase 3: Update Existing Skills
- [ ] For each skill with upstream changes, fetch the raw MDX source
- [ ] Compare against current SKILL.md content section by section
- [ ] Update code examples, method signatures, and Quick Reference tables
- [ ] Cross-reference against decompiled server source in `lib/`
- [ ] Update description trigger keywords if new concepts were added

### Phase 4: Server-Source-Only Skills
- [ ] Check `lib/hytale-shared-source/HytaleServer/` for changes in relevant packages
- [ ] Update skills with new/changed APIs from decompiled source

### Phase 5: Validate
- [ ] Run the build task to ensure no compilation errors
- [ ] Verify all cross-references between skills are bidirectional
- [ ] Confirm all doc URLs still resolve
- [ ] Ensure `copilot-instructions.md` reflects all current skills
