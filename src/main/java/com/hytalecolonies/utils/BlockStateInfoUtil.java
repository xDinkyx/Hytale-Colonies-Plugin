package com.hytalecolonies.utils;

import org.joml.Vector3i;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class BlockStateInfoUtil
{
    /**
     * Resolves the world position of a {@link BlockModule.BlockStateInfo}. The index is local to its owning
     * {@link ChunkSection} (32x32x32), so the section's own coordinates give the world offset directly -- no need
     * to go through the legacy chunk column ({@code WorldChunk}).
     */
    public Vector3i GetBlockWorldPosition(BlockModule.BlockStateInfo blockStateInfo, ComponentAccessor<ChunkStore> componentAccessor)
    {
        ChunkSection section = componentAccessor.getComponent(blockStateInfo.getSectionRef(), ChunkSection.getComponentType());
        int          idx     = blockStateInfo.getIndex();
        return new Vector3i(ChunkUtil.worldCoordFromLocalCoord(section.getX(), ChunkUtil.xFromIndex(idx)),
                            section.getY() * ChunkUtil.SIZE + ChunkUtil.yFromIndex(idx),
                            ChunkUtil.worldCoordFromLocalCoord(section.getZ(), ChunkUtil.zFromIndex(idx)));
    }
}
