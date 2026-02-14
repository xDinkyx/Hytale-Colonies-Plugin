package com.colonies.coloniesplugin.systems.jobs;

import com.colonies.coloniesplugin.ColoniesPlugin;
import com.colonies.coloniesplugin.components.jobs.JobComponent;
import com.colonies.coloniesplugin.components.jobs.UnemployedComponent;
import com.colonies.coloniesplugin.components.jobs.WorkStationComponent;
import com.colonies.coloniesplugin.components.npc.ColonistComponent;
import com.colonies.coloniesplugin.utils.BlockStateInfoUtil;
import com.hypixel.hytale.component.*;
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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.UUID;


// ToDo: Make more efficient by adding unemployed component and only querying those entities.
public class JobAssignmentSystems extends DelayedEntitySystem<ChunkStore> {

    // Query for Job Sources (Workstations/Blocks)
    private final Query<ChunkStore> workStationQuery = Archetype.of(WorkStationComponent.getComponentType());
    private final Query<EntityStore> unemployedQuery = Archetype.of(UnemployedComponent.getComponentType()); // ToDo: Replace later with unemployed component query.

    public JobAssignmentSystems() {
        super(1.0f); // Run once every 1 second.
    }

    @Override
    public void tick(float dt, int index,
                     @NonNull ArchetypeChunk<ChunkStore> archetypeChunk,
                     @NonNull Store<ChunkStore> chunkStore,
                     @NonNull CommandBuffer<ChunkStore> commandBuffer) {
        WorkStationComponent workStation = archetypeChunk.getComponent(index, WorkStationComponent.getComponentType());
        assert workStation != null;

        // ToDo: Perhaps add check to see that all assigned colonists still exist? If not remove from work station.

        // If no job slots are available, do nothing.
        if (workStation.getAvailableJobSlots() <= 0) return;

        BlockModule.BlockStateInfo blockStateInfo = archetypeChunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
        if (blockStateInfo == null) return;

        // Get the world position of the work station block entity
        Vector3i workStationPos = new BlockStateInfoUtil().GetBlockWorldPosition(blockStateInfo, commandBuffer);

        ColoniesPlugin.LOGGER.atInfo().log(String.format("Work station at position %s has %d available job slots of type %s. Attempting to assign unemployed colonists...", workStationPos, workStation.getAvailableJobSlots(), workStation.getJobType().toString()));

        // Iterate through unemployed colonists and assign them to this work station until we run out of job slots or unemployed colonists.
        World world = chunkStore.getExternalData().getWorld();
        EntityStore entityStore = world.getEntityStore();
        entityStore.getStore().forEachChunk(unemployedQuery, (_archetypeChunk, _commandBuffer) ->
        {
            // ToDo: Remove loop. Query for first.
            for (int colonistId = 0; colonistId < _archetypeChunk.size(); colonistId++) {
                ColonistComponent colonist = _archetypeChunk.getComponent(colonistId, ColonistComponent.getComponentType());
                assert colonist != null;
                UUIDComponent colonistEntityUuid = _archetypeChunk.getComponent(colonistId, UUIDComponent.getComponentType());
                assert colonistEntityUuid != null;

//                // We queried for unemployed components, but ensure there is no issue with them having a job component assigned.
//                JobComponent colonistJob = _archetypeChunk.getComponent(colonistId, JobComponent.getComponentType());
//                if (colonistJob != null) {
//                    ColoniesPlugin.LOGGER.atWarning().log(String.format("Unemployed colonist #%d : %s is not allowed to have a job component. Skipping assignment. UUID: %s", colonistId, colonist.getColonistName(), colonistEntityUuid.getUuid()));
//                    continue; // Skip employed colonists.
//                }

                ColoniesPlugin.LOGGER.atInfo().log(String.format("Colonist #%d : %s is marked as unemployed | Colonist info: %s", colonistId, colonist.getColonistName(), colonist));

                // Assign the colonist to the work station.
                workStation.assignColonist(colonistEntityUuid.getUuid());

                // Add job component to colonist.
                Ref<EntityStore> colonistRef = _archetypeChunk.getReferenceTo(colonistId);
                // ToDo: Put in a function to make sure we always do proper logic.
                _commandBuffer.removeComponent(colonistRef, UnemployedComponent.getComponentType()); // Remove unemployed component since colonist is now employed.
                _commandBuffer.addComponent(colonistRef, JobComponent.getComponentType(), new JobComponent(workStationPos));

                ColoniesPlugin.LOGGER.atInfo().log(String.format("Assigned Colonist #%d : %s to job at %s.", colonistId, colonistEntityUuid.getUuid(), workStation.getJobType()));

                // Move on to the next work station after assigning one colonist.
                // We only assign one colonist per tick to keep it gradual.
                // ToDo: Implement more complex job assignment logic that considers distance and colonist preferences, etc.
                break;
            }
        });
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
        public void onEntityAdded(@NonNull Ref<ChunkStore> ref,
                                  @NonNull AddReason reason,
                                  @NonNull Store<ChunkStore> store,
                                  @NonNull CommandBuffer<ChunkStore> commandBuffer) {

        }

        @Override
        public void onEntityRemove(@NonNull Ref<ChunkStore> ref,
                                   @NonNull RemoveReason reason,
                                   @NonNull Store<ChunkStore> store,
                                   @NonNull CommandBuffer<ChunkStore> commandBuffer) {
            if (reason == RemoveReason.UNLOAD) return; // Ignore unloads.

            WorkStationComponent workStation = commandBuffer.getComponent(ref, WorkStationComponent.getComponentType());
            assert workStation != null;
            workStation.clearAssignedColonists();

            fireColonists(workStation, commandBuffer);
        }

        private void fireColonists(WorkStationComponent workStation, CommandBuffer<ChunkStore> commandBuffer) {
            World world = commandBuffer.getExternalData().getWorld();
            EntityStore entityStore = world.getEntityStore();

            // Make colonists assigned to this work station unemployed.
            for (UUID colonistUuid : workStation.getAssignedColonists()) {
                var colonistRef = entityStore.getRefFromUUID(colonistUuid);
                entityStore.getStore().tryRemoveComponent(colonistRef, JobComponent.getComponentType());
                entityStore.getStore().addComponent(colonistRef, UnemployedComponent.getComponentType(), new UnemployedComponent()); // Mark colonist as unemployed again.
                ColoniesPlugin.LOGGER.atInfo().log(String.format("Unassigned colonist with UUID %s from work station.", colonistUuid));
            }
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
        public void onEntityAdded(@NonNull Ref<EntityStore> ref,
                                  @NonNull AddReason reason,
                                  @NonNull Store<EntityStore> store,
                                  @NonNull CommandBuffer<EntityStore> commandBuffer) {

        }

        @Override
        public void onEntityRemove(@NonNull Ref<EntityStore> ref,
                                   @NonNull RemoveReason reason,
                                   @NonNull Store<EntityStore> store,
                                   @NonNull CommandBuffer<EntityStore> commandBuffer) {
            if (reason == RemoveReason.UNLOAD) return; // Ignore unloads.

            ColonistComponent colonist = commandBuffer.getComponent(ref, ColonistComponent.getComponentType());
            assert colonist != null;
            JobComponent jobComponent = commandBuffer.getComponent(ref, JobComponent.getComponentType());
            assert jobComponent != null;
            UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
            assert  uuidComponent != null;

            // Get work station from position.
            Vector3i workStationPos = jobComponent.getWorkStationBlockPosition();

            World world = commandBuffer.getExternalData().getWorld();
            Ref<ChunkStore> blockEntity = BlockModule.getBlockEntity(world, workStationPos.x, workStationPos.y, workStationPos.z);
            var workStationComponent = blockEntity.getStore().getComponent(blockEntity, WorkStationComponent.getComponentType());
            assert workStationComponent != null;

            // Free up colonist job slot at work station.
            workStationComponent.removeAssignedColonist(uuidComponent.getUuid());
            ColoniesPlugin.LOGGER.atInfo().log(String.format("Colonist %s with UUID %s has been removed. Freed up work station job slot at position %s.", colonist.getColonistName(), uuidComponent.getUuid(), workStationPos));
        }


        @Override
        public @Nullable Query<EntityStore> getQuery() {
            return Query.and(ColonistComponent.getComponentType(), JobComponent.getComponentType());
        }
    }

    /** Only here to log when colonists get assigned a job but are still accidentally marked as unemployed. */
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
                ColoniesPlugin.LOGGER.atSevere().log(String.format("Colonist with UUID %s has a job component but is still marked as unemployed. This should not happen.", uuidComponent.getUuid()));
            }
        }

        @Override
        public void onComponentSet(@Nonnull Ref<EntityStore> ref, @Nullable JobComponent oldComponent, @Nonnull JobComponent newComponent, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        }

        @Override
        public void onComponentRemoved(@NonNull Ref<EntityStore> var1, @NonNull JobComponent var2, @NonNull Store<EntityStore> var3, @NonNull CommandBuffer<EntityStore> var4) {
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(ColonistComponent.getComponentType(), JobComponent.getComponentType());
        }
    }

    /** Only here to log when colonists get marked as unemployed but still have a job assigned. */
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
                ColoniesPlugin.LOGGER.atSevere().log(String.format("Colonist with UUID %s is marked as unemployed but still has a job component. This should not happen.", uuidComponent.getUuid()));
            }
        }

        @Override
        public void onComponentSet(@Nonnull Ref<EntityStore> ref, @Nullable UnemployedComponent oldComponent, @Nonnull UnemployedComponent newComponent, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        }

        @Override
        public void onComponentRemoved(@NonNull Ref<EntityStore> var1, @NonNull UnemployedComponent var2, @NonNull Store<EntityStore> var3, @NonNull CommandBuffer<EntityStore> var4) {
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.and(ColonistComponent.getComponentType(), UnemployedComponent.getComponentType());
        }
    }
}

