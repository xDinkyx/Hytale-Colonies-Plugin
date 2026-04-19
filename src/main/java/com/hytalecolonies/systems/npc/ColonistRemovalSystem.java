package com.hytalecolonies.systems.npc;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hytalecolonies.components.jobs.ConstructorJobComponent;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ClaimBlockUtil;

/**
 * Fires when a colonist entity is removed from the world (death, despawn, etc.)
 * and drops all items from its inventory onto the ground at its last known position.
 */
public class ColonistRemovalSystem extends RefSystem<EntityStore>
{

    private final Query<EntityStore> query = Query.and(ColonistComponent.getComponentType());

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref,
                              @Nonnull AddReason reason,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull CommandBuffer<EntityStore> commandBuffer)
    {
        // No action on add.
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref,
                               @Nonnull RemoveReason reason,
                               @Nonnull Store<EntityStore> store,
                               @Nonnull CommandBuffer<EntityStore> commandBuffer)
    {
        // Always release any block claims held by this colonist (clearing target + pending build queue),
        // regardless of whether the removal is a permanent death or a chunk unload.
        releaseBlockClaims(ref, store);

        if (reason == RemoveReason.UNLOAD)
            return; // Chunk unload -- colonist is not gone, just unloaded. Don't drop inventory.

        ColonistComponent colonist = store.getComponent(ref, ColonistComponent.getComponentType());
        if (colonist == null)
            return;

        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        String uuidStr = uuid != null ? uuid.getUuid().toString() : "<unknown>";

        DebugLog.info(DebugCategory.COLONIST_LIFECYCLE,
                      "[ColonistRemoval] Colonist '%s' (UUID: %s) removed from world (reason: %s).",
                      colonist.getColonistName(),
                      uuidStr,
                      reason);

        dropInventory(ref, store, commandBuffer);
    }

    /**
     * Releases any block claims held by this colonist:
     * the current job-target claim (clearing) and all pre-claimed build-queue positions.
     * Captured positions are snapshotted before the removal, then unclaimed on the world thread.
     */
    private void releaseBlockClaims(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store)
    {
        ConstructorJobComponent constructorJob = store.getComponent(ref, ConstructorJobComponent.getComponentType());
        List<Vector3i> buildQueue = (constructorJob != null && !constructorJob.pendingBuildQueue.isEmpty())
                ? new ArrayList<>(constructorJob.pendingBuildQueue)
                : null;
        if (constructorJob != null)
            constructorJob.pendingBuildQueue.clear();

        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        Vector3i clearingTarget = jobTarget != null ? jobTarget.targetPosition : null;

        if (buildQueue == null && clearingTarget == null)
            return;

        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            if (clearingTarget != null)
                ClaimBlockUtil.unclaimBlock(world, clearingTarget);
            if (buildQueue != null)
            {
                for (Vector3i pos : buildQueue)
                    ClaimBlockUtil.unclaimBlock(world, pos);
            }
        });
    }

    /**
     * Collects all items from the colonist's hotbar and storage and drops them
     * as item entities at the colonist's current position.
     */
    private void dropInventory(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer)
    {

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null)
        {
            DebugLog.warning(DebugCategory.COLONIST_LIFECYCLE,
                             "[ColonistRemoval] [%s] No TransformComponent -- cannot drop inventory.",
                             DebugLog.npcId(ref, store));
            return;
        }

        NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
        if (npcEntity == null || npcEntity.getInventory() == null)
        {
            DebugLog.warning(DebugCategory.COLONIST_LIFECYCLE,
                             "[ColonistRemoval] [%s] No NPCEntity or inventory -- cannot drop inventory.",
                             DebugLog.npcId(ref, store));
            return; // No inventory to drop.
        }

        List<ItemStack> toDrop = new ArrayList<>();
        collectItems(npcEntity.getInventory().getHotbar(), toDrop);
        collectItems(npcEntity.getInventory().getStorage(), toDrop);

        if (toDrop.isEmpty())
        {
            return;
        }

        Vector3d dropPosition = transform.getPosition().clone().add(0.0, 0.5, 0.0);
        Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(store, toDrop, dropPosition, Vector3f.ZERO);
        commandBuffer.addEntities(drops, AddReason.SPAWN);

        DebugLog.info(DebugCategory.COLONIST_LIFECYCLE,
                      "[ColonistRemoval] [%s] Dropped %d item stack(s) at %s.",
                      DebugLog.npcId(ref, store),
                      toDrop.size(),
                      dropPosition);
    }

    /**
     * Removes all non-empty stacks from {@code container} and adds them to {@code out}.
     */
    private void collectItems(@Nullable ItemContainer container, @Nonnull List<ItemStack> out)
    {
        if (container == null)
            return;
        List<ItemStack> dropped = container.dropAllItemStacks();
        if (dropped != null)
            out.addAll(dropped);
    }

    @Override @Nullable public Query<EntityStore> getQuery()
    {
        return query;
    }
}
