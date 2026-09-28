---
name: hytale-npc-custom-components
version: 4
tags: [hytale, npc, custom, sensor, action, extension, registerCoreComponentType, BuilderActionBase, BuilderSensorBase, ActionBase, SensorBase, ExecutionSupport, Role, data-driven]
---

# Hytale NPC Custom Components

Documents the Hytale NPC extensibility API for creating custom sensors and actions that register as first-class `"Type"` values in NPC role JSON templates. Covers both the legacy Role-based API (what the plugin currently compiles against) and the modern ExecutionSupport-based interfaces, the complete ExecutionSupport reference, the Builder+Runtime pair pattern, Holder types, registration, and runtime patterns for ECS component access.

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
- ExecutionSupport
- ExecutionContext
- Role
- StateSupport
- MarkedEntitySupport
- FlagsComponent
- NavTarget
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

Every built-in NPC instruction type (`"Block"`, `"Seek"`, `"PlayAnimation"`, etc.) is registered exactly like the extension point. Plugins use the same public API:

```java
NPCPlugin.get().registerCoreComponentType("MyType", BuilderMyType::new);
```

Once registered, `"Type": "MyType"` is valid anywhere in an NPC role JSON template -- in `Instructions`, `Sensor`, `Action`, etc. The category (sensor vs action) is determined by which base class the builder extends.

---

## API Tiers

Two API tiers exist. **The plugin currently compiles against the legacy tier** (the compiled JAR exposes `Role`-based method signatures). The modern tier is the canonical interface from the official shared source and will be what new JAR versions expose.

| Tier | Runtime 2nd parameter | When to use |
|---|---|---|
| Legacy | `Role role` | Current plugin code; what the JAR supports now |
| Modern | `ExecutionSupport executionSupport` | Official interface; use when JAR is updated |

The `ExecutionSupport` sub-supports (`getStateSupport()`, `getMarkedEntitySupport()`, etc.) mirror the equivalent methods on `Role`, so migration will be mechanical.

---

## Pattern: Builder + Runtime Pair

Each custom component is two classes:

| Class | Purpose |
|---|---|
| `BuilderXxx extends BuilderActionBase` or `BuilderSensorBase` | Reads JSON config once at load time; constructs the runtime instance |
| `ActionXxx extends ActionBase` or `SensorXxx extends SensorBase` | Called per-NPC tick; contains actual logic |

---

## Registration

Call registration from `setup()` -- **before** NPC roles are parsed. All types must be registered at this point.

```java
// HytaleColoniesPlugin.java
import com.hypixel.hytale.server.npc.NPCPlugin;

@Override
public void setup() {
    registerNpcComponentTypes();
}

private void registerNpcComponentTypes() {
    NPCPlugin.get()
        .registerCoreComponentType("SeekNearestTree", BuilderActionSeekNearestTree::new)
        .registerCoreComponentType("HarvestableTree", BuilderSensorHarvestableTree::new);
    // chain as many as needed
}
```

---

## Custom Action -- Legacy API (Current Plugin Pattern)

Use this pattern for all new actions until the server JAR exposes ExecutionSupport signatures.

### Builder

```java
package com.hytalecolonies.npc.actions.common;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.IntHolder;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringHolder;
import com.hypixel.hytale.server.npc.asset.builder.validators.IntSingleValidator;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import com.hypixel.hytale.server.npc.util.expression.ExecutionContext;
import javax.annotation.Nonnull;

public class BuilderActionMyAction extends BuilderActionBase {

    private final StringHolder targetSlot = new StringHolder();
    private final IntHolder    maxItems   = new IntHolder();

    @Nonnull @Override public String getShortDescription() { return "One-line summary."; }
    @Nonnull @Override public String getLongDescription()  { return "Full description."; }
    @Nonnull @Override public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        // Required field -- throws a load error if the key is absent
        this.requireString(data, "TargetSlot", this.targetSlot, null,
            BuilderDescriptorState.Experimental, "Inventory slot category", null);
        // Optional field with default value
        this.getInt(data, "MaxItems", this.maxItems, 64,
            IntSingleValidator.greater0(),
            BuilderDescriptorState.Experimental, "Max items to transfer", null);
        return this;
    }

    @Nonnull @Override
    public Action build(@Nonnull BuilderSupport support) {
        return new ActionMyAction(this, support);
    }

    // Accessor for runtime: always pass support.getExecutionContext() to holder.get()
    @Nonnull public String getTargetSlot(@Nonnull BuilderSupport support) {
        ExecutionContext ctx = support.getExecutionContext();
        return this.targetSlot.get(ctx);
    }

    public int getMaxItems(@Nonnull BuilderSupport support) {
        return this.maxItems.get(support.getExecutionContext());
    }
}
```

