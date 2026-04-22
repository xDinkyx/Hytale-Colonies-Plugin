---
name: hytale-npc-custom-components
version: 2
tags: [hytale, npc, custom, sensor, action, extension, registerCoreComponentType, BuilderActionBase, BuilderSensorBase, holder, data-driven]
---

# Hytale NPC Custom Components

Documents the Hytale NPC extensibility API for creating custom sensors and actions that register as first-class `"Type"` values in NPC role JSON templates. Covers the Builder+Runtime pair pattern, Holder types, registration, and the key gotchas discovered through implementation.

Use when writing a custom NPC sensor, custom NPC action, extending NPC AI beyond built-in types, or wiring plugin logic into the NPC JSON system.

## Triggers

- custom NPC sensor
- custom NPC action
- registerCoreComponentType
- BuilderActionBase
- BuilderSensorBase
- SensorBase
- ActionBase
- NPC extension
- NPC plugin component
- Holder types
- NPCPlugin.get()
- ExecutionContext
- IntHolder
- StringHolder
- DoubleHolder
- BooleanHolder
- AssetHolder
- BuilderSupport
- custom action builder
- custom sensor builder
- NPC extensibility

---

## Core Concept

Every built-in NPC instruction type (`"Block"`, `"Seek"`, `"PlayAnimation"`, etc.) is registered the same way as the extension point. Plugins access the same public API:

```java
NPCPlugin.get().registerCoreComponentType("MyType", BuilderMyType::new);
```

Once registered, `"Type": "MyType"` is valid anywhere in an NPC role JSON template — in `Instructions`, `Sensor`, `Action`, etc. The type category (sensor vs action vs body motion) is determined by which base class the builder extends.

---

## Pattern: Builder + Runtime Pair

Each custom component consists of two classes:

| Class | Purpose |
|---|---|
| `BuilderXxx extends BuilderActionBase` (or `BuilderSensorBase`) | Reads JSON config once at load time; constructs the runtime instance |
| `ActionXxx extends ActionBase` (or `SensorXxx extends SensorBase`) | Called per-NPC tick; contains the actual logic |

This mirrors exactly how the engine's built-in types are structured.

---

## Custom Action — Full Example (`EquipBestTool`)

### Builder

```java
package com.hytalecolonies.npc.actions;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.IntHolder;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringHolder;
import com.hypixel.hytale.server.npc.asset.builder.validators.IntSingleValidator;
import com.hypixel.hytale.server.npc.util.expression.ExecutionContext;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;

public class BuilderActionEquipBestTool extends BuilderActionBase {

    private final StringHolder gatherType = new StringHolder();
    private final IntHolder minQuality    = new IntHolder();

    @Nonnull @Override public String getShortDescription() {
        return "Equip the best tool for a gather type from the colonist's inventory.";
    }

    @Nonnull @Override public String getLongDescription() {
        return "Searches inventory (hotbar first) for the best tool matching GatherType at MinQuality.";
    }

    @Nonnull @Override public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        // Required string field
        this.requireString(data, "GatherType", this.gatherType, null,
            BuilderDescriptorState.Experimental,
            "The gather type to find a tool for (e.g. Woodcutting, Mining)", null);

        // Optional int field with a default and validator
        this.getInt(data, "MinQuality", this.minQuality, 0,
            IntSingleValidator.greaterEqual0(),
            BuilderDescriptorState.Experimental,
            "Minimum tool quality tier required (0 = any)", null);

        return this;
    }

    @Nonnull @Override
    public Action build(@Nonnull BuilderSupport support) {
        return new ActionEquipBestTool(this, support);
    }

    // Accessor methods — always pass support.getExecutionContext() to the holder
    @Nonnull public String getGatherType(@Nonnull BuilderSupport support) {
        ExecutionContext ctx = support.getExecutionContext();
        return this.gatherType.get(ctx);
    }

    public int getMinQuality(@Nonnull BuilderSupport support) {
        ExecutionContext ctx = support.getExecutionContext();
        return this.minQuality.get(ctx);
    }
}
```

