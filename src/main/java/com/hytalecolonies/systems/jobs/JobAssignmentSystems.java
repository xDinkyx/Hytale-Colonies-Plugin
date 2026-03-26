package com.hytalecolonies.systems.jobs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.DelayedSystem;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.JobType;
import com.hytalecolonies.components.jobs.UnemployedComponent;
import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.components.jobs.WoodsmanJobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.components.world.HarvestableTreeComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.debug.DebugTiming;
import com.hytalecolonies.utils.BlockStateInfoUtil;

public class JobAssignmentSystems extends DelayedEntitySystem<ChunkStore> {

    // Query for Job Sources (Workstations/Blocks)
    private final Query<ChunkStore> workStationQuery = Archetype.of(WorkStationComponent.getComponentType());
    private final Query<EntityStore> unemployedQuery = Query.and(UnemployedComponent.getComponentType(),
            ColonistComponent.getComponentType());

    // Tracks colonists already assigned this cycle so the same colonist isn't assigned to two
    // workstations. CommandBuffer removals are deferred, so they still appear in unemployedQuery
    // until the next flush.
    private final Set<UUID> colonistsAlreadyAssignedThisCycle = new HashSet<>();

    public JobAssignmentSystems() {
        super(5.0f); // Run once every 5 seconds.
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<ChunkStore> store) {
        // Called once per cadence cycle before any workstation entity is processed.
        colonistsAlreadyAssignedThisCycle.clear();
        super.tick(dt, systemIndex, store);
    }

    @Override
    public void tick(float dt, int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> chunkStore,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        WorkStationComponent workStation = archetypeChunk.getComponent(index, WorkStationComponent.getComponentType());
        assert workStation != null;

        World world = chunkStore.getExternalData().getWorld();
        EntityStore entityStore = world.getEntityStore();

        // Remove ghost workers — colonists recorded in the workstation that no longer
        // exist.
        removeGhostWorkers(workStation, entityStore);

        LogWorkStationInfo(workStation);

        // If no job slots are available, do nothing.
        if (workStation.getAvailableJobSlots() <= 0)
            return;

        BlockModule.BlockStateInfo blockStateInfo = archetypeChunk.getComponent(index,
                BlockModule.BlockStateInfo.getComponentType());
        if (blockStateInfo == null) {
            DebugLog.warning(DebugCategory.JOB_ASSIGNMENT,
                    "[JobAssignment] WorkStation has no BlockStateInfo — skipping.");
            return;
        }

        // Get the world position of the work station block entity
        Vector3i workStationPos = new BlockStateInfoUtil().GetBlockWorldPosition(blockStateInfo, commandBuffer);

        // Iterate through unemployed colonists and assign them to this work station
        // until we run out of job slots or unemployed colonists.

        int unemployedCount = entityStore.getStore().getEntityCountFor(unemployedQuery);
        DebugLog.fine(DebugCategory.JOB_ASSIGNMENT,
                "[JobAssignment] WorkStation %s (%s) | slots: %d | unemployed colonists: %d",
                workStation.getJobType(), workStationPos, workStation.getAvailableJobSlots(), unemployedCount);

        if (unemployedCount == 0)
            return;

        try (var t = DebugTiming.measure("JobAssignment.assignColonists@" + workStationPos, 100)) {
            entityStore.getStore().forEachChunk(unemployedQuery, (_archetypeChunk, _commandBuffer) -> {
                // Stop once slots are full.
                if (workStation.getAvailableJobSlots() <= 0) return;

                // ToDo: Implement more complex job assignment logic that considers distance,
                // stats, preferences, etc.
                int colonistId = 0;
                Ref<EntityStore> colonistRef = _archetypeChunk.getReferenceTo(colonistId);

                ColonistComponent colonist = _archetypeChunk.getComponent(colonistId,
                        ColonistComponent.getComponentType());
                assert colonist != null;
                UUIDComponent colonistEntityUuid = _archetypeChunk.getComponent(colonistId,
                        UUIDComponent.getComponentType());
                assert colonistEntityUuid != null;

                // Skip colonists assigned by another workstation earlier this cycle.
                if (colonistsAlreadyAssignedThisCycle.contains(colonistEntityUuid.getUuid())) return;

                DebugLog.info(DebugCategory.JOB_ASSIGNMENT,
                        "[JobAssignment] Assigning colonist '%s' (%s) to WorkStation %s at %s.",
                        colonist.getColonistName(), colonistEntityUuid.getUuid(), workStation.getJobType(),
                        workStationPos);

                EmployInWorkStation(_commandBuffer, workStation, colonistEntityUuid, colonistRef, workStationPos);
                colonistsAlreadyAssignedThisCycle.add(colonistEntityUuid.getUuid());
            });
        }
    }

