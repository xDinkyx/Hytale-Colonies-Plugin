package com.hytalecolonies.utils;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic utility for locating block-based item containers (chests, crates, etc.)
 * that are close to a workstation — within a configurable block radius.
 *
 * <p>Uses the engine's {@link ItemContainerBlock} spatial index for efficient
 * lookup rather than scanning all chunks. This is the same spatial index the server
 * uses internally to track all open containers (see {@code CraftingManager.getContainersAroundBench}).
 *
 * <p>Job-specific delivery systems call {@link #findNearbyContainer} to retrieve the
 * deposit target at runtime. The result is not persisted; the scan is cheap because
 * it only checks blocks already registered with the spatial index.
 */
public final class WorkstationContainerUtil {

    private WorkstationContainerUtil() {}

    /**
     * Returns the world position of the nearest {@link ItemContainerBlock} block
     * within {@code radiusBlocks} of {@code workStationPos}, or {@code null} if none.
     *
     * @param world          the world to search
     * @param workStationPos block position of the workstation
     * @param radiusBlocks   inclusive search radius in blocks (3D Euclidean)
     */
    @Nullable
    public static Vector3i findNearbyContainer(World world, Vector3i workStationPos, int radiusBlocks) {
        SpatialResource<Ref<ChunkStore>, ChunkStore> spatialResource =
                world.getChunkStore().getStore().getResource(BlockModule.get().getItemContainerSpatialResourceType());

        Vector3d searchCenter = new Vector3d(workStationPos.x + 0.5, workStationPos.y + 0.5, workStationPos.z + 0.5);
        List<Ref<ChunkStore>> nearby = new ArrayList<>();
        // Use ordered3DAxis (same as engine's CraftingManager chest-linking) to allow separate
        // horizontal/vertical radii — here they're equal for a uniform 3D sphere search.
        spatialResource.getSpatialStructure().ordered3DAxis(searchCenter, radiusBlocks, radiusBlocks, radiusBlocks, nearby);

        if (nearby.isEmpty()) return null;

        // The spatial index is pre-sorted by distance, so the first valid ref is the nearest.
        for (Ref<ChunkStore> ref : nearby) {
            if (!ref.isValid()) continue;
            ItemContainerBlock container = ref.getStore().getComponent(ref, BlockModule.get().getItemContainerBlockComponentType());
            if (container == null) continue;

            // Retrieve block world position from BlockStateInfo.
            var blockStateInfo = ref.getStore().getComponent(ref, com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo.getComponentType());
            if (blockStateInfo == null) continue;

            var chunkRef = blockStateInfo.getChunkRef();
            if (chunkRef == null || !chunkRef.isValid()) continue;

            var worldChunk = chunkRef.getStore().getComponent(chunkRef, com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk.getComponentType());
            if (worldChunk == null) continue;

            int idx = blockStateInfo.getIndex();
            int wx = com.hypixel.hytale.math.util.ChunkUtil.worldCoordFromLocalCoord(worldChunk.getX(), com.hypixel.hytale.math.util.ChunkUtil.xFromBlockInColumn(idx));
            int wy = com.hypixel.hytale.math.util.ChunkUtil.yFromBlockInColumn(idx);
            int wz = com.hypixel.hytale.math.util.ChunkUtil.worldCoordFromLocalCoord(worldChunk.getZ(), com.hypixel.hytale.math.util.ChunkUtil.zFromBlockInColumn(idx));

            return new Vector3i(wx, wy, wz);
        }
        return null;
    }
}
