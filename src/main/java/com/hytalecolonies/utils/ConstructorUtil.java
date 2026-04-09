package com.hytalecolonies.utils;

import javax.annotation.Nullable;

import com.hytalecolonies.components.jobs.ConstructionOrderComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.debug.DebugTiming;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.prefab.PrefabLoadException;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/** Utility methods for the Constructor colonist job. */
public final class ConstructorUtil {

    /** The block type key used in prefabs to mark a position that should be cleared to air. */
    private static final String EMPTY_BLOCK_KEY = "Empty";

    private ConstructorUtil() {}

    /**
     * Loads the prefab for the active construction order on the workstation.
     * The prefab key is retrieved from the {@link ConstructionOrderComponent} attached to the
     * block entity at {@code ws.activeConstructionOrderOrigin}.
     * Returns {@code null} when there is no active order, the block entity cannot be found,
     * or the prefab cannot be loaded.
     */
    @Nullable
    public static BlockSelection loadPrefab(WorkStationComponent ws, World world) {
        Vector3i origin = ws.activeConstructionOrderOrigin;
        if (origin == null) return null;

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, origin.x, origin.y, origin.z);
        if (blockRef == null || !blockRef.isValid()) {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                    "[ConstructorUtil] No block entity at active construction order origin %s.", origin);
            return null;
        }

        ConstructionOrderComponent order = chunkStore.getComponent(blockRef, ConstructionOrderComponent.getComponentType());
        if (order == null || order.prefabId == null || order.prefabId.isEmpty()) {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                    "[ConstructorUtil] No ConstructionOrderComponent (or empty prefabId) at origin %s.", origin);
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

    /**
     * Finds the next world position inside the prefab footprint that needs to be cleared:
     * the prefab expects air ("Empty") but the world has a solid block.
     *
     * <p>Returns {@code null} when the clearing phase is complete (no mismatches remain).
     */
    @Nullable
    public static Vector3i findNextClearingTarget(WorkStationComponent ws, World world, BlockSelection prefab) {
        Vector3i origin = ws.activeConstructionOrderOrigin;
        if (origin == null) return null;

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

    /**
     * Finds the next world position inside the prefab footprint that needs to be built:
     * the prefab expects a solid block but the world has the wrong block (or air).
     *
     * <p>Returns {@code null} when the build phase is complete (all blocks match).
     */
    @Nullable
    public static Vector3i findNextBuildTarget(WorkStationComponent ws, World world, BlockSelection prefab) {
        Vector3i origin = ws.activeConstructionOrderOrigin;
        if (origin == null) return null;

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
     * Returns the block type key the prefab expects at the given world position.
     * Returns {@code "Empty"} for positions the prefab wants cleared to air.
     * Returns {@code null} if the position is outside the prefab footprint or has
     * an unknown block type.
     *
     * <p>Coordinate formula: {@code lx = wx - origin.x + prefab.getAnchorX()}
     */
    @Nullable
    public static String getDesiredBlockKey(WorkStationComponent ws, BlockSelection prefab, int wx, int wy, int wz) {
        Vector3i origin = ws.activeConstructionOrderOrigin;
        if (origin == null) return null;

        int lx = wx - origin.x + prefab.getAnchorX();
        int ly = wy - origin.y + prefab.getAnchorY();
        int lz = wz - origin.z + prefab.getAnchorZ();

        // prefab.getX() == 0 for freshly loaded prefabs, so getBlockAtWorldPos(lx,ly,lz)
        // is equivalent to getBlockAtLocalPos(lx, ly, lz).
        int blockId = prefab.getBlockAtWorldPos(lx, ly, lz);
        if (blockId == Integer.MIN_VALUE) return null; // outside footprint

        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        return blockType != null ? blockType.getId() : null;
    }
}
