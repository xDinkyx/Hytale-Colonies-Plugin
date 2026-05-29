package com.hytalecolonies.utils;

import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/** Utility wrappers for block entity lookups. */
public final class BlockEntityUtil
{
    private BlockEntityUtil() {}

    /** Returns the block entity {@link Ref} at the given world position, or {@code null} if none. */
    @Nullable
    public static Ref<ChunkStore> getBlockEntityAt(World world, Vector3i pos)
    {
        return BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
    }
}
