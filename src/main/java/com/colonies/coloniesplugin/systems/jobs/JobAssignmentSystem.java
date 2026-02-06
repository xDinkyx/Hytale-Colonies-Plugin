package com.colonies.coloniesplugin.systems.jobs;

import com.colonies.coloniesplugin.ColoniesPlugin;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.meta.state.LaunchPad;
import com.hypixel.hytale.server.core.universe.world.meta.state.RespawnBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;


// ToDo: Make more efficient by adding unemployed component and only querying those entities.
public class JobAssignmentSystem extends DelayedEntitySystem<ChunkStore> {

    // Query for NPCs that have a ColonistComponent but NO JobLink (unemployed)
//    private final Query<EntityStore> unemployedQuery;
    // Query for Job Sources (Workstations/Blocks)
    private final Query<ChunkStore> jobProviderQuery;

    public JobAssignmentSystem() {
        super(1.0f); // Run once every 1 second.

        jobProviderQuery = Archetype.of(ColoniesPlugin.getInstance().getJobProviderComponentType());
    }

    @Override
    public void tick(float dt, int index,
                     @NonNull ArchetypeChunk<ChunkStore> archetypeChunk,
                     @NonNull Store<ChunkStore> store,
                     @NonNull CommandBuffer<ChunkStore> commandBuffer)
    {
//        ColoniesPlugin.LOGGER.atInfo().log(
//                String.format(
//                        "JobAssignmentSystem #%d Tick: Scanning for unemployed colonists in chunk: %s",
//                        index, archetypeChunk.toString()
//                )
//        );

        // For each unemployed colonist, log their info (ToDo: Assign job)
//        store.forEachEntityParallel(unemployedQuery, (id, _archetypeChunk, _commandBuffer) -> {
//            ColonistComponent colonist = archetypeChunk.getComponent(id, ColoniesPlugin.getInstance().getColonistComponentType());
//            ColoniesPlugin.LOGGER.atInfo().log(
//                    String.format(
//                            "Assigning job to colonist #%d : %s | Colonist info: %s | Chunk: %s",
//                            id, colonist.getColonistName(), colonist.toString(), archetypeChunk.toString()
//                    )
//            );
//        });
    }


    @Override
    public @Nullable Query<ChunkStore> getQuery() {
//        return jobProviderQuery;
       return Query.and(RespawnBlock.getComponentType());
    }
}
