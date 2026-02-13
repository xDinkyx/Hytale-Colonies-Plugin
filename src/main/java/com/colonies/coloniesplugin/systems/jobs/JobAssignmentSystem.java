package com.colonies.coloniesplugin.systems.jobs;

import com.colonies.coloniesplugin.ColoniesPlugin;
import com.colonies.coloniesplugin.components.jobs.ColonistJobComponent;
import com.colonies.coloniesplugin.components.jobs.JobProviderComponent;
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
        assert jobProvider != null;

        // If no job slots are available, do nothing.
        if(jobProvider.getAvailableJobSlots() <= 0) return;

        BlockModule.BlockStateInfo blockStateInfo = archetypeChunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
        if (blockStateInfo == null) return;

        // Get the world position of the job provider block entity
        Vector3i jobProviderPos = new BlockStateInfoUtil().GetBlockWorldPosition(blockStateInfo, commandBuffer);

        ColoniesPlugin.LOGGER.atInfo().log(String.format("Job Provider at position %s has %d available job slots. Attempting to assign unemployed colonists...", jobProviderPos, jobProvider.getAvailableJobSlots()));

        // Iterate through unemployed colonists and assign them to this job provider until we run out of job slots or unemployed colonists.
        World world = chunkStore.getExternalData().getWorld();
        EntityStore entityStore = world.getEntityStore();
        entityStore.getStore().forEachChunk(unemployedQuery, (_archetypeChunk, _commandBuffer) ->
        {
            for (int colonistId = 0; colonistId < _archetypeChunk.size(); colonistId++) {

                ColonistComponent colonist = _archetypeChunk.getComponent(colonistId, ColoniesPlugin.getInstance().getColonistComponentType());
                assert colonist != null;

                // Check if colonist already has a job. If so, skip them.
                ColonistJobComponent colonistJob = _archetypeChunk.getComponent(colonistId, ColonistJobComponent.getComponentType());
                if (colonistJob != null) continue; // Skip employed colonists.

                // Get colonist UUID and entity ref.
                UUIDComponent colonistEntityUuid = _archetypeChunk.getComponent(colonistId, UUIDComponent.getComponentType());
                assert colonistEntityUuid != null;

                ColoniesPlugin.LOGGER.atInfo().log(String.format("Colonist #%d : %s is UNEMPLOYED | Colonist info: %s", colonistId, colonist.getColonistName(), colonist));

                // Assign the colonist to the job provider.
                jobProvider.assignColonist(colonistEntityUuid.getUuid());

                // Add job component to colonist with reference to job provider block.
                ColonistJobComponent newColonistJobComponent = new ColonistJobComponent();
                newColonistJobComponent.setJobProviderBlockPosition(jobProviderPos);

                Ref<EntityStore> colonistRef = _archetypeChunk.getReferenceTo(colonistId);
                _commandBuffer.addComponent(colonistRef, ColoniesPlugin.getInstance().getColonistJobComponentType(), newColonistJobComponent);

                ColoniesPlugin.LOGGER.atInfo().log(String.format("Assigned Colonist #%d : %s to job at %s.", colonistId, colonistEntityUuid.getUuid(), jobProvider.getJobType()));

                // Move on to the next job provider after assigning one colonist to this provider.
                // We only assign one colonist per tick to prevent all colonists from being assigned to the first job provider we find.
                // ToDo: Implement more complex job assignment logic that considers distance to job providers and colonist preferences, etc.
                break;
            }
        });
    }

    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return jobProviderQuery;
    }
}
