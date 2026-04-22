package com.hytalecolonies.utils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.prefab.PrefabLoadException;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hytalecolonies.ConstructionOrderStore;
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

    /**
     * Collects all non-empty prefab blocks and returns them sorted by Y ascending so colonists build floor-first.
     * Each entry is {@code {lx, ly, lz, blockId}}.
     */
    private static List<int[]> sortedPrefabBlocks(BlockSelection prefab)
    {
        List<int[]> blocks = new ArrayList<>();
        prefab.forEachBlock((lx, ly, lz, block) -> blocks.add(new int[] {lx, ly, lz, block.blockId()}));
        blocks.sort(Comparator.comparingInt((int[] b) -> b[1]).thenComparingInt(b -> b[0]).thenComparingInt(b -> b[2]));
        return blocks;
    }

    /**
     * Returns the cached sorted block list for {@code order}, building it on first use.
     * Callers iterate forward (Y ascending) for building, or reversed for clearing.
     */
    private static List<int[]> getSortedBlocks(ConstructionOrderStore.Entry order, BlockSelection prefab)
    {
        if (order.cachedSortedBlocks == null)
            order.cachedSortedBlocks = sortedPrefabBlocks(prefab);
        return order.cachedSortedBlocks;
    }

    /**
     * Returns {@code true} if {@code worldBlockId} satisfies the {@code prefabBlockId} requirement.
     * Handles grass growth: a placed {@code Soil_Dirt} block that has grown into any
     * {@code Soil_Grass} variant is considered already correct.
     *
     * <p>TODO: this equivalence logic is hardcoded. Move to a data-driven JSON mapping
     * (e.g. a tag or a dedicated block-equivalence asset) so new cases don't require code changes.
     */
    private static boolean isBlockAcceptable(int worldBlockId, int prefabBlockId)
    {
        if (worldBlockId == prefabBlockId)
            return true;

        BlockType expected = BlockType.getAssetMap().getAsset(prefabBlockId);
        BlockType actual = BlockType.getAssetMap().getAsset(worldBlockId);
        if (expected == null || actual == null)
            return false;

        String eKey = expected.getId();
        String aKey = actual.getId();
        if (!eKey.startsWith("Soil_Dirt") || !aKey.startsWith("Soil_Grass"))
            return false;

        // Plain Soil_Dirt accepts any Soil_Grass* variant.
        if (eKey.equals("Soil_Dirt"))
            return true;

        // Soil_Dirt_X accepts only Soil_Grass_X (e.g. Soil_Dirt_Cold -> Soil_Grass_Cold).
        String suffix = eKey.substring("Soil_Dirt".length());
        return aKey.equals("Soil_Grass" + suffix);
    }

    /**
     * Returns the cached runtime selection if available, otherwise loads from the asset-pack ZipFS.
     * {@code prefabId} is a ZipFS-internal path, e.g. {@code /Server/Prefabs/test.prefab.json}.
     * The loaded result is stored back on {@code entry.cachedSelection} for reuse.
     */
    @Nullable public static BlockSelection loadPrefab(@Nullable ConstructionOrderStore.Entry order)
    {
        if (order == null)
        {
            return null; // caller should log
        }

        if (order.prefabId == null || order.prefabId.isEmpty())
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorUtil] Order %s has no prefabId stored -- cannot load prefab.", order.id);
            return null;
        }

        // Prefer the entry's own cached selection.
        if (order.cachedSelection != null)
            return order.cachedSelection;

        // Next, prefer the runtime selection from the build-order filter -- it has the player's rotation baked in.
        if (order.buildOrigin != null)
        {
            BlockSelection cached = ConstructorBuildOrderFilter.pendingSelections.get(order.buildOrigin);
            if (cached != null)
            {
                order.cachedSelection = cached;
                order.cachedSortedBlocks = sortedPrefabBlocks(cached);
                return cached;
            }
        }

        // Fall back to the asset-pack ZipFS path (must use the live ZipFileSystem to avoid cross-filesystem issues).
        try
        {
            Path packRoot = AssetModule.get().getBaseAssetPack().getRoot();
            Path prefabPath = packRoot.getFileSystem().getPath(order.prefabId);
            BlockSelection loaded = PrefabStore.get().getPrefab(prefabPath);
            order.cachedSelection = loaded;
            order.cachedSortedBlocks = sortedPrefabBlocks(loaded);
            return loaded;
        }
        catch (PrefabLoadException e)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorUtil] Failed to load prefab '%s' from asset pack: %s", order.prefabId, e.getMessage());
            return null;
        }
        catch (Exception e)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                             "[ConstructorUtil] Error loading prefab '%s': %s (%s)",
                             order.prefabId,
                             e.getMessage(),
                             e.getClass().getSimpleName());
            return null;
        }
    }

    /** Returns the next world position inside the prefab footprint that needs to be cleared, or {@code null} when clearing is complete. Scans top-down (Y descending) so floating blocks are cleared first. */
    @Nullable public static Vector3i findNextClearingTarget(@Nullable ConstructionOrderStore.Entry order, World world, BlockSelection prefab)
    {
        if (order == null || order.buildOrigin == null)
            return null;
        Vector3i origin = order.buildOrigin;

        int emptyId = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);

        try (var _ = DebugTiming.measure("ConstructorUtil.findNextClearingTarget", 50))
        {
            List<int[]> blocks = getSortedBlocks(order, prefab);
            for (int i = blocks.size() - 1; i >= 0; i--)
            {
                int[] b = blocks.get(i);
                int prefabBlockId = b[3];
                boolean isAir = (prefabBlockId == 0 || prefabBlockId == emptyId);

                int wx = b[0] + origin.x - prefab.getAnchorX();
                int wy = b[1] + origin.y - prefab.getAnchorY();
                int wz = b[2] + origin.z - prefab.getAnchorZ();

                int worldBlock = world.getBlock(wx, wy, wz);

                if (isAir)
                {
                    if (worldBlock != 0)
                        return new Vector3i(wx, wy, wz);
                }
                else
                {
                    if (worldBlock != 0 && !isBlockAcceptable(worldBlock, prefabBlockId))
                        return new Vector3i(wx, wy, wz);
                }
            }
        }

        return null;
    }

    /**
     * Returns the next world position inside the prefab footprint that is currently air/empty
     * and still needs a block placed there, or {@code null} when no such position exists.
     */
    @Nullable public static Vector3i findNextBuildTarget(@Nullable ConstructionOrderStore.Entry order, World world, BlockSelection prefab)
    {
        if (order == null || order.buildOrigin == null)
            return null;
        Vector3i origin = order.buildOrigin;

        int emptyId = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);

        try (var _ = DebugTiming.measure("ConstructorUtil.findNextBuildTarget", 50))
        {
            for (int[] b : getSortedBlocks(order, prefab))
            {
                int prefabBlockId = b[3];
                if (prefabBlockId == 0 || prefabBlockId == emptyId)
                    continue;

                int wx = b[0] + origin.x - prefab.getAnchorX();
                int wy = b[1] + origin.y - prefab.getAnchorY();
                int wz = b[2] + origin.z - prefab.getAnchorZ();

                int worldBlock = world.getBlock(wx, wy, wz);
                if (isBlockAcceptable(worldBlock, prefabBlockId))
                    continue; // already correct

                // Only return air slots -- non-air blocks need clearing first.
                if (worldBlock == 0)
                    return new Vector3i(wx, wy, wz);
            }
        }

        return null;
    }

    /** White = origin, red = needs clearing, yellow = needs filling, green = already correct. */
    public static void drawConstructionOrderOverlay(@Nullable ConstructionOrderStore.Entry order, @Nullable BlockSelection prefab, World world)
    {
        if (order == null || order.buildOrigin == null || prefab == null)
            return;
        Vector3i origin = order.buildOrigin;
        float drawTime = 2.0f;
        int emptyId = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);

        DebugUtils.addCube(world, origin.x + 0.5, origin.y + 0.5, origin.z + 0.5, DebugUtils.COLOR_WHITE, 1.4, drawTime);

        prefab.forEachBlock((lx, ly, lz, block) -> {
            int prefabBlockId = block.blockId();
            boolean isAir = (prefabBlockId == 0 || prefabBlockId == emptyId);

            int wx = lx + origin.x - prefab.getAnchorX();
            int wy = ly + origin.y - prefab.getAnchorY();
            int wz = lz + origin.z - prefab.getAnchorZ();

            int worldBlock = world.getBlock(wx, wy, wz);

            Vector3f color;
            if (isAir ? worldBlock != 0 : (worldBlock != 0 && !isBlockAcceptable(worldBlock, prefabBlockId)))
            {
                color = DebugUtils.COLOR_RED;
                DebugUtils.addCube(world, wx + 0.5, wy + 0.5, wz + 0.5, color, 1.1, drawTime);
            }
            // else if (!isAir && worldBlock == 0)
            // {
            //     color = DebugUtils.COLOR_YELLOW;
            // }
            // else
            // {
            //     color = DebugUtils.COLOR_LIME;
            // }
        });
    }

    /** Returns the block type key for the prefab position, or {@code null} if outside the footprint. */
    @Nullable public static String getDesiredBlockKey(@Nullable ConstructionOrderStore.Entry order, BlockSelection prefab, int wx, int wy, int wz)
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

    /**
     * Iterates the prefab top-down, finds the first block that needs clearing and
     * can be claimed, claims it, and returns its world position. Returns {@code null} if no
     * claimable clearing target exists (all done or all already claimed by other colonists).
     *
     * <p>Must be called on the world thread (e.g. inside a {@code world.execute()} callback).
     * The find and claim are atomic so two colonists cannot race to claim the same position.
     */
    @Nullable
    public static Vector3i
    claimNextClearingTarget(@Nullable ConstructionOrderStore.Entry order, @Nonnull World world, @Nullable BlockSelection prefab, @Nonnull UUID colonistUuid)
    {
        if (order == null || order.buildOrigin == null || prefab == null)
            return null;
        Vector3i origin = order.buildOrigin;
        int emptyId = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);

        try (var _ = DebugTiming.measure("ConstructorUtil.claimNextClearingTarget", 50))
        {
            // Scan top-down so floating blocks are claimed and cleared before their supports.
            List<int[]> blocks = getSortedBlocks(order, prefab);
            for (int i = blocks.size() - 1; i >= 0; i--)
            {
                int[] b = blocks.get(i);
                int prefabBlockId = b[3];
                boolean isAir = (prefabBlockId == 0 || prefabBlockId == emptyId);
                int wx = b[0] + origin.x - prefab.getAnchorX();
                int wy = b[1] + origin.y - prefab.getAnchorY();
                int wz = b[2] + origin.z - prefab.getAnchorZ();
                int worldBlock = world.getBlock(wx, wy, wz);

                boolean needsClear = isAir ? (worldBlock != 0) : (worldBlock != 0 && !isBlockAcceptable(worldBlock, prefabBlockId));
                if (!needsClear)
                    continue;

                Vector3i pos = new Vector3i(wx, wy, wz);
                if (ClaimBlockUtil.claimBlock(world, pos, colonistUuid, "Clear"))
                    return pos;
            }
        }
        return null;
    }

    /**
     * Iterates the prefab, tries to claim each block that still needs placing, and returns
     * up to {@code maxCount} successfully claimed world positions (in prefab scan order).
     *
     * <p>Must be called on the world thread (e.g. inside a {@code world.execute()} callback).
     */
    @Nonnull
    public static List<Vector3i> collectAndClaimBuildTargets(@Nullable ConstructionOrderStore.Entry order,
                                                             @Nonnull World world,
                                                             @Nullable BlockSelection prefab,
                                                             @Nonnull UUID colonistUuid,
                                                             int maxCount)
    {
        if (order == null || order.buildOrigin == null || prefab == null || maxCount <= 0)
        {
            return Collections.emptyList();
        }

        Vector3i origin = order.buildOrigin;
        int emptyId = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);
        List<Vector3i> result = new ArrayList<>();

        for (int[] b : getSortedBlocks(order, prefab))
        {
            if (result.size() >= maxCount)
                break;

            int prefabBlockId = b[3];
            if (prefabBlockId == 0 || prefabBlockId == emptyId)
                continue;

            int wx = b[0] + origin.x - prefab.getAnchorX();
            int wy = b[1] + origin.y - prefab.getAnchorY();
            int wz = b[2] + origin.z - prefab.getAnchorZ();

            int worldBlock = world.getBlock(wx, wy, wz);
            if (isBlockAcceptable(worldBlock, prefabBlockId))
                continue; // already correct
            if (worldBlock != 0)
                continue; // block in the way -- needs to be cleared first

            Vector3i pos = new Vector3i(wx, wy, wz);
            if (ClaimBlockUtil.claimBlock(world, pos, colonistUuid, "Build"))
            {
                result.add(pos);
            }
        }

        return result;
    }

    /**
     * Computes the item stacks needed to place all blocks in {@code buildTargets},
     * grouped by item ID. Returns an empty list if no items can be determined.
     *
     * <p>Safe to call on any thread (pure read-only asset lookups).
     */
    @Nonnull
    public static List<ItemStack>
    getRequiredItemStacks(@Nullable ConstructionOrderStore.Entry order, @Nullable BlockSelection prefab, @Nonnull List<Vector3i> buildTargets)
    {
        if (order == null || prefab == null || buildTargets.isEmpty())
            return Collections.emptyList();

        Map<String, Integer> needed = new LinkedHashMap<>();
        for (Vector3i pos : buildTargets)
        {
            String blockKey = getDesiredBlockKey(order, prefab, pos.x, pos.y, pos.z);
            if (blockKey == null || blockKey.isEmpty() || EMPTY_BLOCK_KEY.equals(blockKey))
                continue;
            BlockType blockType = BlockType.getAssetMap().getAsset(blockKey);
            if (blockType == null)
                continue;
            Item item = blockType.getItem();
            if (item == null)
                continue;
            needed.merge(item.getId(), 1, Integer::sum);
        }

        List<ItemStack> result = new ArrayList<>(needed.size());
        for (Map.Entry<String, Integer> entry : needed.entrySet())
        {
            result.add(new ItemStack(entry.getKey(), entry.getValue()));
        }
        return result;
    }
}
