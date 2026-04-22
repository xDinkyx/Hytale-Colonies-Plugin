package com.hytalecolonies.systems.jobs;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.ConstructionOrderStore;
import com.hytalecolonies.components.jobs.ConstructorJobComponent;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobRunCounterComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hytalecolonies.utils.ColonistLeashUtil;
import com.hytalecolonies.utils.ColonistStateUtil;
import com.hytalecolonies.utils.ConstructorUtil;
import com.hytalecolonies.utils.JobNavigationUtil;
import com.hytalecolonies.utils.WorkStationUtil;


/**
 * Reacts to block-placed and block-broken notifications for constructor colonists,
 * driving the clearing → retrieving → constructing work cycle.
 *
 * <p>Per-tick block damage and the place action are applied by the NPC role JSON pipeline
 * ({@code Colonist_Constructor.json}). This system handles state transitions after each
 * notification flag is set.
 *
 * <p>The initial dispatch from {@link JobState#WaitingForWork} is handled by
 * {@link ConstructorJobCheckSystem} (runs every 2 seconds).
 */
public class ConstructorWorkingSystem extends EntityTickingSystem<EntityStore>
{

    private static final Query<EntityStore> QUERY =
            Query.and(ConstructorJobComponent.getComponentType(), JobRunCounterComponent.getComponentType(), JobComponent.getComponentType());

    @Nonnull @Override public Query<EntityStore> getQuery()
    {
        return QUERY;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer)
    {

        JobComponent job = chunk.getComponent(index, JobComponent.getComponentType());
        if (job == null)
            return;
        JobRunCounterComponent counter = chunk.getComponent(index, JobRunCounterComponent.getComponentType());
        if (counter == null)
            return;
        ConstructorJobComponent constructorJob = chunk.getComponent(index, ConstructorJobComponent.getComponentType());
        if (constructorJob == null)
            return;

        JobState state = job.getCurrentTask();

        if (state == JobState.WorkingClearing && job.blockBrokenNotification)
        {
            job.blockBrokenNotification = false;
            counter.count++;
            dispatchClearingAdvance(chunk.getReferenceTo(index), store, job, counter);
            return;
        }

        if ((state == JobState.WorkingRetrievingBlocks || state == JobState.WorkingConstructing) && job.itemsRetrievedNotification)
        {
            job.itemsRetrievedNotification = false;
            counter.count = 0;
            dispatchBuildStart(chunk.getReferenceTo(index), store, job);
            return;
        }

        if (state == JobState.WorkingConstructing && job.blockPlacedNotification)
        {
            job.blockPlacedNotification = false;
            dispatchBuildAdvance(chunk.getReferenceTo(index), store, job, constructorJob);
        }
    }

    private static void dispatchClearingAdvance(@Nonnull Ref<EntityStore> colonistRef,
                                              @Nonnull Store<EntityStore> store,
                                              @Nonnull JobComponent job,
                                              @Nonnull JobRunCounterComponent counter)
    {
        Vector3i wsPos = job.getWorkStationBlockPosition();
        if (wsPos == null)
        {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }

        World world = store.getExternalData().getWorld();
        WorkStationComponent wsBase = WorkStationUtil.getWorkStationAt(world, wsPos);
        if (wsBase == null)
        {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }
        if (WorkStationUtil.getConstructorWorkStationAt(world, wsPos) == null)
        {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }
        ConstructionOrderStore.Entry order = WorkStationUtil.getConstructionOrderForWorkstation(world, wsPos);

        String npcId = DebugLog.npcId(colonistRef, store);
        boolean quotaReached = counter.count >= wsBase.blocksPerRun;

        JobTargetComponent jt = store.getComponent(colonistRef, JobTargetComponent.getComponentType());
        @Nullable final Vector3i currentPos = jt != null ? jt.targetPosition : null;
        UUIDComponent uuid = store.getComponent(colonistRef, UUIDComponent.getComponentType());
        final UUID colonistUuid = uuid != null ? uuid.getUuid() : null;
        EntityStore entityStore = world.getEntityStore();
        final BlockSelection prefab = ConstructorUtil.loadPrefab(order);

        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                      "[ConstructorWorking] [%s] Block cleared (%d/%d). quota=%s",
                      npcId,
                      counter.count,
                      wsBase.blocksPerRun,
                      quotaReached);