    private static void removeGhostWorkers(WorkStationComponent workStation, EntityStore entityStore) {
        List<UUID> ghosts = null;
        List<UUID> zombies = null;
        for (UUID colonistUuid : workStation.getAssignedColonists()) {
            Ref<EntityStore> ref = entityStore.getRefFromUUID(colonistUuid);
            if (ref == null || !ref.isValid()) {
                if (ghosts == null)
                    ghosts = new ArrayList<>();
                ghosts.add(colonistUuid);
            } else if (entityStore.getStore().getComponent(ref, JobComponent.getComponentType()) == null) {
                // Entity exists but lost its JobComponent (e.g. loaded from old save without
                // codec).
                if (zombies == null)
                    zombies = new ArrayList<>();
                zombies.add(colonistUuid);
            }
        }
        if (ghosts != null) {
            for (UUID ghost : ghosts) {
                workStation.removeAssignedColonist(ghost);
                DebugLog.warning(DebugCategory.JOB_ASSIGNMENT,
                        "[JobAssignment] Removed ghost worker %s from workstation %s.",
                        ghost, workStation.getJobType());
            }
        }
        if (zombies != null) {
            for (UUID zombie : zombies) {
                workStation.removeAssignedColonist(zombie);
                Ref<EntityStore> ref = entityStore.getRefFromUUID(zombie);
                if (ref != null && ref.isValid()) {
                    WoodsmanJobSystem.unmarkClaimedTree(ref, entityStore.getStore());
                    entityStore.getStore().tryRemoveComponent(ref, JobTargetComponent.getComponentType());
                    entityStore.getStore().tryRemoveComponent(ref, WoodsmanJobComponent.getComponentType());
                    if (entityStore.getStore().getComponent(ref, UnemployedComponent.getComponentType()) == null) {
                        entityStore.getStore().addComponent(ref, UnemployedComponent.getComponentType(),
                                new UnemployedComponent());
                    }
                }
                DebugLog.warning(DebugCategory.JOB_ASSIGNMENT,
                        "[JobAssignment] Restored zombie worker %s from workstation %s as unemployed.",
                        zombie, workStation.getJobType());
            }
        }
    }

    private static void EmployInWorkStation(CommandBuffer<EntityStore> _commandBuffer, WorkStationComponent workStation,
            UUIDComponent colonistEntityUuid, Ref<EntityStore> colonistRef, Vector3i workStationPos) {
        // Assign the colonist to the work station.
        workStation.assignColonist(colonistEntityUuid.getUuid());

        // Add job component to colonist.
        _commandBuffer.removeComponent(colonistRef, UnemployedComponent.getComponentType()); // Remove unemployed
                                                                                             // component since colonist
                                                                                             // is now employed.
        _commandBuffer.addComponent(colonistRef, JobComponent.getComponentType(), new JobComponent(workStationPos));

        // Add job-type-specific component.
        switch (workStation.getJobType()) {
            case Woodsman -> _commandBuffer.addComponent(colonistRef, WoodsmanJobComponent.getComponentType(),
                    new WoodsmanJobComponent());
            case Miner -> _commandBuffer.addComponent(colonistRef, MinerJobComponent.getComponentType(),
                    new MinerJobComponent());
            case Farmer, Builder -> { /* TODO: implement job-specific component */ }
        }

        DebugLog.info(DebugCategory.JOB_ASSIGNMENT, "Assigned Colonist %s to job at %s.", colonistEntityUuid.getUuid(),
                workStation.getJobType());
    }