### Runtime

```java
package com.hytalecolonies.npc.actions.common;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.components.jobs.JobTargetComponent;

public class ActionMyAction extends ActionBase {

    private final String targetSlot;
    private final int    maxItems;

    public ActionMyAction(@Nonnull BuilderActionMyAction builder, @Nonnull BuilderSupport support) {
        super(builder);
        // Resolve all holder values once here; store as plain fields.
        this.targetSlot = builder.getTargetSlot(support);
        this.maxItems   = builder.getMaxItems(support);
    }

    @Override
    public boolean execute(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Role role,
            @Nullable InfoProvider sensorInfo,
            double dt,
            @Nonnull Store<EntityStore> store) {

        super.execute(ref, role, sensorInfo, dt, store); // must call -- handles Once bookkeeping

        // Read an ECS component from the store
        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jobTarget == null) return true;

        // Do work using targetSlot, maxItems, etc.

        return true; // true = done; false = still in progress (blocking mode only)
    }
}
```

### JSON usage

```json
{
  "Type": "MyAction",
  "TargetSlot": "Hotbar",
  "MaxItems": 16
}
```

---

## Custom Sensor -- Legacy API (Current Plugin Pattern)

### Builder

```java
package com.hytalecolonies.npc.sensors.common;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.DoubleHolder;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import com.hypixel.hytale.server.npc.util.expression.ExecutionContext;
import javax.annotation.Nonnull;

public class BuilderSensorMyCondition extends BuilderSensorBase {

    private final DoubleHolder range = new DoubleHolder();

    @Nonnull @Override public String getShortDescription() { return "One-line summary."; }
    @Nonnull @Override public String getLongDescription()  { return "Full description."; }
    @Nonnull @Override public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) {
        this.requireDouble(data, "Range", this.range, null,
            BuilderDescriptorState.Experimental, "Detection radius in blocks", null);
        return this;
    }

    @Nonnull @Override
    public Sensor build(@Nonnull BuilderSupport support) {
        return new SensorMyCondition(this, support);
    }

    public double getRange(@Nonnull BuilderSupport support) {
        return this.range.get(support.getExecutionContext());
    }
}
```

### Runtime

```java
package com.hytalecolonies.npc.sensors.common;

import javax.annotation.Nonnull;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hypixel.hytale.server.npc.sensorinfo.PositionProvider;
import com.hytalecolonies.components.jobs.JobTargetComponent;

public class SensorMyCondition extends SensorBase {

    private final double           range;
    private final PositionProvider positionProvider = new PositionProvider();

    public SensorMyCondition(@Nonnull BuilderSensorMyCondition builder, @Nonnull BuilderSupport support) {
        super(builder);
        this.range = builder.getRange(support);
    }

    @Override
    public boolean matches(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Role role,
            double dt,
            @Nonnull Store<EntityStore> store) {

        if (!super.matches(ref, role, dt, store)) { // must call -- handles Once logic
            positionProvider.clear();
            return false;
        }

        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jobTarget == null || jobTarget.targetPosition == null) {
            positionProvider.clear();
            return false;
        }

        // Expose position to paired actions via getSensorInfo()
        positionProvider.setTarget(
            jobTarget.targetPosition.x + 0.5,
            jobTarget.targetPosition.y,
            jobTarget.targetPosition.z + 0.5);
        return true;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return positionProvider; // return null for sensors that don't provide position
    }
}
```

### JSON usage

```json
{
  "Type": "MyCondition",
  "Range": 16.0
}
```

---

## Modern API Interfaces (for future migration)

When the server JAR is updated to expose ExecutionSupport, replace `Role role` with `ExecutionSupport executionSupport` and call the same sub-supports via the ExecutionSupport getters.

### Action interface (canonical, `com.hypixel.hytale.server.npc.instructions.Action`)