        world.execute(() -> advanceClearing(colonistRef, entityStore, world, currentPos, quotaReached, colonistUuid, order, prefab, npcId));
    }

    private static void dispatchBuildStart(@Nonnull Ref<EntityStore> colonistRef, @Nonnull Store<EntityStore> store, @Nonnull JobComponent job)
    {
        Vector3i wsPos = job.getWorkStationBlockPosition();
        if (wsPos == null)
        {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }

        World world = store.getExternalData().getWorld();
        WorkStationComponent wsBase = WorkStationUtil.getWorkStationAt(world, wsPos);
        if (wsBase == null)
        {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }
        if (WorkStationUtil.getConstructorWorkStationAt(world, wsPos) == null)
        {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }
        ConstructionOrderStore.Entry order = WorkStationUtil.getConstructionOrderForWorkstation(world, wsPos);

        String npcId = DebugLog.npcId(colonistRef, store);
        BlockSelection prefab = ConstructorUtil.loadPrefab(order);
        UUIDComponent uuid = store.getComponent(colonistRef, UUIDComponent.getComponentType());
        final UUID colonistUuid = uuid != null ? uuid.getUuid() : null;
        final int maxBlocks = wsBase.blocksPerRun;
        EntityStore entityStore = world.getEntityStore();

        world.execute(() -> startBuilding(colonistRef, entityStore, world, colonistUuid, prefab, order, maxBlocks, npcId));
    }

    private static void dispatchBuildAdvance(@Nonnull Ref<EntityStore> colonistRef,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull JobComponent job,
                                      @Nonnull ConstructorJobComponent constructorJob)
    {
        Vector3i wsPos = job.getWorkStationBlockPosition();
        if (wsPos == null)
        {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }

        World world = store.getExternalData().getWorld();
        if (WorkStationUtil.getWorkStationAt(world, wsPos) == null || WorkStationUtil.getConstructorWorkStationAt(world, wsPos) == null)
        {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }

        String npcId = DebugLog.npcId(colonistRef, store);
        @Nullable final Vector3i placedPos = constructorJob.pendingBuildQueue.peekFirst();

        ConstructionOrderStore.Entry order = WorkStationUtil.getConstructionOrderForWorkstation(world, wsPos);
        EntityStore entityStore = world.getEntityStore();

        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                      "[ConstructorWorking] [%s] Block placed at %s. Queue remaining after pop: %d.",
                      npcId,
                      placedPos,
                      Math.max(0, constructorJob.pendingBuildQueue.size() - 1));

        world.execute(() -> advanceBuildQueue(colonistRef, entityStore, world, placedPos, order, npcId));
    }

    private static void advanceClearing(
            @Nonnull Ref<EntityStore> colonistRef,
            @Nonnull EntityStore entityStore,
            @Nonnull World world,
            @Nullable Vector3i currentPos,
            boolean quotaReached,
            @Nullable UUID colonistUuid,
            @Nonnull ConstructionOrderStore.Entry order,
            @Nullable BlockSelection prefab,
            @Nonnull String npcId)
    {
        JobComponent liveJob = entityStore.getStore().getComponent(colonistRef, JobComponent.getComponentType());
        if (liveJob == null || liveJob.getCurrentTask() != JobState.WorkingClearing)
            return;

        if (currentPos != null)
            ClaimBlockUtil.unclaimBlock(world, currentPos);

        if (quotaReached)
        {
            clearTarget(entityStore, colonistRef);
            if (currentPos != null)
                ColonistLeashUtil.setLeashToBlockCenter(colonistRef, entityStore.getStore(), currentPos);
            liveJob.collectingDropsSince = System.currentTimeMillis();
            ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.CollectingDrops);
            return;
        }

        // Claim on world thread to prevent two colonists racing to the same block.
        if (colonistUuid == null)
        {
            ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.Idle);
            return;
        }
        Vector3i claimed = ConstructorUtil.claimNextClearingTarget(order, world, prefab, colonistUuid);
        if (claimed != null)
        {
            JobNavigationUtil.setJobTarget(entityStore.getStore(), colonistRef, claimed);
            JobNavigationUtil.dispatchNavigation(entityStore.getStore(), colonistRef, claimed);
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorWorking] [%s] Clearing next block at %s.", npcId, claimed);
            return;
        }

        clearTarget(entityStore, colonistRef);
        boolean trulyDone = ConstructorUtil.findNextClearingTarget(order, world, prefab) == null;
        if (trulyDone)
        {
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorWorking] [%s] Clearing complete -- WorkingRetrievingBlocks.", npcId);
            ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WorkingRetrievingBlocks);
        }
        else
        {
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                          "[ConstructorWorking] [%s] All clearing targets claimed by another colonist -- WaitingForWork.",
                          npcId);
            liveJob.workAvailable = true;
            ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WaitingForWork);
        }
    }

    private static void startBuilding(
            @Nonnull Ref<EntityStore> colonistRef,
            @Nonnull EntityStore entityStore,
            @Nonnull World world,
            @Nullable UUID colonistUuid,
            @Nullable BlockSelection prefab,
            @Nonnull ConstructionOrderStore.Entry order,
            int maxBlocks,
            @Nonnull String npcId)
    {
        JobComponent liveJob = entityStore.getStore().getComponent(colonistRef, JobComponent.getComponentType());
        if (liveJob == null)
            return;
        JobState liveState = liveJob.getCurrentTask();
        if (liveState != JobState.WorkingRetrievingBlocks && liveState != JobState.WorkingConstructing)
            return;

        if (colonistUuid == null || prefab == null)
        {
            liveJob.workAvailable = true;
            ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WaitingForWork);
            return;
        }

        List<Vector3i> claimed = ConstructorUtil.collectAndClaimBuildTargets(order, world, prefab, colonistUuid, maxBlocks);
        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                      "[ConstructorWorking] [%s] Items retrieved. Claimed %d/%d build blocks.",
                      npcId,
                      claimed.size(),
                      maxBlocks);

        if (claimed.isEmpty())
        {
            liveJob.workAvailable = true;
            ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WaitingForWork);
            return;
        }

        List<ItemStack> required = ConstructorUtil.getRequiredItemStacks(order, prefab, claimed);
        LivingEntity colonist = (LivingEntity)EntityUtils.getEntity(colonistRef, entityStore.getStore());
        if (colonist != null && colonist.getInventory() != null)
        {
            var storage = colonist.getInventory().getStorage();
            if (storage != null)
            {
                for (ItemStack stack : required)
                {
                    storage.addItemStack(stack);
                }
                DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorWorking] [%s] Gave %d item stack type(s) to inventory.", npcId, required.size());
            }
        }

        ConstructorJobComponent liveConstructorJob = entityStore.getStore().getComponent(colonistRef, ConstructorJobComponent.getComponentType());
        if (liveConstructorJob != null)
        {
            liveConstructorJob.pendingBuildQueue.clear();
            liveConstructorJob.pendingBuildQueue.addAll(claimed);
        }

        Vector3i first = claimed.get(0);
        JobNavigationUtil.setJobTarget(entityStore.getStore(), colonistRef, first);
        JobNavigationUtil.dispatchNavigation(entityStore.getStore(), colonistRef, first);
        ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WorkingConstructing);
        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorWorking] [%s] Building first block at %s.", npcId, first);
    }

    private static void advanceBuildQueue(
            @Nonnull Ref<EntityStore> colonistRef,
            @Nonnull EntityStore entityStore,
            @Nonnull World world,
            @Nullable Vector3i placedPos,
            @Nonnull ConstructionOrderStore.Entry order,
            @Nonnull String npcId)
    {
        JobComponent liveJob = entityStore.getStore().getComponent(colonistRef, JobComponent.getComponentType());
        if (liveJob == null || liveJob.getCurrentTask() != JobState.WorkingConstructing)
            return;

        ConstructorJobComponent liveConstructorJob = entityStore.getStore().getComponent(colonistRef, ConstructorJobComponent.getComponentType());
        if (liveConstructorJob == null)
            return;

        if (placedPos != null)
        {
            ClaimBlockUtil.unclaimBlock(world, placedPos);
            if (placedPos.equals(liveConstructorJob.pendingBuildQueue.peekFirst()))
            {
                liveConstructorJob.pendingBuildQueue.pollFirst();
            }
        }

        if (!liveConstructorJob.pendingBuildQueue.isEmpty())
        {
            Vector3i next = liveConstructorJob.pendingBuildQueue.peekFirst();
            JobNavigationUtil.setJobTarget(entityStore.getStore(), colonistRef, next);
            JobNavigationUtil.dispatchNavigation(entityStore.getStore(), colonistRef, next);
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorWorking] [%s] Navigating to next queued block at %s.", npcId, next);
            return;
        }

        BlockSelection prefab = ConstructorUtil.loadPrefab(order);
        @Nullable Vector3i nextBuild = prefab != null ? ConstructorUtil.findNextBuildTarget(order, world, prefab) : null;

        if (nextBuild != null)
        {
            clearTarget(entityStore, colonistRef);
            ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WorkingRetrievingBlocks);
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                          "[ConstructorWorking] [%s] Queue exhausted, more blocks remain -- WorkingRetrievingBlocks.",
                          npcId);
            return;
        }

        @Nullable Vector3i nextClear = prefab != null ? ConstructorUtil.findNextClearingTarget(order, world, prefab) : null;
        if (nextClear != null)
        {
            clearTarget(entityStore, colonistRef);
            UUIDComponent liveUuid = entityStore.getStore().getComponent(colonistRef, UUIDComponent.getComponentType());
            UUID colonistUuid = liveUuid != null ? liveUuid.getUuid() : null;
            if (colonistUuid != null)
            {
                Vector3i claimed = ConstructorUtil.claimNextClearingTarget(order, world, prefab, colonistUuid);
                if (claimed != null)
                {
                    JobRunCounterComponent liveCounter = entityStore.getStore().getComponent(colonistRef, JobRunCounterComponent.getComponentType());
                    if (liveCounter != null)
                        liveCounter.count = 0;

                    JobNavigationUtil.setJobTarget(entityStore.getStore(), colonistRef, claimed);
                    JobNavigationUtil.dispatchNavigation(entityStore.getStore(), colonistRef, claimed);

                    liveJob.workAvailable = true;

                    ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WorkingClearing);
                    DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                                  "[ConstructorWorking] [%s] Wrong block detected after build -- switching to clearing at %s.",
                                  npcId,
                                  claimed);
                    return;
                }
            }

            liveJob.workAvailable = true;
            ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WaitingForWork);
            return;
        }

        clearTarget(entityStore, colonistRef);
        liveJob.workAvailable = true;
        ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WaitingForWork);
        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorWorking] [%s] Construction complete -- WaitingForWork.", npcId);
    }

    private static void clearTarget(@Nonnull EntityStore entityStore, @Nonnull Ref<EntityStore> colonistRef)
    {
        JobTargetComponent liveJt = entityStore.getStore().getComponent(colonistRef, JobTargetComponent.getComponentType());
        if (liveJt != null)
            liveJt.setTargetPosition(null);
    }
}
