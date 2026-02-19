# Hytale NPC Template Reference

This document provides a comprehensive reference for Hytale's JSON-based NPC template and behavior system. It is based on the official documentation and provides a structured overview of the available components.

## Table of Contents

- [Roles](#roles)
- [Motion Controllers](#motion-controllers)
- [Actions](#actions)
- [Sensors](#sensors)
- [Filters (IEntityFilter)](#filters-ientityfilter)
- [Other Components](#other-components)

---

## Roles

Roles define the fundamental attributes and capabilities of an NPC.

- **`Abstract`**: Generic role for an NPC with a core planner and a list of Motion controllers.
- **`Generic`**: A more specific generic role with additional attributes for inventory, hotbar, and equipment.
- **`Variant`**: Creates a new NPC variant based on an existing NPC JSON file.

---

## Motion Controllers

Motion Controllers dictate how an NPC moves through the world.

- **`Dive`**: Provides diving abilities for an NPC.
- **`Fly`**: A flight motion controller.
- **`Walk`**: Provides standard on-ground walking, climbing, and jumping abilities.

---

## Actions

Actions are commands that an NPC can execute.

- **`AddToHostileTargetMemory`**: Adds a sensor-provided target to the NPC's hostile memory.
- **`Appearance`**: Changes the model of the NPC.
- **`ApplyEntityEffect`**: Applies a status effect to the target or self.
- **`Attack`**: Starts an attack.
- **`Beacon`**: Sends a beacon message to other NPCs.
- **`CombatAbility`**: Starts a combat ability.
- **`CompleteTask`**: Completes a player-related task.
- **`Crouch`**: Sets the NPC's crouching state.
- **`DelayDespawn`**: Delays the despawning of the NPC.
- **`Despawn`**: Triggers the NPC to despawn.
- **`Die`**: Kills the NPC.
- **`DisplayName`**: Sets the name displayed above the NPC.
- **`DropItem`**: Drops an item.
- **`FlockBeacon`**: Sends a beacon message to the flock.
- **`FlockState`**: Sets the state for the entire flock.
- **`FlockTarget`**: Sets or clears the locked target for the flock.
- **`IgnoreForAvoidance`**: Sets a target to be ignored for avoidance calculations.
- **`Inventory`**: Adds, removes, or equips items in the NPC's inventory.
- **`JoinFlock`**: Attempts to join or form a flock with a target entity.
- **`LeaveFlock`**: The NPC leaves its current flock.
- **`LockOnInteractionTarget`**: Locks onto the player currently interacting with the NPC.
- **`Log`**: Logs a message to the server console.
- **`MakePath`**: Constructs a transient path for the NPC.
- **`ModelAttachment`**: Sets an attachment on the NPC's model.
- **`Mount`**: Enables a player to mount the entity.
- **`Nothing`**: Does nothing (often used as a placeholder).
- **`Notify`**: Directly notifies a target NPC with a beacon message.
- **`OpenBarterShop`**: Opens a barter shop UI for the interacting player.
- **`OpenShop`**: Opens a shop UI for the interacting player.
- **`OverrideAltitude`**: Temporarily overrides the preferred altitude of a flying NPC.
- **`OverrideAttitude`**: Overrides the NPC's attitude towards a target for a duration.
- **`ParentState`**: Sets the main state of the NPC from within a component.
- **`PickUpItem`**: Picks up a nearby item.
- **`PlaceBlock`**: Places a block at a position returned by a sensor.
- **`PlayAnimation`**: Plays an animation.
- **`PlaySound`**: Plays a sound event.
- **`Random`**: Executes a single random action from a weighted list.
- **`RecomputePath`**: Forces a recomputation of the current path.
- **`ReleaseTarget`**: Clears the locked target.
- **`Remove`**: Erases the target entity from the world.
- **`ResetBlockSensors`**: Resets block sensors.
- **`ResetInstructions`**: Resets instruction lists.
- **`ResetPath`**: Resets the current patrol path.
- **`ResetSearchRays`**: Resets cached positions for search ray sensors.
- **`Role`**: Changes the role of the NPC.
- **`Sequence`**: Executes a list of actions in order.
- **`SetAlarm`**: Sets a named alarm on the NPC.
- **`SetBlockToPlace`**: Sets the block type the NPC will place.
- **`SetFlag`**: Sets a named flag to a boolean value.
- **`SetInteractable`**: Sets whether a player can interact with the NPC.
- **`SetLeashPosition`**: Sets the NPC's current position as its leash/spawn point.
- **`SetMarkedTarget`**: Explicitly sets a marked target in a given slot.
- **`SetStat`**: Sets or modifies an entity stat on the NPC.
- **`Spawn`**: Spawns another NPC.
- **`SpawnParticles`**: Spawns a particle system.
- **`StartObjective`**: Starts an objective for the interacting player.
- **`State`**: Sets the state of the NPC.
- **`StorePosition`**: Stores the position from an attached sensor.
- **`Timeout`**: Delays an action or inserts a delay in a sequence.
- **`TimerContinue`**: Continues a paused timer.
- **`TimerModify`**: Modifies the values of a timer.
- **`TimerPause`**: Pauses a timer.
- **`TimerRestart`**: Restarts a timer.
- **`TimerStart`**: Starts a new timer.
- **`TimerStop`**: Stops a timer.
- **`ToggleStateEvaluator`**: Enables or disables the NPC's state evaluator.
- **`TriggerSpawnBeacon`**: Triggers the nearest spawn beacon.
- **`TriggerSpawners`**: Triggers manual spawn markers in a radius.

---

## Sensors

Sensors are used to test conditions in the world and provide information to other components.

- **`AdjustPosition`**: Applies an offset to a position returned by a wrapped sensor.
- **`Age`**: Triggers when the NPC's age is within a certain range.
- **`Alarm`**: Checks the state of a named alarm.
- **`And`**: Logical AND of a list of sensors.
- **`Animation`**: Checks if a specific animation is playing.
- **`Any`**: Always returns true.
- **`Beacon`**: Checks for broadcasted messages from nearby NPCs.
- **`Block`**: Checks for a set of blocks in the nearby area.
- **`BlockChange`**: Matches when a block is changed or interacted with.
- **`BlockType`**: Checks if the block at a given position matches a block set.
- **`CanInteract`**: Checks if the interacting player can interact with this NPC.
- **`CanPlaceBlock`**: Tests if a block can be placed at a relative position.
- **`CombatActionEvaluator`**: Funnels information to actions/motions from the combat action evaluator.
- **`Count`**: Checks for a certain number of NPCs or players within a range.
- **`Damage`**: Tests if the NPC has suffered damage.
- **`DroppedItem`**: Triggers if a given item is within a certain range.
- **`EntityEvent`**: Matches when a nearby entity is damaged, killed, or interacted with.
- **`Eval`**: Evaluates a JavaScript expression.
- **`Flag`**: Tests if a named flag is set.
- **`FlockCombatDamage`**: Tests if the NPC's flock has received combat damage.
- **`FlockLeader`**: Tests for the presence of a flock leader.
- **`HasHostileTargetMemory`**: Checks if there is a hostile target in memory.
- **`HasInteracted`**: Checks if the interacting player has interacted with this NPC.
- **`HasTask`**: Checks if the interacting player has a specific task.
- **`InAir`**: Tests if the NPC is not on the ground.
- **`InWater`**: Checks if the NPC is in water.
- **`InflictedDamage`**: Tests if the NPC or its flock has inflicted damage.
- **`InteractionContext`**: Checks if the player has interacted in a given context.
- **`IsBusy`**: Tests if the NPC is in a "busy" state.
- **`Kill`**: Tests if the NPC has made a kill.
- **`Leash`**: Triggers when the NPC is outside its leash range.
- **`Light`**: Checks the light level at the NPC's position.
- **`Mob`**: A general-purpose sensor to detect other entities (NPCs or players).
- **`MotionController`**: Tests if a specific motion controller is active.
- **`Nav`**: Queries the state of the pathfinder.
- **`Not`**: Inverts the result of a wrapped sensor.
- **`OnGround`**: Tests if the NPC is on the ground.
- **`Or`**: Logical OR of a list of sensors.
- **`Path`**: Finds a path based on various criteria.
- **`Player`**: Detects players matching specific attributes and filters.
- **`Random`**: Alternates between returning true and false for random durations.
- **`ReadPosition`**: Reads a stored position.
- **`SearchRay`**: Fires a ray to check for a specific block.
- **`Self`**: Tests if the NPC itself matches a set of entity filters.
- **`State`**: Tests for a specific NPC state.
- **`Switch`**: Checks if a computed boolean is true.
- **`Target`**: Tests if a given target matches a series of criteria.
- **`Time`**: Checks if the world time is within a specified range.
- **`Timer`**: Tests the value of a named timer.
- **`ValueProviderWrapper`**: Wraps a sensor and passes additional parameter overrides.
- **`Weather`**: Matches the current weather against a set of weather types.

---

## Filters (IEntityFilter)

Filters are used within sensors (like `Mob` and `Player`) to refine the selection of targets.

- **`Altitude`**: Matches targets within a defined height range above the ground.
- **`And`**: Logical AND of a list of filters.
- **`Attitude`**: Matches the attitude towards the locked target.
- **`Combat`**: Checks the target's combat state.
- **`Flock`**: Tests for flock membership and related properties.
- **`HeightDifference`**: Matches entities within a given height range relative to the NPC.
- **`InsideBlock`**: Matches if the entity is inside a specific set of blocks.
- **`Inventory`**: Tests conditions related to the entity's inventory.
- **`ItemInHand`**: Checks if the entity is holding a specific item.
- **`LineOfSight`**: Matches if there is a clear line of sight to the target.
- **`MovementState`**: Checks if the entity is in a given movement state.
- **`NPCGroup`**: Matches entities belonging to specified NPC groups.
- **`Not`**: Inverts the result of a wrapped filter.
- **`Or`**: Logical OR of a list of filters.
- **`SpotsMe`**: Checks if the target entity can see the NPC.
- **`StandingOnBlock`**: Matches the block directly beneath the entity.
- **`Stat`**: Matches stat values of the entity.
- **`ViewSector`**: Matches entities within the NPC's view sector.

---

## Other Components

- **`Instruction`**: The core building block of NPC behavior, combining a `Sensor` with `BodyMotion`, `HeadMotion`, and `Actions`.
- **`Reference`**: A reusable instruction that can be referenced from elsewhere in the template.
- **`StateTransition`**: Defines actions to be executed when moving between states.
- **`BodyMotion`**: Defines how the NPC's body moves (e.g., `Seek`, `Wander`, `Flee`, `Path`).
- **`HeadMotion`**: Defines how the NPC's head moves (e.g., `Watch`, `Observe`, `Aim`).
- **`ISensorEntityPrioritiser`**: Used in sensors to prioritize multiple matching entities (e.g., by `Attitude`).
- **`ISensorEntityCollector`**: Used in sensors to process all matched entities (e.g., `CombatTargets`).
- **`Path`**: Defines a transient path for an NPC to follow.
- **`RelativeWaypointDefinition`**: Defines a single point in a relative path.
- **`WeightedAction`**: A wrapper for an action used in a `Random` action list.