```java
boolean canExecute(
    @Nonnull Ref<EntityStore> ref,
    @Nonnull ExecutionSupport executionSupport,
    @Nullable InfoProvider sensorInfo,
    double dt,
    @Nonnull Store<EntityStore> store);

boolean execute(
    @Nonnull Ref<EntityStore> ref,
    @Nonnull ExecutionSupport executionSupport,
    @Nullable InfoProvider sensorInfo,
    double dt,
    @Nonnull Store<EntityStore> store);

void activate(ExecutionSupport executionSupport, InfoProvider infoProvider);
void deactivate(ExecutionSupport executionSupport, InfoProvider infoProvider);
boolean isActivated();
```

### Sensor interface (canonical, `com.hypixel.hytale.server.npc.instructions.Sensor`)

```java
boolean matches(
    @Nonnull Ref<EntityStore> ref,
    @Nonnull ExecutionSupport executionSupport,
    double dt,
    @Nonnull Store<EntityStore> store);

@Nullable InfoProvider getSensorInfo();
void done();
```

---

## ExecutionSupport Reference

`ExecutionSupport` (`com.hypixel.hytale.server.npc.instructions.ExecutionSupport`) is a per-tick scratch context that bundles all NPC ECS sub-support references. It mirrors what `Role` provides in the legacy API. Instances are pooled per thread.

### Lifecycle

```java
ExecutionSupport support = ExecutionSupport.acquire(); // from thread-local pool
try {
    // Choose one populate method:
    support.populateFromEntity(ref, accessor);  // lazy -- fetches on first getter call
    // or:
    support.populateFromHolder(holder);          // eager -- fetches all at once, asserts non-null
    
    // use support...
} finally {
    support.clearForReuse(); // always in finally; returns instance to pool
}
```

### Sub-supports (all lazy via getter; all assert non-null)

| Getter | Type | Use for |
|---|---|---|
| `getStateSupport()` | `StateSupport` | Read/write NPC main-state and sub-state |
| `getMarkedEntitySupport()` | `MarkedEntitySupport` | Position slots; NavTarget (slot 0) |
| `getWorldSupport()` | `WorldSupport` | World position and slot management |
| `getEntitySupport()` | `EntitySupport` | Deferred entity actions |
| `getPositionCache()` | `PositionCache` | Position caching utilities |
| `getDebugSupport()` | `DebugSupport` | Debug rendering/visualization |
| `getFlagsComponent()` | `FlagsComponent` | Named boolean flags read/write |
| `getCombatSupport()` | `CombatSupport` | Combat state (NPC-only) |
| `getMotionContextSupport()` | `MotionContextSupport` | Active/next motion context |
| `getDisplayNameSupport()` | `DisplayNameSupport` | NPC display name |
| `getPlayerTaskSupport()` | `PlayerTaskSupport` | Player task state |

### Other methods

```java
int    getRoleIndex()                          // role slot index (-1 if unset)
String getName()                               // role name (nullable)
void   setRoleIndex(int roleIndex)
void   setName(String name)

// Tree-mode utilities (used internally by instruction tree; rarely needed in custom code)
Instruction swapTreeModeSteps(Instruction newStep)  // swap current tree step; returns old
void        notifySensorMatch()                      // notify parent of child sensor match

// Slot-indexed instruction map
IndexedInstructions getIndexedInstructions()
void setIndexedInstructions(IndexedInstructions ii)

// @Deprecated bridges -- use sub-supports instead
Role      getRole()       // @Deprecated
NPCEntity getNpcEntity()  // @Deprecated
```

---

## Role Sub-Support Access (Legacy API)

In the legacy API, `Role` exposes the same sub-supports as ExecutionSupport. Call them directly on the `role` parameter:

```java
// State machine
StateSupport state = role.getStateSupport();
boolean inMain  = state.inState(mainStateIndex);
boolean inSub   = state.inSubState(subStateIndex);
state.setState(mainStateIndex, subStateIndex);
Object iterTarget = state.getInteractionIterationTarget();

// Position slots -- NavTarget = slot 0 by convention
MarkedEntitySupport marked = role.getMarkedEntitySupport();
marked.getStoredPosition(0).set(x, y, z); // write NavTarget (dispatches pathfinding)
marked.getStoredPosition(0);              // read current NavTarget

// Named flags
FlagsComponent flags = role.getFlagsComponent();
boolean val = flags.getFlag("MyFlag");
flags.setFlag("MyFlag", true);

// World support
WorldSupport world = role.getWorldSupport();

// Combat
CombatSupport combat = role.getCombatSupport();
```

