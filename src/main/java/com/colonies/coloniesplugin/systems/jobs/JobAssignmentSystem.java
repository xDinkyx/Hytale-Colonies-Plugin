package com.colonies.coloniesplugin.systems.jobs;

import com.colonies.coloniesplugin.ColoniesPlugin;
import com.colonies.coloniesplugin.components.jobs.JobComponent;
import com.colonies.coloniesplugin.components.jobs.WorkStationComponent;
import com.colonies.coloniesplugin.components.npc.ColonistComponent;
import com.colonies.coloniesplugin.utils.BlockStateInfoUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;


// ToDo: Make more efficient by adding unemployed component and only querying those entities.
public class JobAssignmentSystem extends DelayedEntitySystem<ChunkStore> {

    // Query for Job Sources (Workstations/Blocks)
    private final Query<ChunkStore> workStationQuery = Archetype.of(ColoniesPlugin.getInstance().getWorkStationComponentType());
    private final Query<EntityStore> colonistQuery = Archetype.of(ColoniesPlugin.getInstance().getColonistComponentType()); // ToDo: Replace later with unemployed component query.

    public JobAssignmentSystem() {
        super(1.0f); // Run once every 1 second.
    }

    @Override
    public void tick(float dt, int index,
                     @NonNull ArchetypeChunk<ChunkStore> archetypeChunk,
                     @NonNull Store<ChunkStore> chunkStore,
                     @NonNull CommandBuffer<ChunkStore> commandBuffer)
    {
        WorkStationComponent workStation = archetypeChunk.getComponent(index, ColoniesPlugin.getInstance().getWorkStationComponentType());
        assert workStation != null;

        // If no job slots are available, do nothing.
        if(workStation.getAvailableJobSlots() <= 0) return;

        BlockModule.BlockStateInfo blockStateInfo = archetypeChunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
        if (blockStateInfo == null) return;

        // Get the world position of the work station block entity
        Vector3i workStationPos = new BlockStateInfoUtil().GetBlockWorldPosition(blockStateInfo, commandBuffer);

        ColoniesPlugin.LOGGER.atInfo().log(String.format("Work station at position %s has %d available job slots of type %s. Attempting to assign unemployed colonists...", workStationPos, workStation.getAvailableJobSlots(), workStation.getJobType().toString()));

        // Iterate through unemployed colonists and assign them to this work station until we run out of job slots or unemployed colonists.
        World world = chunkStore.getExternalData().getWorld();
        EntityStore entityStore = world.getEntityStore();
        entityStore.getStore().forEachChunk(colonistQuery, (_archetypeChunk, _commandBuffer) ->
        {
            for (int colonistId = 0; colonistId < _archetypeChunk.size(); colonistId++) {

                ColonistComponent colonist = _archetypeChunk.getComponent(colonistId, ColoniesPlugin.getInstance().getColonistComponentType());
                assert colonist != null;

                // Check if colonist already has a job. If so, skip them.
                JobComponent colonistJob = _archetypeChunk.getComponent(colonistId, JobComponent.getComponentType());
                if (colonistJob != null) continue; // Skip employed colonists.

                // Get colonist UUID and entity ref.
                UUIDComponent colonistEntityUuid = _archetypeChunk.getComponent(colonistId, UUIDComponent.getComponentType());
                assert colonistEntityUuid != null;

                ColoniesPlugin.LOGGER.atInfo().log(String.format("Colonist #%d : %s is UNEMPLOYED | Colonist info: %s", colonistId, colonist.getColonistName(), colonist));

                // Assign the colonist to the work station.
                workStation.assignColonist(colonistEntityUuid.getUuid());

                // Add job component to colonist.
                Ref<EntityStore> colonistRef = _archetypeChunk.getReferenceTo(colonistId);
                JobComponent newJobComponent = new JobComponent(workStationPos);
                _commandBuffer.addComponent(colonistRef, ColoniesPlugin.getInstance().getColonistJobComponentType(), newJobComponent);

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
}
