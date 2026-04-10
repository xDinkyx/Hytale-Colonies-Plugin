package com.hytalecolonies.utils;

import javax.annotation.Nullable;

import com.hytalecolonies.components.jobs.ConstructionOrderComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.debug.DebugTiming;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.prefab.PrefabLoadException;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;

/** Utility methods for the Constructor colonist job. */
public final class ConstructorUtil {

    private static final String EMPTY_BLOCK_KEY = "Empty";

    private ConstructorUtil() {}

    /** Loads the prefab named in {@code order}. Returns {@code null} if the order is absent or the prefab cannot be loaded. */
    @Nullable
    public static BlockSelection loadPrefab(@Nullable ConstructionOrderComponent order) {
        if (order == null || order.prefabId == null || order.prefabId.isEmpty()) {
            return null;
        }
        try {
            return PrefabStore.get().getServerPrefab(order.prefabId);
        } catch (PrefabLoadException e) {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                    "[ConstructorUtil] Failed to load prefab '%s': %s", order.prefabId, e.getMessage());
            return null;
        }
    }

    /** Returns the next world position inside the prefab footprint that needs to be cleared, or {@code null} when clearing is complete. */
    @Nullable
    public static Vector3i findNextClearingTarget(@Nullable ConstructionOrderComponent order, World world, BlockSelection prefab) {
        if (order == null || order.buildOrigin == null) return null;
        Vector3i origin = order.buildOrigin;

        int emptyId = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);
        Vector3i[] result = {null};

        try (var _ = DebugTiming.measure("ConstructorUtil.findNextClearingTarget", 50)) {
            prefab.forEachBlock((lx, ly, lz, block) -> {
                if (result[0] != null) return;

                int prefabBlockId = block.blockId();
                boolean prefabWantsAir = (prefabBlockId == 0 || prefabBlockId == emptyId);
                if (!prefabWantsAir) return;

                int wx = lx + origin.x - prefab.getAnchorX();
                int wy = ly + origin.y - prefab.getAnchorY();
                int wz = lz + origin.z - prefab.getAnchorZ();

                if (world.getBlock(wx, wy, wz) != 0) {
                    result[0] = new Vector3i(wx, wy, wz);
                }
            });
        }

        return result[0];
    }

    /** Returns the next world position inside the prefab footprint that needs to be built, or {@code null} when the build phase is complete. */
    @Nullable
    public static Vector3i findNextBuildTarget(@Nullable ConstructionOrderComponent order, World world, BlockSelection prefab) {
        if (order == null || order.buildOrigin == null) return null;
        Vector3i origin = order.buildOrigin;

        int emptyId = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);
        Vector3i[] result = {null};

        try (var _ = DebugTiming.measure("ConstructorUtil.findNextBuildTarget", 50)) {
            prefab.forEachBlock((lx, ly, lz, block) -> {
                if (result[0] != null) return;

                int prefabBlockId = block.blockId();
                boolean prefabWantsAir = (prefabBlockId == 0 || prefabBlockId == emptyId);
                if (prefabWantsAir) return;

                int wx = lx + origin.x - prefab.getAnchorX();
                int wy = ly + origin.y - prefab.getAnchorY();
                int wz = lz + origin.z - prefab.getAnchorZ();

                if (world.getBlock(wx, wy, wz) != prefabBlockId) {
                    result[0] = new Vector3i(wx, wy, wz);
                }
            });
        }

        return result[0];
    }

    /**
     * Returns the block type key the prefab expects at the given world position,
     * {@code "Empty"} for air positions, or {@code null} if outside the prefab footprint.
     */
    @Nullable
    public static String getDesiredBlockKey(@Nullable ConstructionOrderComponent order, BlockSelection prefab, int wx, int wy, int wz) {
        if (order == null || order.buildOrigin == null) return null;
        Vector3i origin = order.buildOrigin;

        int lx = wx - origin.x + prefab.getAnchorX();
        int ly = wy - origin.y + prefab.getAnchorY();
        int lz = wz - origin.z + prefab.getAnchorZ();

        int blockId = prefab.getBlockAtWorldPos(lx, ly, lz);
        if (blockId == Integer.MIN_VALUE) return null;

        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        return blockType != null ? blockType.getId() : null;
    }
}
