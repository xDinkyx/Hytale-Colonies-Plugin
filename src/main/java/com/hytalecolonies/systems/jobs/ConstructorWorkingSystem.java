package com.hytalecolonies.systems.jobs;

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
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.components.jobs.ConstructionOrderComponent;
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
public class ConstructorWorkingSystem extends EntityTickingSystem<EntityStore> {

    private static final Query<EntityStore> QUERY = Query.and(
            ConstructorJobComponent.getComponentType(),
            JobRunCounterComponent.getComponentType(),
            JobComponent.getComponentType()
    );

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        JobComponent job = chunk.getComponent(index, JobComponent.getComponentType());
        if (job == null) return;
        JobRunCounterComponent counter = chunk.getComponent(index, JobRunCounterComponent.getComponentType());
        if (counter == null) return;

        JobState state = job.getCurrentTask();

        // --- Clearing phase: a block was cleared ---
        if (state == JobState.WorkingClearing && job.blockBrokenNotification) {
            job.blockBrokenNotification = false;
            counter.count++;
            onClearingBlockBroken(chunk.getReferenceTo(index), store, job, counter);
            return;
        }

        // --- Constructing phase: items retrieved from workstation (reset counter) ---
        if (state == JobState.WorkingConstructing && job.itemsRetrievedNotification) {
            job.itemsRetrievedNotification = false;
            counter.count = 0;
            onItemsRetrieved(chunk.getReferenceTo(index), store, job);
            return;
        }

