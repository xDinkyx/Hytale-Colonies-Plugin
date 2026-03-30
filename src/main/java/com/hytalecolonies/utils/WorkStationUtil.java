package com.hytalecolonies.utils;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/** Utilities for resolving the {@link WorkStationComponent} from a colonist entity. */
public final class WorkStationUtil {

    private WorkStationUtil() {}

    /**
     * Resolves the {@link WorkStationComponent} for the colonist's current workstation.
     * Returns {@code null} if the colonist has no job, no workstation position, or the
     * workstation block entity no longer exists.
     */
    @Nullable
    public static WorkStationComponent resolve(Store<EntityStore> entityStore,
                                               Ref<EntityStore> entityRef) {
        JobComponent job = entityStore.getComponent(entityRef, JobComponent.getComponentType());
        if (job == null) {
            return null;
        }

        Vector3i workStationPosition = job.getWorkStationBlockPosition();
        if (workStationPosition == null) {
            return null;
        }

        World world = entityStore.getExternalData().getWorld();
        return resolveAt(world, workStationPosition);
    }

    /**
     * Resolves the {@link WorkStationComponent} at the given world position.
     * Returns {@code null} if there is no block entity or no workstation component there.
     */
    @Nullable
    public static WorkStationComponent resolveAt(World world, Vector3i position) {
        Ref<ChunkStore> blockEntity = BlockModule.getBlockEntity(world, position.x, position.y, position.z);
        if (blockEntity == null) {
            return null;
        }
        return blockEntity.getStore().getComponent(blockEntity, WorkStationComponent.getComponentType());
    }
}
