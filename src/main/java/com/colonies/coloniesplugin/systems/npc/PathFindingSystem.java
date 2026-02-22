package com.colonies.coloniesplugin.systems.npc;

import com.colonies.coloniesplugin.components.npc.ColonistComponent;
import com.colonies.coloniesplugin.components.npc.MoveToTargetComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.builtin.path.path.TransientPath;
import java.util.ArrayDeque;
import com.hypixel.hytale.builtin.path.waypoint.RelativeWaypointDefinition;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class PathFindingSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger logger = HytaleLogger.forEnclosingClass();

    public PathFindingSystem() {
    }

    @Override
    public void tick(float value,
                     int index,
                     @NonNull ArchetypeChunk<EntityStore> archetypeChunk,
                     @NonNull Store<EntityStore> store,
                     @NonNull CommandBuffer<EntityStore> commandBuffer) {
        MoveToTargetComponent moveToTargetComponent = archetypeChunk.getComponent(index, MoveToTargetComponent.getComponentType());
        assert moveToTargetComponent != null;
        Vector3d target = moveToTargetComponent.target;

        // Get ref to the colonist entity.
        Ref<EntityStore> colonistRef = archetypeChunk.getReferenceTo(index);

        // Remove the MoveToTargetComponent immediately to prevent this system from running on this entity again.
        commandBuffer.removeComponent(colonistRef, MoveToTargetComponent.getComponentType());

        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        assert transform != null;

        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        assert npc != null;

        logger.atInfo().log("Assigning direct path for NPC from %s to %s", transform.getPosition(), target);

        // Build a direct path using a single relative waypoint (angle and distance)
        Vector3d from = transform.getPosition();
        Vector3d to = target;
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        float angle = (float) Math.atan2(dz, dx);

        ArrayDeque<RelativeWaypointDefinition> instructions = new ArrayDeque<>();
        instructions.add(new RelativeWaypointDefinition(angle, distance));

        // Use the NPC's current head rotation if available, else zero
        // ToDo: (If you want to use head rotation, you can get HeadRotation component here)
        npc.getPathManager().setTransientPath(
            TransientPath.buildPath(from, transform.getRotation(), instructions, 1.0)
        );
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return Query.and(MoveToTargetComponent.getComponentType());
    }
}