    private static void LogWorkStationInfo(WorkStationComponent workStation) {
        StringBuilder workStationInfo = new StringBuilder(
                String.format("Work Station Job Type: %s | Available Job Slots: %d | Assigned Colonists: %d",
                        workStation.getJobType(), workStation.getAvailableJobSlots(),
                        workStation.getAssignedColonists().size()));
        int i = 0;
        for (UUID colonistUuid : workStation.getAssignedColonists()) {
            workStationInfo.append(String.format("\n- Colonist %d UUID %s.", i, colonistUuid));
            i++;
        }
        DebugLog.fine(DebugCategory.JOB_ASSIGNMENT, workStationInfo.toString());
    }

    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return workStationQuery;
    }

    /**
     * System for job related actions when work stations are added or removed.
     */
    public static class WorkStationEntitySystem extends RefSystem<ChunkStore> {
        @Override
        public void onEntityAdded(@Nonnull Ref<ChunkStore> ref,
                @Nonnull AddReason reason,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        }

        @Override
        public void onEntityRemove(@Nonnull Ref<ChunkStore> ref,
                @Nonnull RemoveReason reason,
                @Nonnull Store<ChunkStore> store,
                @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
            if (reason == RemoveReason.UNLOAD)
                return; // Ignore unloads.

            WorkStationComponent workStation = commandBuffer.getComponent(ref, WorkStationComponent.getComponentType());
            assert workStation != null;

            fireColonists(workStation, commandBuffer);
            workStation.clearAssignedColonists();
        }

        private void fireColonists(WorkStationComponent workStation, CommandBuffer<ChunkStore> commandBuffer) {
            World world = commandBuffer.getExternalData().getWorld();
            EntityStore entityStore = world.getEntityStore();

            // Snapshot colonist UUIDs now — the workStation will be cleared/removed
            // before world.execute() runs, so we cannot iterate it lazily.
            List<UUID> colonists = new ArrayList<>(workStation.getAssignedColonists());

            // Defer all EntityStore mutations: this callback fires inside a ChunkStore
            // entity-removal path while the EntityStore tick may still be active.
            // world.execute() queues the work safely between ticks.
            world.execute(() -> {
                for (UUID colonistUuid : colonists) {
                    Ref<EntityStore> colonistRef = entityStore.getRefFromUUID(colonistUuid);
                    if (colonistRef == null) continue; // Colonist may have been removed already.
                    // Clean up job-type-specific state before removing the job.
                    WoodsmanJobSystem.unmarkClaimedTree(colonistRef, entityStore.getStore());
                    entityStore.getStore().tryRemoveComponent(colonistRef, JobTargetComponent.getComponentType());
                    entityStore.getStore().tryRemoveComponent(colonistRef, WoodsmanJobComponent.getComponentType());
                    entityStore.getStore().tryRemoveComponent(colonistRef, JobComponent.getComponentType());
                    entityStore.getStore().addComponent(colonistRef, UnemployedComponent.getComponentType(),
                            new UnemployedComponent()); // Mark colonist as unemployed again.
                    DebugLog.info(DebugCategory.JOB_ASSIGNMENT, "Unassigned colonist with UUID %s from work station.",
                            colonistUuid);
                }
            });
        }

        @Override
        public @Nullable Query<ChunkStore> getQuery() {
            return Query.and(BlockModule.BlockStateInfo.getComponentType(), WorkStationComponent.getComponentType());
        }
    }

    /**
     * System for job related actions when colonists are added or removed.
     */
    public static class ColonistEntitySystem extends RefSystem<EntityStore> {
        @Override
        public void onEntityAdded(@Nonnull Ref<EntityStore> ref,
                @Nonnull AddReason reason,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            if (reason != AddReason.LOAD)
                return;

            // On server load, transient fields (targetTreePosition etc.) are gone.
            // Reset any in-progress travel state back to Idle so the movement system
            // cleanly picks up the colonist and finds a new tree.
            JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
            if (job == null)
                return;
            JobState state = job.getCurrentTask();
            if (state == JobState.TravelingToJob || state == JobState.TravelingHome || state == JobState.Working) {
                DebugLog.info(DebugCategory.JOB_ASSIGNMENT,
                        "[JobAssignment] Resetting colonist job state from %s to Idle on load.", state);
                job.setCurrentTask(JobState.Idle);
                // Remove the job target so ColonistMovementSystem does not process stale
                // travel.
                // StaleMarkCleanupSystem will clear any orphaned tree marks.
                commandBuffer.removeComponent(ref, JobTargetComponent.getComponentType());
            }
        }

        @Override
        public void onEntityRemove(@Nonnull Ref<EntityStore> ref,
                @Nonnull RemoveReason reason,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            if (reason == RemoveReason.UNLOAD)
                return; // Ignore unloads.

            ColonistComponent colonist = commandBuffer.getComponent(ref, ColonistComponent.getComponentType());
            assert colonist != null;
            JobComponent jobComponent = commandBuffer.getComponent(ref, JobComponent.getComponentType());
            assert jobComponent != null;
            UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
            assert uuidComponent != null;

            // Release any claimed tree before removing.
            WoodsmanJobSystem.unmarkClaimedTree(ref, store);
            commandBuffer.removeComponent(ref, JobTargetComponent.getComponentType());

            // Get work station from position.
            Vector3i workStationPos = jobComponent.getWorkStationBlockPosition();

            World world = commandBuffer.getExternalData().getWorld();
            Ref<ChunkStore> blockEntity = BlockModule.getBlockEntity(world, workStationPos.x, workStationPos.y,
                    workStationPos.z);
            var workStationComponent = blockEntity.getStore().getComponent(blockEntity,
                    WorkStationComponent.getComponentType());
            assert workStationComponent != null;

            // Free up colonist job slot at work station.
            workStationComponent.removeAssignedColonist(uuidComponent.getUuid());
            DebugLog.info(DebugCategory.JOB_ASSIGNMENT,
                    "Colonist %s with UUID %s has been removed. Freed up work station job slot at position %s.",
                    colonist.getColonistName(), uuidComponent.getUuid(), workStationPos);
        }

        @Override
        public @Nullable Query<EntityStore> getQuery() {
            return Query.and(ColonistComponent.getComponentType(), JobComponent.getComponentType());
        }
    }

    /**
     * Only here to log when colonists get assigned a job but are still accidentally
     * marked as unemployed.
     */
    public static class JobAssignedSystem extends RefChangeSystem<EntityStore, JobComponent> {

        @Override
        public ComponentType<EntityStore, JobComponent> componentType() {
            return JobComponent.getComponentType();
        }

        @Override
        public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
                @Nonnull JobComponent component,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
            UnemployedComponent unemployedComponent = store.getComponent(ref, UnemployedComponent.getComponentType());
            if (unemployedComponent != null) {
                DebugLog.severe(DebugCategory.JOB_ASSIGNMENT,
                        "Colonist with UUID %s has a job component but is still marked as unemployed. This should not happen.",
                        uuidComponent.getUuid());
            }
        }

        @Override
        public void onComponentSet(@Nonnull Ref<EntityStore> ref, @Nullable JobComponent oldComponent,
                @Nonnull JobComponent newComponent, @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        }

        @Override
        public void onComponentRemoved(@Nonnull Ref<EntityStore> var1, @Nonnull JobComponent var2,
                @Nonnull Store<EntityStore> var3, @Nonnull CommandBuffer<EntityStore> var4) {
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(ColonistComponent.getComponentType(), JobComponent.getComponentType());
        }
    }

    /**
     * Periodically scans every {@link HarvestableTreeComponent} and clears the
     * {@code markedForHarvest} flag on any tree that has no active colonist
     * claiming it. This covers orphaned marks left behind by crashes, hard
     * removal of colonists, or server restarts.
     */
    public static class StaleMarkCleanupSystem extends DelayedSystem<ChunkStore> {

        public StaleMarkCleanupSystem() {
            super(30.0f); // Audit every 30 seconds.
        }

        @Override
        public void delayedTick(float dt, int systemIndex, @Nonnull Store<ChunkStore> store) {
            World world = store.getExternalData().getWorld();
            EntityStore entityStore = world.getEntityStore();

            // Collect positions of trees that are actively claimed by a colonist.
            Set<Vector3i> activelyClaimed = new HashSet<>();
            Query<EntityStore> woodsmanJobTargetQuery = Query.and(
                    WoodsmanJobComponent.getComponentType(),
                    JobTargetComponent.getComponentType());
            entityStore.getStore().forEachChunk(woodsmanJobTargetQuery, (chunk, _cb) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    JobTargetComponent jobTarget = chunk.getComponent(i, JobTargetComponent.getComponentType());
                    if (jobTarget != null && jobTarget.targetPosition != null) {
                        activelyClaimed.add(jobTarget.targetPosition);
                    }
                }
            });

            // Clear marks on any tree not in the active-claim set.
            int[] cleared = { 0 };
            Query<ChunkStore> treeQuery = Query.and(HarvestableTreeComponent.getComponentType());
            store.forEachChunk(treeQuery, (chunk, _cb) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    HarvestableTreeComponent tree = chunk.getComponent(i, HarvestableTreeComponent.getComponentType());
                    if (tree != null && tree.isMarkedForHarvest()
                            && !activelyClaimed.contains(tree.getBasePosition())) {
                        tree.setMarkedForHarvest(false);
                        cleared[0]++;
                    }
                }
            });

            if (cleared[0] > 0) {
                DebugLog.info(DebugCategory.JOB_ASSIGNMENT, "[StaleMarks] Cleared %d orphaned tree mark(s).",
                        cleared[0]);
            }
        }
    }

    /**
     * Only here to log when colonists get marked as unemployed but still have a job
     * assigned.
     */
    public static class UnemployedAssignedSystem extends RefChangeSystem<EntityStore, UnemployedComponent> {

        @Override
        public ComponentType<EntityStore, UnemployedComponent> componentType() {
            return UnemployedComponent.getComponentType();
        }

        @Override
        public void onComponentAdded(@Nonnull Ref<EntityStore> ref,
                @Nonnull UnemployedComponent component,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
            JobComponent jobComponent = store.getComponent(ref, JobComponent.getComponentType());
            if (jobComponent != null) {
                DebugLog.severe(DebugCategory.JOB_ASSIGNMENT,
                        "Colonist with UUID %s is marked as unemployed but still has a job component. This should not happen.",
                        uuidComponent.getUuid());
            }
        }

        @Override
        public void onComponentSet(@Nonnull Ref<EntityStore> ref, @Nullable UnemployedComponent oldComponent,
                @Nonnull UnemployedComponent newComponent, @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        }

        @Override
        public void onComponentRemoved(@Nonnull Ref<EntityStore> var1, @Nonnull UnemployedComponent var2,
                @Nonnull Store<EntityStore> var3, @Nonnull CommandBuffer<EntityStore> var4) {
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(ColonistComponent.getComponentType(), UnemployedComponent.getComponentType());
        }
    }
}