### Runtime

```java
package com.hytalecolonies.npc.actions;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ActionEquipBestTool extends ActionBase {

    private final String gatherType;
    private final int minQuality;

    public ActionEquipBestTool(@Nonnull BuilderActionEquipBestTool builder,
                               @Nonnull BuilderSupport support) {
        super(builder);
        // Resolve holder values once in the constructor; store as plain fields
        this.gatherType = builder.getGatherType(support);
        this.minQuality = builder.getMinQuality(support);
    }

    @Override
    public boolean execute(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Role role,
            @Nullable InfoProvider sensorInfo,
            double dt,
            @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store); // handles "once" logic

        LivingEntity entity = (LivingEntity) EntityUtils.getEntity(ref, store);
        if (entity == null) return false;

        Inventory inventory = entity.getInventory();
        if (inventory == null) return false;

        boolean equipped = ColonistToolUtil.equipBestToolForGatherType(inventory, gatherType, minQuality, ref, store);
        // Always return true: the action is done regardless of whether a tool was found.
        // Callers that need a tool present should check via a sensor, not the action return value.
        return true;
    }
}
```

### JSON usage

```json
{
  "Type": "EquipBestTool",
  "GatherType": "Woodcutting",
  "MinQuality": 0
}
```

---

## Custom Sensor — Template

```java
package com.hytalecolonies.npc.sensors;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import javax.annotation.Nonnull;

public class BuilderSensorMyCondition extends BuilderSensorBase {

    // Declare holders for each JSON field
    // private final StringHolder someParam = new StringHolder();

    @Nonnull @Override public String getShortDescription() { return "..."; }
    @Nonnull @Override public String getLongDescription()  { return "..."; }
    @Nonnull @Override public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) {
        // this.requireString(data, "Param", this.someParam, null, ...);
        return this;
    }

    @Nonnull @Override
    public Sensor build(@Nonnull BuilderSupport support) {
        return new SensorMyCondition(this, support);
    }
}
```

```java
package com.hytalecolonies.npc.sensors;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import javax.annotation.Nonnull;

public class SensorMyCondition extends SensorBase {

    public SensorMyCondition(@Nonnull BuilderSensorMyCondition builder,
                             @Nonnull BuilderSupport support) {
        super(builder);
        // resolve holder values from support.getExecutionContext()
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref,
                           @Nonnull Role role,
                           double dt,
                           @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, role, dt, store)) return false; // handles "once" logic
        // --- your condition logic ---
        return true;
    }
}
```

---

## Registration

Call registration from `setup()` — **before** NPC roles are parsed. All types must be registered at this point.

```java
// HytaleColoniesPlugin.java
import com.hypixel.hytale.server.npc.NPCPlugin;

@Override
public void setup() {
    // ... other registrations ...
    registerNpcComponentTypes();
}

private void registerNpcComponentTypes() {
    NPCPlugin.get()
        .registerCoreComponentType("EquipBestTool", BuilderActionEquipBestTool::new);
    // Add more types here — chaining is supported:
    // NPCPlugin.get()
    //     .registerCoreComponentType("TypeA", BuilderA::new)
    //     .registerCoreComponentType("TypeB", BuilderB::new);
}
```

---

## Holder Types & Builder Methods

Holders are how builder fields map from JSON to Java. **Always resolve with `holder.get(support.getExecutionContext())`**, never `holder.get(support)`.

| Holder | JSON value | Builder method (require = throw if missing, get = use default) |
|---|---|---|
| `StringHolder` | `"text"` | `requireString(...)` / `getString(...)` |
| `IntHolder` | `42` | `requireInt(...)` / `getInt(...)` |
| `DoubleHolder` | `3.14` | `requireDouble(...)` / `getDouble(...)` |
| `BooleanHolder` | `true` | `getBoolean(...)` (always optional, always has default) |
| `AssetHolder` | `"Namespace:AssetName"` | `requireAsset(...)` / `getAsset(...)` |
| `EnumHolder<E>` | `"EnumValue"` | `getEnum(...)` |
| `FloatHolder` | `1.5` | `requireDouble(...)` / `getDouble(...)` (cast to float) |

