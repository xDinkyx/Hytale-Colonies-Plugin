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
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.debug.DebugTiming;
import com.hytalecolonies.utils.BlockStateInfoUtil;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hytalecolonies.utils.WorkStationUtil;


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

        // Guard against concurrent chunk unloads invalidating the chunk ref.
        if (!blockStateInfo.getChunkRef().isValid()) {
            DebugLog.fine(DebugCategory.JOB_ASSIGNMENT,
                    "[JobAssignment] WorkStation chunk ref is invalid (chunk unloading) — skipping.");
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
                    fireColonist(ref, entityStore.getStore());
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

    /**
     * Removes a colonist's job assignment and marks them as unemployed.
     * Safe to call from ChunkStore tick contexts (e.g. workstation removal,
     * ghost-worker cleanup) where the EntityStore is not currently processing.
     * For EntityStore tick contexts, use the CommandBuffer overload instead.
     */
    static void fireColonist(Ref<EntityStore> ref, Store<EntityStore> store) {
        ClaimBlockUtil.unclaimByColonist(ref, store);
        store.tryRemoveComponent(ref, JobTargetComponent.getComponentType());
        for (ComponentType<EntityStore, ?> type : JobRegistry.getJobComponentTypes()) {
            store.tryRemoveComponent(ref, type);
        }
        store.tryRemoveComponent(ref, JobComponent.getComponentType());
        if (store.getComponent(ref, UnemployedComponent.getComponentType()) == null) {
            store.addComponent(ref, UnemployedComponent.getComponentType(), new UnemployedComponent());
        }
    }

    /**
     * CommandBuffer-safe overload for use within system ticks.
     * All EntityStore mutations go through the CommandBuffer.
     * The block claim is released via {@code world.execute()} since ChunkStore
     * cannot be mutated directly inside an EntityStore tick.
     */
    static void fireColonist(Ref<EntityStore> ref, Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer) {
        // Schedule the ChunkStore unclaim on the world thread.
        World world = store.getExternalData().getWorld();
        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jobTarget != null && jobTarget.targetPosition != null) {
            final Vector3i claimedPos = jobTarget.targetPosition;
            world.execute(() -> ClaimBlockUtil.unclaimBlock(world, claimedPos));
        }
        commandBuffer.tryRemoveComponent(ref, JobTargetComponent.getComponentType());
        for (ComponentType<EntityStore, ?> type : JobRegistry.getJobComponentTypes()) {
            commandBuffer.tryRemoveComponent(ref, type);
        }
        commandBuffer.tryRemoveComponent(ref, JobComponent.getComponentType());
        commandBuffer.addComponent(ref, UnemployedComponent.getComponentType(), new UnemployedComponent());
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

            // Snapshot UUIDs now — workStation is cleared before world.execute() runs.
            // world.execute() defers EntityStore mutations: this can be called while the
            // EntityStore is ticking (e.g. a block broken during interaction processing
            // fires onEntityRemove on the ChunkStore workstation entity).
            List<UUID> colonists = new ArrayList<>(workStation.getAssignedColonists());
            world.execute(() -> {
                for (UUID colonistUuid : colonists) {
                    Ref<EntityStore> colonistRef = entityStore.getRefFromUUID(colonistUuid);
                    if (colonistRef == null) continue;
                    fireColonist(colonistRef, entityStore.getStore());
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
            // Reset any in-progress travel state back to Idling so the movement system
            // cleanly picks up the colonist and finds a new tree.
            JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
            if (job == null)
                return;
            JobState state = job.getCurrentTask();
            if (state == JobState.TravelingToJob || state == JobState.TravelingHome || state == JobState.Working) {
                DebugLog.info(DebugCategory.JOB_ASSIGNMENT,
                        "[JobAssignment] Resetting colonist job state from %s to Idling on load.", state);
                job.setCurrentTask(JobState.Idling);
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

            // Schedule release of any claimed block on the world thread
            // (ChunkStore mutation cannot happen directly in RefSystem<EntityStore> callbacks).
            World world = commandBuffer.getExternalData().getWorld();
            JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
            if (jobTarget != null && jobTarget.targetPosition != null) {
                final Vector3i claimedPos = jobTarget.targetPosition;
                world.execute(() -> ClaimBlockUtil.unclaimBlock(world, claimedPos));
            }
            commandBuffer.removeComponent(ref, JobTargetComponent.getComponentType());

            // Get work station from position.
            Vector3i workStationPos = jobComponent.getWorkStationBlockPosition();

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
     * Switches the colonist's NPC role when a job is assigned or removed.
     *
     * <ul>
     *   <li>On assignment: looks up the workstation's {@link JobType} and switches
     *       to the matching role (e.g. {@code Colonist_Miner}, {@code Colonist_Woodsman}).</li>
     *   <li>On removal: reverts to {@code Colonist_Dummy} so the colonist wanders
     *       while waiting for a new job.</li>
     * </ul>
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

            // Switch to the job-specific NPC role.
            World world = store.getExternalData().getWorld();
            WorkStationComponent workStation = WorkStationUtil.resolve(store, ref);
            if (workStation == null) {
                DebugLog.warning(DebugCategory.JOB_ASSIGNMENT,
                        "[RoleSwitch] No workstation found for colonist %s — keeping generic role.",
                        uuidComponent != null ? uuidComponent.getUuid() : "?");
                return;
            }
            String targetRole = ColonistRoleMap.roleFor(workStation.getJobType());
            ColonistRoleMap.switchRole(ref, store, targetRole);
        }

        @Override
        public void onComponentSet(@Nonnull Ref<EntityStore> ref, @Nullable JobComponent oldComponent,
                @Nonnull JobComponent newComponent, @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        }

        @Override
        public void onComponentRemoved(@Nonnull Ref<EntityStore> ref, @Nonnull JobComponent component,
                @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            // Revert to generic wandering role when the colonist loses their job.
            ColonistRoleMap.switchRole(ref, store, ColonistRoleMap.ROLE_GENERIC);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(ColonistComponent.getComponentType(), JobComponent.getComponentType());
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
