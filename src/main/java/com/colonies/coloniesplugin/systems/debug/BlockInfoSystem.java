package com.colonies.coloniesplugin.systems.debug;

import com.colonies.coloniesplugin.ColoniesPlugin;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.state.RespawnBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.StringJoiner;

public class BlockInfoSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public BlockInfoSystem() {
        super(PlaceBlockEvent.class);
    }

    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull PlaceBlockEvent event
    ) {
        ItemStack itemStack = event.getItemInHand();
        System.out.println("Block placed: " + itemStack.getBlockKey());

        LogChunkComponents(commandBuffer, event);
        LogEntityComponents(archetypeChunk);
    }

    private void LogChunkComponents(@Nonnull CommandBuffer<EntityStore> commandBuffer,
                                    @Nonnull PlaceBlockEvent event) {
        World world = commandBuffer.getExternalData().getWorld();
        Vector3i blockLocation = event.getTargetBlock();
        ChunkStore chunkComponentStore = world.getChunkStore();

        // Get the index and the reference to the specific chunk
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockLocation.x, blockLocation.z);
        Ref<ChunkStore> chunkRef = chunkComponentStore.getChunkReference(chunkIndex);

        // Retrieve the archetype for this chunk to see all attached components
        Archetype<ChunkStore> archetype = chunkRef.getStore().getArchetype(chunkRef);
        StringJoiner joiner = new StringJoiner(", ");

        // Iterate through all component types present in this archetype
        for (int i = 0; i < archetype.length(); i++) {
            ComponentType<ChunkStore, ?> type = archetype.get(i);
            if (type != null) {
                joiner.add(type.getTypeClass().getSimpleName());

                // Optional: Log specific data if you know the type
                // Object componentData = chunkComponentStore.getChunkComponent(chunkIndex, type);
            }
        }

        System.out.println("Chunk [" + chunkIndex + "] Components: [" + joiner.toString() + "]");
    }


    private void LogEntityComponents(@Nonnull ArchetypeChunk<EntityStore> archetypeChunk)
    {
        Archetype<EntityStore> archetype = archetypeChunk.getArchetype();
        StringJoiner joiner = new StringJoiner(", ");

        // Iterate using the public length() and get(i) methods
        for (int i = 0; i < archetype.length(); i++) {
            ComponentType<EntityStore, ?> type = archetype.get(i);

            // The array is sparse; only add non-null component types
            if (type != null) {
                joiner.add(type.getTypeClass().getSimpleName());
            }
        }

        System.out.println(" | EntityComponents: [" + joiner.toString() + "]");
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
