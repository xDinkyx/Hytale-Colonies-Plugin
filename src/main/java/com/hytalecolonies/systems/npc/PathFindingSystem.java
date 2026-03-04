package com.hytalecolonies.systems.npc;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.path.WorldPath;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.entities.PathManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * A one-shot system that runs when an entity has a {@link MoveToTargetComponent}.
 * It calculates a path to the target and assigns it to the NPC's {@link PathManager},
 * then removes the component to prevent running again.
 * ToDo:
 * - Maybe this is not the correct type of system. Its probably better to use a RefChangeSystem<> that triggers when MoveToTargetComponent is added. 
 *   This way we can guarantee it runs immediately when the component is added, instead of waiting for the next tick.
 *   Alternatively we keep as is. A path is calculated when changing job state. This way we can ignore world changes and optimizations.
 * - Stop NPC following old path when new one is assigned. Now it first completes its path before following the new one.
 * - Store the calculated path. Reload path instead of recalculating. 
 * - Optimize pathfinding by only recalculating when necessary (e.g. target moved, path blocked, blocks changed, etc.). 
 */
public class PathFindingSystem extends EntityTickingSystem<EntityStore> {

    public PathFindingSystem() {
    }

    @Override
    public void tick(
            float value,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);

        // Get relevant components.
        MoveToTargetComponent moveTo = store.getComponent(entityRef, MoveToTargetComponent.getComponentType());
        TransformComponent transformComponent = store.getComponent(entityRef, TransformComponent.getComponentType());
        NPCEntity npcEntity = store.getComponent(entityRef, NPCEntity.getComponentType());
        UUIDComponent uuidComponent = store.getComponent(entityRef, UUIDComponent.getComponentType());

        // This is a one-shot system. Remove the component immediately so we don't process this entity again.
        commandBuffer.removeComponent(entityRef, MoveToTargetComponent.getComponentType());

        if (moveTo == null || transformComponent == null || npcEntity == null || uuidComponent == null) {
            HytaleColoniesPlugin.LOGGER.atWarning().log(String.format("Entity %s is missing required components for PathFindingSystem", index));
            return;
        }

        Vector3d from = transformComponent.getTransform().getPosition();
        Vector3d to = moveTo.target;

        // Create a new WorldPath object with the start and end points.
        // This path is "transient" and only exists for this movement.
        ObjectArrayList<Transform> waypoints = new ObjectArrayList<>();
        waypoints.add(transformComponent.getTransform().clone()); // Current location
        waypoints.add(new Transform(to)); // Target location
        WorldPath transientPath = new WorldPath("transient-path-" + uuidComponent.getUuid(), waypoints);

        // Get the NPC's PathManager and assign the new transient path.
        PathManager pathManager = npcEntity.getPathManager();
        if (pathManager != null) {
            pathManager.setTransientPath(transientPath);
        }

        // Display debug shapes to visualize the path for 20 seconds.
        showDebugPath(store.getExternalData().getWorld(), from, to);
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        // This system will only run on entities that have the MoveToTargetComponent.
        return Query.and(MoveToTargetComponent.getComponentType());
    }

    private void showDebugPath(@Nonnull World world, @Nonnull Vector3d from, @Nonnull Vector3d to) {
        DebugUtils.addSphere(world, from, DebugUtils.COLOR_BLUE, 0.5, 20.0f);
        DebugUtils.addSphere(world, to, DebugUtils.COLOR_RED, 0.5, 20.0f);
        DebugUtils.addLine(world, from, to, DebugUtils.COLOR_GREEN, 0.1, 20.0f, true);
    }
}