### Builder method signatures (shared across all holder types)

```java
// Required — throws load error if key missing
this.requireString(JsonElement data, String key, StringHolder out,
    @Nullable StringValidator validator,
    BuilderDescriptorState state, String description, @Nullable String since);

// Optional — uses defaultValue if key absent
this.getString(JsonElement data, String key, StringHolder out,
    @Nullable String defaultValue,
    @Nullable StringValidator validator,
    BuilderDescriptorState state, String description, @Nullable String since);

// Int example
this.getInt(data, "MinQuality", this.minQuality, 0,
    IntSingleValidator.greaterEqual0(),
    BuilderDescriptorState.Experimental, "description", null);
```

### Available validators

```java
// Int validators (com.hypixel.hytale.server.npc.asset.builder.validators)
IntSingleValidator.greaterEqual0()  // value >= 0
IntSingleValidator.greater0()       // value > 0
// No .range() method exists — do not use it
```

---

## Package Conventions

| Contents | Package |
|---|---|
| Custom action builders | `com.hytalecolonies.npc.actions` |
| Custom action runtimes | `com.hytalecolonies.npc.actions` |
| Custom sensor builders | `com.hytalecolonies.npc.sensors` |
| Custom sensor runtimes | `com.hytalecolonies.npc.sensors` |

---

## Common Gotchas

| Mistake | Fix |
|---|---|
| `holder.get(support)` | Use `holder.get(support.getExecutionContext())` |
| `IntSingleValidator.range(0, 100)` | Method does not exist — use `greaterEqual0()` or `greater0()` |
| Registering in `start()` | Must register in `setup()` — NPC roles are parsed before `start()` |
| Calling `super.execute()` and ignoring return value | Call `super.execute(...)` for once-logic bookkeeping, then proceed with custom logic regardless of its return value |
| Custom action in `StateTransitions` returning `false` | Transition never completes; the NPC instruction body is skipped every tick until the transition finishes. | Actions in `StateTransitions` must always return `true`. |
| Mutating entity state directly inside `execute()` | Prefer `CommandBuffer` for ECS component changes; direct `Inventory` mutations are safe |

---

## `execute()` return value contract

The `boolean` return value of `Action.execute()` only matters in **`ActionsBlocking: true`** sequences (`ActionList` with `blocking=true`):

| Return | Meaning in blocking mode |
|---|---|
| `true` | Action is **done** — advance to the next action in the sequence |
| `false` | Action is **still in progress** — retry this same action next tick |

In **non-blocking** mode (the default when `ActionsBlocking` is absent), the return value is **completely ignored** — all eligible actions run every tick.

**Design rule:** if an action's logical success is uncertain (e.g. equipping a tool when none may exist), return `true` unconditionally. Success checks belong in sensors, not action return values. Returning `false` in a blocking context means "retry me" — only do that for genuinely in-progress work (e.g. pathfinding, timed waits).

---

## Integration with Built-in Block Sensor

Custom sensors can layer on top of the engine's `BlockTypeView` blackboard for block detection without re-scanning chunks. Declare interest in the constructor:

```java
// In custom sensor builder constructor — tells engine to maintain block position index
public BuilderSensorHarvestableTree() {
    super();
    // support.requireBlockTypeBlackboard("Hyforged:TreeWood"); // call in readConfig or build
}
```

Then in the runtime `matches()`:

```java
// Query the index for a nearby block
BlockTypeBlackboardView view = npc.getBlockTypeBlackboardView(ref, store); // hypothetical — verify exact API
Optional<Vector3i> pos = view.findBlock(blockSetId, range, yRange, random, ref, store);
```

> **Verify the exact API** in `lib/hytale-server` source before implementing — method names may differ. The pattern is documented here as a guide.

---

## Relationship to `hytale-npc-templates`

The `hytale-npc-templates` skill covers what goes in NPC role JSON. This skill covers how to add *new JSON types* for that JSON. The two skills are complementary — use both when building a full custom NPC behavior.