> **NavTarget convention:** Slot 0 of `MarkedEntitySupport` is the pathfinding target. Writing it dispatches navigation to the built-in `ReadPosition` / `Seek` sensors and `BodyMotionFind` motion.

---

## Reading ECS Components in Custom Actions/Sensors

Always read components via `store.getComponent(ref, ComponentType.getComponentType())`. Null-check the result before use.

```java
// In execute() or matches():
JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
if (jobTarget == null) return true; // or false for sensors

TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
if (transform == null) return true;

Vector3d pos = transform.getPosition();
```

For **writing** ECS component changes, use `CommandBuffer` rather than mutating the store directly. Direct `Inventory` mutations (e.g. `inventory.addItemStack(...)`) are safe from actions.

---

## Builder Infrastructure

### Holder types

| Holder | JSON type | Builder method |
|---|---|---|
| `StringHolder` | `"text"` | `requireString(...)` / `getString(...)` |
| `IntHolder` | `42` | `requireInt(...)` / `getInt(...)` |
| `DoubleHolder` | `3.14` | `requireDouble(...)` / `getDouble(...)` |
| `BooleanHolder` | `true` | `getBoolean(...)` (always optional) |
| `AssetHolder` | `"Namespace:AssetName"` | `requireAsset(...)` / `getAsset(...)` |
| `EnumHolder<E>` | `"EnumValue"` | `getEnum(...)` |

All holders live in `com.hypixel.hytale.server.npc.asset.builder.holder.*`.

**Always** resolve holder values at build time: `holder.get(support.getExecutionContext())`. Never call `holder.get(support)`.

### Builder method signatures (same pattern for all holder types)

```java
// Required -- load error if key absent
this.requireString(data, "Key", holder, validator,      state, "description", since);
this.requireInt   (data, "Key", holder, validator,      state, "description", since);
this.requireDouble(data, "Key", holder, validator,      state, "description", since);

// Optional -- uses defaultValue if key absent
this.getString(data, "Key", holder, defaultValue, validator, state, "description", since);
this.getInt   (data, "Key", holder, defaultValue, validator, state, "description", since);
this.getDouble(data, "Key", holder, defaultValue, validator, state, "description", since);

// Boolean (always optional)
this.getBoolean(data, "Key", holder, defaultValue, state, "description", since);
```

`since` is a nullable `String` for the version this field was added. Pass `null` if not applicable.

### Available validators

```java
// com.hypixel.hytale.server.npc.asset.builder.validators
IntSingleValidator.greaterEqual0()   // value >= 0
IntSingleValidator.greater0()        // value > 0
DoubleSingleValidator.greater0()     // double > 0
```

`IntSingleValidator.range(...)` does NOT exist. Use `greaterEqual0()` or `greater0()` only.

### Built-in fields from base classes

`BuilderActionBase` and `BuilderSensorBase` both automatically read:
- `"Once"` (boolean, default `false`) -- execute/match only on the first trigger
- `"Enabled"` (boolean, default `true`) -- whether the component is active

`BuilderActionWithDelay` additionally reads:
- `"Delay"` (range, default `[1,1]`) -- tick delay before execution

---

## Worked Example: Positional Sensor Reading ECS Component

Full sensor that fires when the entity is within range of a job target and exposes the position for paired `Seek` or `BodyMotionFind`:

```java
package com.hytalecolonies.npc.sensors.common;

import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hypixel.hytale.server.npc.sensorinfo.PositionProvider;
import com.hytalecolonies.components.jobs.JobTargetComponent;

public class SensorJobTarget extends SensorBase {

    private final double           range;
    private final PositionProvider positionProvider = new PositionProvider();

    public SensorJobTarget(@Nonnull BuilderSensorJobTarget builder, @Nonnull BuilderSupport support) {
        super(builder);
        this.range = builder.getRange(support);
    }

    @Override
    public boolean matches(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Role role,
            double dt,
            @Nonnull Store<EntityStore> store) {

        if (!super.matches(ref, role, dt, store)) {
            positionProvider.clear();
            return false;
        }

        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jobTarget == null || jobTarget.targetPosition == null) {
            positionProvider.clear();
            return false;
        }
        Vector3i target = jobTarget.targetPosition;

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            positionProvider.clear();
            return false;
        }
        Vector3d pos = transform.getPosition();
        double dx = target.x + 0.5 - pos.x;
        double dz = target.z + 0.5 - pos.z;
        if (dx * dx + dz * dz > range * range) {
            positionProvider.clear();
            return false;
        }

        positionProvider.setTarget(target.x + 0.5, target.y, target.z + 0.5);
        return true;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return positionProvider;
    }
}
```

