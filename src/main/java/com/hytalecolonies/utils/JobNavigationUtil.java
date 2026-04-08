package com.hytalecolonies.utils;

import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * Utilities for atomically claiming a block and dispatching navigation toward it.
 * Methods that mutate the store are designed to be called inside {@code world.execute()}.
 */
public final class JobNavigationUtil {

    private JobNavigationUtil() {}

    /**
     * Claims the block at the given position, sets it as the colonist's job target,
     * and dispatches navigation. Must be called inside {@code world.execute()}.
     *
     * @return {@code true} if the block was claimed and navigation dispatched;
     *         {@code false} if the claim failed (another colonist claimed it first).
     */
    public static boolean claimAndNavigateTo(World world,
                                              Store<EntityStore> entityStore,
                                              Ref<EntityStore> entityRef,
                                              UUID colonistUuid,
                                              Vector3i block,
                                              String claimType) {
        if (!ClaimBlockUtil.claimBlock(world, block, colonistUuid, claimType)) {
            return false;
        }
        setJobTarget(entityStore, entityRef, block);
        dispatchNavigation(entityStore, entityRef, block);
        return true;
    }

    /**
     * Sets {@link JobTargetComponent#targetPosition} to the given block, creating the
     * component if it does not already exist on the entity.
     */
    public static void setJobTarget(Store<EntityStore> entityStore,
                                     Ref<EntityStore> entityRef,
                                     Vector3i block) {
        JobTargetComponent jobTarget = entityStore.getComponent(entityRef, JobTargetComponent.getComponentType());
        if (jobTarget == null) {
            entityStore.addComponent(entityRef, JobTargetComponent.getComponentType(), new JobTargetComponent(block));
        } else {
            jobTarget.setTargetPosition(block);
        }
    }

    /**
     * Writes the block centre as the navigation target by adding or updating
     * {@link MoveToTargetComponent}, which {@code PathFindingSystem} uses to forward
     * the destination to the NPC role's navigation slot.
     */
    public static void dispatchNavigation(Store<EntityStore> entityStore,
                                           Ref<EntityStore> entityRef,
                                           Vector3i block) {
        if (!entityRef.isValid()) return;
        Vector3d navigationTarget = new Vector3d(block.x + 0.5, block.y, block.z + 0.5);
        MoveToTargetComponent existingMove = entityStore.getComponent(entityRef, MoveToTargetComponent.getComponentType());
        if (existingMove != null) {
            existingMove.target = navigationTarget;
        } else {
            entityStore.addComponent(entityRef, MoveToTargetComponent.getComponentType(),
                    new MoveToTargetComponent(navigationTarget));
        }
    }
}