        // --- Constructing phase: a block was placed ---
        if (state == JobState.WorkingConstructing && job.blockPlacedNotification) {
            job.blockPlacedNotification = false;
            onBlockPlaced(chunk.getReferenceTo(index), store, job, counter);
        }
    }

    // -------------------------------------------------------------------------
    // Clearing phase
    // -------------------------------------------------------------------------

    private static void onClearingBlockBroken(@Nonnull Ref<EntityStore> colonistRef,
                                               @Nonnull Store<EntityStore> store,
                                               @Nonnull JobComponent job,
                                               @Nonnull JobRunCounterComponent counter) {
        Vector3i wsPos = job.getWorkStationBlockPosition();
        if (wsPos == null) {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }

        World world = store.getExternalData().getWorld();
        WorkStationComponent wsBase = WorkStationUtil.getWorkStationAt(world, wsPos);
        if (wsBase == null) {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }
        if (WorkStationUtil.getConstructorWorkStationAt(world, wsPos) == null) {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }
        ConstructionOrderComponent order = WorkStationUtil.getConstructionOrderForWorkstation(world, wsPos);

        String npcId = DebugLog.npcId(colonistRef, store);
        boolean quotaReached = counter.count >= wsBase.blocksPerRun;

        BlockSelection prefab = ConstructorUtil.loadPrefab(order);
        @Nullable Vector3i nextClear = (!quotaReached && prefab != null)
                ? ConstructorUtil.findNextClearingTarget(order, world, prefab)
                : null;
        final boolean clearingDone = !quotaReached && nextClear == null;

        JobTargetComponent jt = store.getComponent(colonistRef, JobTargetComponent.getComponentType());
        @Nullable final Vector3i currentPos = jt != null ? jt.targetPosition : null;
        UUIDComponent uuid = store.getComponent(colonistRef, UUIDComponent.getComponentType());
        final UUID colonistUuid = uuid != null ? uuid.getUuid() : null;
        EntityStore entityStore = world.getEntityStore();

        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                "[ConstructorWorking] [%s] Block cleared (%d/%d). %s",
                npcId, counter.count, wsBase.blocksPerRun,
                clearingDone ? "Phase complete -- start constructing." :
                quotaReached  ? "Quota reached -- collecting drops." :
                                "Next clearing target: " + nextClear + ".");

        world.execute(() -> {
            JobComponent liveJob = entityStore.getStore().getComponent(colonistRef, JobComponent.getComponentType());
            if (liveJob == null || liveJob.getCurrentTask() != JobState.WorkingClearing) return;

            if (currentPos != null) ClaimBlockUtil.unclaimBlock(world, currentPos);

            if (clearingDone) {
                clearTarget(entityStore, colonistRef);
                ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WorkingRetrievingBlocks);
                return;
            }

            if (quotaReached) {
                clearTarget(entityStore, colonistRef);
                if (currentPos != null) ColonistLeashUtil.setLeashToBlockCenter(colonistRef, entityStore.getStore(), currentPos);
                liveJob.collectingDropsSince = System.currentTimeMillis();
                ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.CollectingDrops);
                return;
            }

            // More clearing to do in this run.
            if (colonistUuid == null || !ClaimBlockUtil.claimBlock(world, nextClear, colonistUuid, "Clear")) {
                DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB,
                        "[ConstructorWorking] [%s] Claim of %s failed (race) -- will retry on next tick.",
                        npcId, nextClear);
                return;
            }
            JobNavigationUtil.setJobTarget(entityStore.getStore(), colonistRef, nextClear);
            JobNavigationUtil.dispatchNavigation(entityStore.getStore(), colonistRef, nextClear);
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                    "[ConstructorWorking] [%s] Clearing next block at %s.", npcId, nextClear);
        });
    }

    // -------------------------------------------------------------------------
    // Construction phase: items retrieved from workstation
    // -------------------------------------------------------------------------

    private static void onItemsRetrieved(@Nonnull Ref<EntityStore> colonistRef,
                                          @Nonnull Store<EntityStore> store,
                                          @Nonnull JobComponent job) {
        Vector3i wsPos = job.getWorkStationBlockPosition();
        if (wsPos == null) {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }

        World world = store.getExternalData().getWorld();
        WorkStationComponent wsBase = WorkStationUtil.getWorkStationAt(world, wsPos);
        if (wsBase == null) {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }
        if (WorkStationUtil.getConstructorWorkStationAt(world, wsPos) == null) {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }
        ConstructionOrderComponent order = WorkStationUtil.getConstructionOrderForWorkstation(world, wsPos);

        String npcId = DebugLog.npcId(colonistRef, store);
        BlockSelection prefab = ConstructorUtil.loadPrefab(order);
        @Nullable Vector3i nextBuild = prefab != null ? ConstructorUtil.findNextBuildTarget(order, world, prefab) : null;
        UUIDComponent uuid = store.getComponent(colonistRef, UUIDComponent.getComponentType());
        final UUID colonistUuid = uuid != null ? uuid.getUuid() : null;
        EntityStore entityStore = world.getEntityStore();

        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                "[ConstructorWorking] [%s] Items retrieved. %s",
                npcId, nextBuild == null ? "No build targets -- WaitingForWork." : "First build block: " + nextBuild + ".");

        world.execute(() -> {
            JobComponent liveJob = entityStore.getStore().getComponent(colonistRef, JobComponent.getComponentType());
            if (liveJob == null || liveJob.getCurrentTask() != JobState.WorkingConstructing) return;

            if (nextBuild == null) {
                liveJob.workAvailable = true;
                ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WaitingForWork);
                return;
            }

            if (colonistUuid == null || !ClaimBlockUtil.claimBlock(world, nextBuild, colonistUuid, "Build")) {
                DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB,
                        "[ConstructorWorking] [%s] Claim of %s failed (race).", npcId, nextBuild);
                return;
            }
            JobNavigationUtil.setJobTarget(entityStore.getStore(), colonistRef, nextBuild);
            JobNavigationUtil.dispatchNavigation(entityStore.getStore(), colonistRef, nextBuild);
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                    "[ConstructorWorking] [%s] Building first block at %s.", npcId, nextBuild);
        });
    }

    // -------------------------------------------------------------------------
    // Construction phase: block placed
    // -------------------------------------------------------------------------

    private static void onBlockPlaced(@Nonnull Ref<EntityStore> colonistRef,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull JobComponent job,
                                       @Nonnull JobRunCounterComponent counter) {
        Vector3i wsPos = job.getWorkStationBlockPosition();
        if (wsPos == null) {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }

        World world = store.getExternalData().getWorld();
        WorkStationComponent wsBase = WorkStationUtil.getWorkStationAt(world, wsPos);
        if (wsBase == null) {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }
        if (WorkStationUtil.getConstructorWorkStationAt(world, wsPos) == null) {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }
        ConstructionOrderComponent order = WorkStationUtil.getConstructionOrderForWorkstation(world, wsPos);

        String npcId = DebugLog.npcId(colonistRef, store);
        BlockSelection prefab = ConstructorUtil.loadPrefab(order);
        @Nullable Vector3i nextBuild = prefab != null ? ConstructorUtil.findNextBuildTarget(order, world, prefab) : null;

        // Construction complete.
        if (nextBuild == null) {
            JobTargetComponent jt = store.getComponent(colonistRef, JobTargetComponent.getComponentType());
            @Nullable final Vector3i currentPos = jt != null ? jt.targetPosition : null;
            EntityStore entityStore = world.getEntityStore();
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                    "[ConstructorWorking] [%s] Construction complete -- WaitingForWork.", npcId);
            world.execute(() -> {
                JobComponent liveJob = entityStore.getStore().getComponent(colonistRef, JobComponent.getComponentType());
                if (liveJob == null || liveJob.getCurrentTask() != JobState.WorkingConstructing) return;
                if (currentPos != null) ClaimBlockUtil.unclaimBlock(world, currentPos);
                clearTarget(entityStore, colonistRef);
                counter.count = 0;
                liveJob.workAvailable = true;
                ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WaitingForWork);
            });
            return;
        }

        counter.count++;
        boolean quotaReached = counter.count >= wsBase.blocksPerRun;
        JobTargetComponent jt = store.getComponent(colonistRef, JobTargetComponent.getComponentType());
        @Nullable final Vector3i currentPos = jt != null ? jt.targetPosition : null;
        UUIDComponent uuid = store.getComponent(colonistRef, UUIDComponent.getComponentType());
        final UUID colonistUuid = uuid != null ? uuid.getUuid() : null;
        EntityStore entityStore = world.getEntityStore();

        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                "[ConstructorWorking] [%s] Block placed (%d/%d). %s",
                npcId, counter.count, wsBase.blocksPerRun,
                quotaReached ? "Quota -- retrieving materials." : "Next build block: " + nextBuild + ".");

        world.execute(() -> {
            JobComponent liveJob = entityStore.getStore().getComponent(colonistRef, JobComponent.getComponentType());
            if (liveJob == null || liveJob.getCurrentTask() != JobState.WorkingConstructing) return;
            if (currentPos != null) ClaimBlockUtil.unclaimBlock(world, currentPos);

            if (quotaReached) {
                clearTarget(entityStore, colonistRef);
                ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WorkingRetrievingBlocks);
                return;
            }

            if (colonistUuid == null || !ClaimBlockUtil.claimBlock(world, nextBuild, colonistUuid, "Build")) {
                DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB,
                        "[ConstructorWorking] [%s] Claim of %s failed (race).", npcId, nextBuild);
                return;
            }
            JobNavigationUtil.setJobTarget(entityStore.getStore(), colonistRef, nextBuild);
            JobNavigationUtil.dispatchNavigation(entityStore.getStore(), colonistRef, nextBuild);
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void clearTarget(@Nonnull EntityStore entityStore, @Nonnull Ref<EntityStore> colonistRef) {
        JobTargetComponent liveJt = entityStore.getStore().getComponent(colonistRef, JobTargetComponent.getComponentType());
        if (liveJt != null) liveJt.setTargetPosition(null);
    }
}