---

## Package Conventions

| Contents | Package |
|---|---|
| Custom action builders + runtimes (role-specific) | `com.hytalecolonies.npc.actions.<role>` |
| Custom action builders + runtimes (shared) | `com.hytalecolonies.npc.actions.common` |
| Custom sensor builders + runtimes (role-specific) | `com.hytalecolonies.npc.sensors.<role>` |
| Custom sensor builders + runtimes (shared) | `com.hytalecolonies.npc.sensors.common` |

---

## `execute()` Return Value Contract

The return value only matters in **blocking** action lists (`"ActionsBlocking": true`):

| Return | Meaning in blocking mode |
|---|---|
| `true` | Action is done -- advance to the next action |
| `false` | Still in progress -- retry this action next tick |

In **non-blocking** mode (default, `"ActionsBlocking"` absent), the return value is ignored -- all eligible actions run every tick.

**Rule:** Return `true` unconditionally unless the action genuinely spans multiple ticks (pathfinding wait, timer). Success/failure checks belong in sensors, not action return values. Returning `false` in blocking mode means "retry me next tick".

---

## Common Gotchas

| Mistake | Fix |
|---|---|
| `holder.get(support)` | Use `holder.get(support.getExecutionContext())` |
| `IntSingleValidator.range(0, 100)` | Method does not exist; use `greaterEqual0()` or `greater0()` |
| Registering in `start()` | Must be in `setup()` -- NPC roles parse before `start()` |
| Missing `super.execute()` call | Call it first; it sets `triggered = true` for `Once` logic |
| Missing `super.matches()` call | Call it first; it handles `Once` and returns false if already triggered |
| Not clearing `positionProvider` on false return | Always call `positionProvider.clear()` on every false path |
| Action in `StateTransitions` returning `false` | Transition never completes; always return `true` in transition actions |
| Mutating ECS components inside execute() | Use `CommandBuffer` for store writes; direct Inventory calls are OK |
| Using `Role role` parameter with new ExecutionSupport JAR | Swap to `ExecutionSupport executionSupport` and use `executionSupport.getStateSupport()` etc. |

---

## Blackboard

`Blackboard` (`com.hypixel.hytale.server.npc.blackboard.Blackboard`) caches expensive world data (nearby block positions, entity events) shared between NPCs. Access it via the role/world reference inside actions. The built-in `Block`, `BlockType`, and `SearchRay` sensors already use the blackboard internally. Custom sensors that scan blocks should prefer delegating to these built-in types via chaining rather than scanning chunks directly.

For the current blackboard API see `lib/hytale-shared-source/HytaleServer/NPC/src/main/java/com/hypixel/hytale/server/npc/blackboard/`.

---

## Relationship to Other Skills

- **`hytale-npc-templates`** -- covers what goes in NPC role JSON. This skill covers how to *add new JSON types*.
- **`hytale-npc-components`** -- covers reusable JSON instruction/sensor components. This skill covers Java-level custom types.
- **`hytalecolonies-npc-design`** -- canonical HytaleColonies NPC architecture; overrides general patterns where they conflict.

---

## Official Javadoc References

- [`NPCPlugin.registerCoreComponentType()`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/NPCPlugin.html#registerCoreComponentType(java.lang.String,java.util.function.Supplier))
- [`NPCPlugin`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/NPCPlugin.html)
- [`ExecutionSupport`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/instructions/ExecutionSupport.html)
- [`Action`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/instructions/Action.html)
- [`Sensor`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/instructions/Sensor.html)
- [`Role`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/Role.html)
- [`StateSupport`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/support/StateSupport.html)
- [`MarkedEntitySupport`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/support/MarkedEntitySupport.html)
- [`FlagsComponent`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/support/FlagsComponent.html)
- [`CombatSupport`](https://release.server.docs.hytale.com/com/hypixel/hytale/server/npc/role/support/CombatSupport.html)

