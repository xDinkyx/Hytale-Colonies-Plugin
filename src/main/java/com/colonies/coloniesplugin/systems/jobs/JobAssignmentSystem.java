package com.colonies.coloniesplugin.systems.jobs;

import com.colonies.coloniesplugin.ColoniesPlugin;
import com.colonies.coloniesplugin.components.npc.ColonistComponent;
import com.hypixel.hytale.builtin.portals.components.PortalDevice;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


// ToDo: Make more efficient by adding unemployed component and only querying those entities.
public class JobAssignmentSystem extends DelayedEntitySystem<EntityStore> {

    // Query for NPCs that have a ColonistComponent but NO JobLink (unemployed)
    private final Query<EntityStore> unemployedQuery;
    // Query for Job Sources (Workstations/Blocks)
    private final Query<EntityStore> jobProviderQuery;

    public JobAssignmentSystem() {
        super(1.0f); // Run once every 1 second.

        jobProviderQuery = Query.and(ColoniesPlugin.getInstance().getJobProviderComponentType());
        unemployedQuery = Query.and(ColoniesPlugin.getInstance().getJobProviderComponentType());
    }

    @Override
    public void tick(float dt, int index,
                     @NonNull ArchetypeChunk<EntityStore> archetypeChunk,
                     @NonNull Store<EntityStore> store,
                     @NonNull CommandBuffer<EntityStore> commandBuffer)
    {
        ColoniesPlugin.LOGGER.atInfo().log(
                String.format(
                        "JobAssignmentSystem #%d Tick: Scanning for unemployed colonists in chunk: %s",
                        index, archetypeChunk.toString()
                )
        );

        // For each unemployed colonist, log their info (ToDo: Assign job)
        store.forEachEntityParallel(unemployedQuery, (id, _archetypeChunk, _commandBuffer) -> {
            ColonistComponent colonist = archetypeChunk.getComponent(id, ColoniesPlugin.getInstance().getColonistComponentType());
            ColoniesPlugin.LOGGER.atInfo().log(
                    String.format(
                            "Assigning job to colonist #%d : %s | Colonist info: %s | Chunk: %s",
                            id, colonist.getColonistName(), colonist.toString(), archetypeChunk.toString()
                    )
            );
        });
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return jobProviderQuery;
    }
}
