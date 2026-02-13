package com.colonies.coloniesplugin.utils;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public class BlockStateInfoUtil {
    public Vector3i GetBlockWorldPosition(BlockModule.BlockStateInfo blockStateInfo, CommandBuffer<ChunkStore> commandBuffer) {
        WorldChunk worldChunkComponent = commandBuffer.getComponent(blockStateInfo.getChunkRef(), WorldChunk.getComponentType());
        return new Vector3i(
                ChunkUtil.worldCoordFromLocalCoord(worldChunkComponent.getX(), ChunkUtil.xFromBlockInColumn(blockStateInfo.getIndex())),
                ChunkUtil.yFromBlockInColumn(blockStateInfo.getIndex()),
                ChunkUtil.worldCoordFromLocalCoord(worldChunkComponent.getZ(), ChunkUtil.zFromBlockInColumn(blockStateInfo.getIndex()))
        );
    }
}
