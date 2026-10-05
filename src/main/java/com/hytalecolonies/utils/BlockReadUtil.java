package com.hytalecolonies.utils;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Reads the block id at a world position. Replaces the removed {@code World.getBlock(x, y, z)} convenience
 * (deleted in Update 7 Part 5) by resolving the chunk section directly. Returns {@code 0} (air/empty) when the
 * section is not loaded, matching the old method's behavior.
 */
public final class BlockReadUtil
{
    private BlockReadUtil() {}

    public static int getBlockId(@Nonnull World world, int x, int y, int z)
    {
        ChunkStore      chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid())
            return 0;

        BlockSection section = chunkStore.getStore().getComponent(sectionRef, BlockSection.getComponentType());
        return section != null ? section.get(x, y, z) : 0;
    }
}
