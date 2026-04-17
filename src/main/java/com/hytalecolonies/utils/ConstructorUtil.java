package com.hytalecolonies.utils;

import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.prefab.PrefabLoadException;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hytalecolonies.components.jobs.ConstructionOrderComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.debug.DebugTiming;
import com.hytalecolonies.listeners.ConstructorBuildOrderFilter;

/** Utility methods for the Constructor colonist job. */
public final class ConstructorUtil
{

    private static final String EMPTY_BLOCK_KEY = "Empty";

    private ConstructorUtil()
    {
    }

    /** Loads the prefab for {@code order}. Prefers the runtime selection (preserving rotation); falls back to loading from disk. */
    @Nullable public static BlockSelection loadPrefab(@Nullable ConstructionOrderComponent order)
    {
        if (order == null || order.prefabId == null || order.prefabId.isEmpty())
        {
            return null;
        }
        // Prefer the runtime selection -- it has the player's rotation baked in.
        if (order.buildOrigin != null)
        {
            BlockSelection cached = ConstructorBuildOrderFilter.pendingSelections.get(order.buildOrigin);
            if (cached != null)
                return cached;
        }
        // Fall back to disk load (loses rotation, but works after restart).
        try
        {
            return PrefabStore.get().getPrefab(java.nio.file.Path.of(order.prefabId));
        }
        catch (PrefabLoadException e)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorUtil] Failed to load prefab '%s': %s", order.prefabId, e.getMessage());
            return null;
        }
    }

    /** Returns the next world position inside the prefab footprint that needs to be cleared, or {@code null} when clearing is complete. */
    @Nullable public static Vector3i findNextClearingTarget(@Nullable ConstructionOrderComponent order, World world, BlockSelection prefab)
    {
        if (order == null || order.buildOrigin == null)
            return null;
        Vector3i origin = order.buildOrigin;

        int emptyId = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);
        Vector3i[] result = {null};

        try (var _ = DebugTiming.measure("ConstructorUtil.findNextClearingTarget", 50))
        {
            prefab.forEachBlock((lx, ly, lz, block) -> {
                if (result[0] != null)
                    return;

                int prefabBlockId = block.blockId();
                boolean prefabWantsAir = (prefabBlockId == 0 || prefabBlockId == emptyId);

                int wx = lx + origin.x - prefab.getAnchorX();
                int wy = ly + origin.y - prefab.getAnchorY();
                int wz = lz + origin.z - prefab.getAnchorZ();

                int worldBlock = world.getBlock(wx, wy, wz);

                if (prefabWantsAir)
                {
                    if (worldBlock != 0)
                        result[0] = new Vector3i(wx, wy, wz);
                }
                else
                {
                    // forEachBlock only visits explicitly stored entries; air positions are never
                    // iterated, so wrong non-air world blocks are the primary clearing target.
                    if (worldBlock != 0 && worldBlock != prefabBlockId)
                        result[0] = new Vector3i(wx, wy, wz);
                }
            });
        }

        return result[0];
    }

    /** Returns the next world position inside the prefab footprint that needs to be built, or {@code null} when the build phase is complete. */
    @Nullable public static Vector3i findNextBuildTarget(@Nullable ConstructionOrderComponent order, World world, BlockSelection prefab)
    {
        if (order == null || order.buildOrigin == null)
            return null;
        Vector3i origin = order.buildOrigin;

        int emptyId = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);
        Vector3i[] result = {null};

        try (var _ = DebugTiming.measure("ConstructorUtil.findNextBuildTarget", 50))
        {
            prefab.forEachBlock((lx, ly, lz, block) -> {
                if (result[0] != null)
                    return;

                int prefabBlockId = block.blockId();
                boolean prefabWantsAir = (prefabBlockId == 0 || prefabBlockId == emptyId);
                if (prefabWantsAir)
                    return;

                int wx = lx + origin.x - prefab.getAnchorX();
                int wy = ly + origin.y - prefab.getAnchorY();
                int wz = lz + origin.z - prefab.getAnchorZ();

                if (world.getBlock(wx, wy, wz) != prefabBlockId)
                {
                    result[0] = new Vector3i(wx, wy, wz);
                }
            });
        }

        return result[0];
    }

    /** White = origin, red = needs clearing, yellow = needs filling, green = already correct. */
    public static void drawConstructionOrderOverlay(@Nullable ConstructionOrderComponent order, @Nullable BlockSelection prefab, World world)
    {
        if (order == null || order.buildOrigin == null || prefab == null)
            return;
        Vector3i origin = order.buildOrigin;
        float drawTime = 5.0f;
        int emptyId = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);

        DebugUtils.addCube(world, origin.x + 0.5, origin.y + 0.5, origin.z + 0.5, DebugUtils.COLOR_WHITE, 1.4, drawTime);

        prefab.forEachBlock((lx, ly, lz, block) -> {
            int prefabBlockId = block.blockId();
            boolean prefabWantsAir = (prefabBlockId == 0 || prefabBlockId == emptyId);

            int wx = lx + origin.x - prefab.getAnchorX();
            int wy = ly + origin.y - prefab.getAnchorY();
            int wz = lz + origin.z - prefab.getAnchorZ();

            int worldBlock = world.getBlock(wx, wy, wz);

            Vector3f color;
            if (prefabWantsAir ? worldBlock != 0 : (worldBlock != 0 && worldBlock != prefabBlockId)) {
                color = DebugUtils.COLOR_RED;    // wrong block present -- must remove
            } else if (!prefabWantsAir && worldBlock == 0) {
                color = DebugUtils.COLOR_YELLOW; // block missing -- must fill
            } else {
                color = DebugUtils.COLOR_LIME;   // already correct
            }
            DebugUtils.addCube(world, wx + 0.5, wy + 0.5, wz + 0.5, color, 0.9, drawTime);
        });
    }

    /** Returns {@code "Empty"} for air, the block type key for solid positions, or {@code null} if outside the footprint. */
    @Nullable public static String getDesiredBlockKey(@Nullable ConstructionOrderComponent order, BlockSelection prefab, int wx, int wy, int wz)
    {
        if (order == null || order.buildOrigin == null)
            return null;
        Vector3i origin = order.buildOrigin;

        int lx = wx - origin.x + prefab.getAnchorX();
        int ly = wy - origin.y + prefab.getAnchorY();
        int lz = wz - origin.z + prefab.getAnchorZ();

        int blockId = prefab.getBlockAtWorldPos(lx, ly, lz);
        if (blockId == Integer.MIN_VALUE)
            return null;

        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        return blockType != null ? blockType.getId() : null;
    }
}
