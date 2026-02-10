package com.colonies.coloniesplugin.systems.jobs;

import com.colonies.coloniesplugin.ColoniesPlugin;
import com.colonies.coloniesplugin.components.npc.ColonistComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;


// ToDo: Make more efficient by adding unemployed component and only querying those entities.
public class JobAssignmentSystem extends DelayedEntitySystem<ChunkStore> {

    // Query for NPCs that have a ColonistComponent but NO JobLink (unemployed)
//    private final Query<EntityStore> unemployedQuery;
    // Query for Job Sources (Workstations/Blocks)
    private final Query<ChunkStore> jobProviderQuery = Archetype.of(ColoniesPlugin.getInstance().getJobProviderComponentType());
    private final Query<EntityStore> unemployedQuery = Archetype.of(ColoniesPlugin.getInstance().getColonistComponentType());

    public JobAssignmentSystem() {
        super(1.0f); // Run once every 1 second.
    }

    @Override
    public void tick(float dt, int index,
                     @NonNull ArchetypeChunk<ChunkStore> archetypeChunk,
                     @NonNull Store<ChunkStore> store,
                     @NonNull CommandBuffer<ChunkStore> commandBuffer)
    {
        ColoniesPlugin.LOGGER.atInfo().log(String.format("JobAssignmentSystem JobProvider #%d Tick: Looking for unemployed colonists.", index));

        World world = store.getExternalData().getWorld();
        EntityStore entityStore = world.getEntityStore();

        entityStore.getStore().forEachEntityParallel(unemployedQuery, (id, _archetypeChunk, _cb) -> {
            ColonistComponent colonist = _archetypeChunk.getComponent(id, ColoniesPlugin.getInstance().getColonistComponentType());
            if (colonist == null) {
                ColoniesPlugin.LOGGER.atInfo().log(String.format("Colonist #%d : ColonistComponent is NULL", id));
                return;
            }

            if (colonist.isEmployed()) {
                ColoniesPlugin.LOGGER.atInfo().log(String.format("Colonist #%d : %s is EMPLOYED | Colonist info: %s", id, colonist.getColonistName(), colonist));
            } else {
                ColoniesPlugin.LOGGER.atInfo().log(String.format("Colonist #%d : %s is UNEMPLOYED | Colonist info: %s", id, colonist.getColonistName(), colonist));
            }
        });
    }


    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return jobProviderQuery;
    }
}
