package com.colonies.coloniesplugin.systems.jobs;

import com.colonies.coloniesplugin.ColoniesPlugin;
import com.colonies.coloniesplugin.components.jobs.ColonistJobComponent;
import com.colonies.coloniesplugin.components.jobs.JobProviderComponent;
import com.colonies.coloniesplugin.components.npc.ColonistComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;


// ToDo: Make more efficient by adding unemployed component and only querying those entities.
public class JobAssignmentSystem extends DelayedEntitySystem<ChunkStore> {

    // Query for Job Sources (Workstations/Blocks)
    private final Query<ChunkStore> jobProviderQuery = Archetype.of(ColoniesPlugin.getInstance().getJobProviderComponentType());
    private final Query<EntityStore> unemployedQuery = Archetype.of(ColoniesPlugin.getInstance().getColonistComponentType());

    public JobAssignmentSystem() {
        super(1.0f); // Run once every 1 second.
    }

    @Override
    public void tick(float dt, int index,
                     @NonNull ArchetypeChunk<ChunkStore> archetypeChunk,
                     @NonNull Store<ChunkStore> chunkStore,
                     @NonNull CommandBuffer<ChunkStore> commandBuffer)
    {
        JobProviderComponent jobProvider = archetypeChunk.getComponent(index, ColoniesPlugin.getInstance().getJobProviderComponentType());
        // If no job slots are available, do nothing.
        if(jobProvider.getAvailableJobSlots() <= 0) return;

        BlockModule.BlockStateInfo blockStateInfo = archetypeChunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
        if (blockStateInfo == null) return;

        // Get the world position of the job provider block entity
        WorldChunk worldChunkComponent = commandBuffer.getComponent(blockStateInfo.getChunkRef(), WorldChunk.getComponentType());
        Vector3i jobProviderPos = new Vector3i(
                ChunkUtil.worldCoordFromLocalCoord(worldChunkComponent.getX(), ChunkUtil.xFromBlockInColumn(blockStateInfo.getIndex())),
                ChunkUtil.yFromBlockInColumn(blockStateInfo.getIndex()),
                ChunkUtil.worldCoordFromLocalCoord(worldChunkComponent.getZ(), ChunkUtil.zFromBlockInColumn(blockStateInfo.getIndex()))
        );
        ColoniesPlugin.LOGGER.atInfo().log(String.format("Job Provider at position %s has %d available job slots. Attempting to assign unemployed colonists...", jobProviderPos, jobProvider.getAvailableJobSlots()));

        // Iterate through unemployed colonists and assign them to this job provider until we run out of job slots or unemployed colonists.
        World world = chunkStore.getExternalData().getWorld();
        EntityStore entityStore = world.getEntityStore();
        entityStore.getStore().forEachEntityParallel(unemployedQuery, (colonistId, _archetypeChunk, _cb) ->
        {
            ColonistComponent colonist = _archetypeChunk.getComponent(colonistId, ColoniesPlugin.getInstance().getColonistComponentType());
            assert colonist != null;

            if (colonist.isEmployed()) {
                return; // Skip employed colonists.
            }

            UUIDComponent colonistEntityUuid = _archetypeChunk.getComponent(colonistId, UUIDComponent.getComponentType());
            assert colonistEntityUuid != null;

            ColoniesPlugin.LOGGER.atInfo().log(String.format("Colonist #%d : %s is UNEMPLOYED | Colonist info: %s", colonistId, colonist.getColonistName(), colonist));

            // Assign the colonist to the job provider.
            jobProvider.assignColonist(colonistEntityUuid.getUuid());

            // Add job component to colonist with reference to job provider block.
            var colonistRef = entityStore.getRefFromUUID(colonistEntityUuid.getUuid());
            ColonistJobComponent newColonistJobComponent = new ColonistJobComponent();
            newColonistJobComponent.jobProviderBlockPosition = jobProviderPos;
            _cb.addComponent(colonistRef, ColoniesPlugin.getInstance().getColonistJobComponentType(), newColonistJobComponent);

            ColoniesPlugin.LOGGER.atInfo().log(String.format("Assigned Colonist #%d : %s to job at %s.", colonistId, colonist.getColonistName(), jobProvider.JobType));
        });
    }


    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return jobProviderQuery;
    }
}
