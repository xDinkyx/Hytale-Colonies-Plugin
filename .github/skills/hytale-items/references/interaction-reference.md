# Hytale Interaction Reference

This document provides a reference for the Hytale Interaction system, as documented in the `interaction-reference.mdx` file from the HytaleModding/site repository.

## The Interaction Base Type

All interactions of every type below are inherited from `Interaction` and have these fields.

**Interaction Fields**

| Field Name | Type | Required? | Notes |
|---|---|---|---|
| `ViewDistance` | `Double` (Default: 96.0) | No | Distance in blocks at which other players can see the effects from this interaction. |
| `Effects` | `InteractionEffects` | No | Sound, animations, and particle effects that should trigger when this interaction begins executing. |
| `HorizontalSpeedMultiplier` | `Float` (Default: 1.0) | No | Multiplier applied to the User entity's movement speed while this interaction is executing |
| `RunTime` | `Float` | No | If provided, this interaction will continue executing for at least this long before the interaction chain moves onto the next interaction. |
| `CancelOnItemChange` | `Boolean` (Default: true) | No | If true, this interaction will be cancelled if the User entity's held item changes during execution. |
| `Rules` | `InteractionRules` | Yes | If provided, adds additional limitations and cancellation conditions to this interaction. |
| `Settings` | `Map` (Key: `GameMode`, Value: `InteractionSettings`) | No | If provided, adds additional per-GameMode settings to the interaction. |
| `Camera` | `InteractionCameraSettings` | No | Arrays of camera motion keyframes for cutscenes and reveals. |

---

## Flow Control Interactions

These are interaction types used to control the flow of an interaction chain and allow interactions to be composed into more complex behaviors.

*   **`Condition`**: Fails if a condition (e.g., `Jumping`, `Swimming`) is not met.
*   **`FirstClick`**: Takes different paths for `Click` vs. `Hold` inputs.
*   **`Interrupt`**: Cancels other running interaction chains.
*   **`Parallel`**: Forks multiple `RootInteraction` assets to run concurrently.
*   **`Repeat`**: Forks a `RootInteraction` repeatedly.
*   **`Replace`**: Executes a `RootInteraction` from an `InteractionVar` on an item, with a default fallback.
*   **`Selector`**: Finds entities/blocks in an area and forks interactions for each.
*   **`Serial`**: Executes a list of `Interaction` assets in sequence.

---

## Combo Chains

Hytale has a rich "combo chaining" system, which allows several inputs in a row to result in different attacks and special moves.

*   **`Chaining`**: Cycles through a list of `Next` interactions based on a shared `ChainId`. Supports `Flags` for alternative behaviors.
*   **`CancelChain`**: Resets progress and flags for a `ChainId`.
*   **`ChainFlag`**: Activates a flag for a `ChainId`.

---

## Charging Interactions

These interactions are based on holding an input button.

*   **`Charging`**: Delays while an input is held, then chooses a `Next` interaction based on charge time. Supports `Forks` for custom actions during the charge.
*   **`Wielding`**: A specialized `Charging` interaction for blocking, applying damage/knockback modifiers based on attack angle.

---

## Block Interactions

These interactions are meant to reason about and modify blocks in the world. They use a `Block Target`.

*   **`BlockCondition`**: Fails if the target block doesn't match specified conditions (type, state, tag).
*   **`BreakBlock`**: Breaks or harvests the target block.
*   **`ChangeBlock`**: Changes a block from one type to another.
*   **`ChangeState`**: Changes a block's state.
*   **`PlaceBlock`**: Places a block, similar to right-clicking.

---

## Item Interactions

These interactions interface with an entity's inventory.

*   **`AddItem`**: Adds an item to the user's inventory. (Note: buggy, `ModifyInventory` is preferred).
*   **`ModifyInventory`**: A robust interaction to add, remove, or modify items, quantity, and durability.
*   **`EquipItem`**: Equips an armor item from the user's hand.
*   **`PickupItem`**: Picks up a targeted item entity from the world.

---

## Entity Interactions

These interactions affect entities in the world.

*   **`DamageEntity`**: Deals damage to a target entity, with complex options for angled and targeted (body part) damage.
*   **`Projectile`**: Spawns a projectile.
*   **`RemoveEntity`**: Despawns a non-player entity.
*   **`SendMessage`**: Sends a chat message to the owner entity.

---

## Stat Interactions

These interactions modify entity stats like health, mana, etc.

*   **`ChangeStat`**: Adds or sets a stat value, either as an absolute number or a percentage.
*   **`StatsCondition`**: Fails if the user entity cannot afford a specified stat cost.

---

## EntityEffect Interactions

These interactions apply or remove status effects (buffs/debuffs).

*   **`ApplyEffect`**: Applies an `EntityEffect` to a target.
*   **`ClearEntityEffect`**: Removes an `EntityEffect` from a target.
*   **`EffectCondition`**: Fails based on whether a target has certain effects.

---

## Farming Interactions

These interactions support the in-game farming system.

*   **`ChangeFarmingStage`**: Modifies the growth stage of a crop.
*   **`FertilizeSoil`**: Fertilizes tilled soil.
*   **`HarvestCrop`**: Harvests a crop and gives the drops to the player.
*   **`UseWateringCan`**: Waters tilled soil.
