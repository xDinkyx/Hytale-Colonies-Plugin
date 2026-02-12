package com.colonies.coloniesplugin.systems.jobs;

import com.colonies.coloniesplugin.ColoniesPlugin;
import com.colonies.coloniesplugin.components.jobs.JobProviderComponent;
import com.colonies.coloniesplugin.components.npc.ColonistComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
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
                     @NonNull Store<ChunkStore> chunkStore,
                     @NonNull CommandBuffer<ChunkStore> commandBuffer)
    {
        JobProviderComponent jobProvider = archetypeChunk.getComponent(index, ColoniesPlugin.getInstance().getJobProviderComponentType());
        Ref<ChunkStore> jobProviderRef = archetypeChunk.getReferenceTo(index);

        for (int i = 0; i < archetypeChunk.getArchetype().length(); i++) {
            var componentType = archetypeChunk.getArchetype().get(i);
            if (componentType != null) {
                String className = componentType.getTypeClass().getSimpleName();
                sb.append("-> ").append(className).append("\n");
            }
        }

        if(jobProvider.getAvailableJobSlots() <= 0) {
            return; // No available job slots, skip this JobProvider.
        }

        ColoniesPlugin.LOGGER.atInfo().log(String.format("JobAssignmentSystem JobProvider #%d Tick: Looking for unemployed colonists.", index));

        World world = chunkStore.getExternalData().getWorld();
        EntityStore entityStore = world.getEntityStore();
//        BlockChunk blockChunk = chunkStore.getComponent(jobProviderRef, Block.getComponentType());
//        BlockEntity blockEntity = BlockModule.getBlockEntity(world, blockChunk.getX(), blockChunk.get, );
////        UUIDComponent jobProviderUuid = entityStore.getStore().get()

        entityStore.getStore().forEachEntityParallel(unemployedQuery, (colonistId, _archetypeChunk, _cb) -> {
            ColonistComponent colonist = _archetypeChunk.getComponent(colonistId, ColoniesPlugin.getInstance().getColonistComponentType());
            assert colonist != null;

            if (colonist.isEmployed()) {
                return; // Skip employed colonists.
            }

            UUIDComponent colonistEntityUuid = _archetypeChunk.getComponent(colonistId, UUIDComponent.getComponentType());
            assert colonistEntityUuid != null;

            ColoniesPlugin.LOGGER.atInfo().log(String.format("Colonist #%d : %s is UNEMPLOYED | Colonist info: %s", colonistId, colonist.getColonistName(), colonist));
            jobProvider.assignColonist(colonistEntityUuid.getUuid());

        });
    }


    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return jobProviderQuery;
    }
}
