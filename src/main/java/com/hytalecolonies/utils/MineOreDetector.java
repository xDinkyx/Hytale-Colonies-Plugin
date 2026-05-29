package com.hytalecolonies.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.buildertool.config.BlockTypeListAsset;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;

/**
 * Detects ore blocks in the 1-block perimeter surrounding a cleared region and performs BFS flood-fill to collect connected ore veins.
 *
 * <p>
 * Uses the {@code Ores} block-type list from the server assets to identify ore blocks without hard-coding any block names.
 */
public final class MineOreDetector
{
    private static final String ORE_BLOCK_LIST_KEY = "Ores";
    private static final int MAX_VEIN_BLOCKS = 48;

    /** 6-connected face neighbours for BFS. */
    private static final int[][] FACE_NEIGHBOURS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static volatile Set<String> cachedOreKeys;

    private MineOreDetector() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Scans the 1-block shell around the axis-aligned bounding box defined by {@code min} (inclusive) and {@code max} (inclusive) for ore blocks. When an ore
     * is found it is expanded into its connected vein (BFS, capped at {@link #MAX_VEIN_BLOCKS}). The result is the full list of ore positions from the first
     * vein encountered, or an empty list if no ore is found.
     *
     * <p>
     * Must be called on the world thread.
     *
     * @param world the world to scan
     * @param min   minimum corner of the cleared region (inclusive)
     * @param max   maximum corner of the cleared region (inclusive)
     * @return ordered list of ore block positions forming the first detected vein, or an empty list if none are found
     */
    @Nonnull
    public static List<Vector3i> findFirstVeinAroundBox(@Nonnull World world, @Nonnull Vector3i min, @Nonnull Vector3i max)
    {
        Set<String> oreKeys = getOreKeys();
        if (oreKeys.isEmpty())
        {
            DebugLog.fine(DebugCategory.MINER_JOB, "[MineOreDetector] Ore key list empty -- skipping scan.");
            return Collections.emptyList();
        }

        // Walk the 1-block shell outside the AABB on each face.
        int x1 = min.x - 1, y1 = min.y - 1, z1 = min.z - 1;
        int x2 = max.x + 1, y2 = max.y + 1, z2 = max.z + 1;

        for (int x = x1; x <= x2; x++)
        {
            for (int y = y1; y <= y2; y++)
            {
                for (int z = z1; z <= z2; z++)
                {
                    // Only check the shell (at least one coordinate is at min-1 or max+1).
                    boolean onShell = x == x1 || x == x2 || y == y1 || y == y2 || z == z1 || z == z2;
                    if (!onShell)
                        continue;

                    if (isOre(world, x, y, z, oreKeys))
                    {
                        List<Vector3i> vein = floodFillVein(world, new Vector3i(x, y, z), oreKeys);
                        if (!vein.isEmpty())
                        {
                            DebugLog.info(DebugCategory.MINER_JOB,
                                          "[MineOreDetector] Found ore vein of %d blocks starting at (%d,%d,%d).",
                                          vein.size(),
                                          x,
                                          y,
                                          z);
                            return vein;
                        }
                    }
                }
            }
        }

        DebugLog.fine(DebugCategory.MINER_JOB, "[MineOreDetector] No ore in perimeter of box %s..%s.", min, max);
        return Collections.emptyList();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /** BFS flood-fill from {@code seed}, collecting all connected ore blocks up to the cap. */
    @Nonnull
    private static List<Vector3i> floodFillVein(@Nonnull World world, @Nonnull Vector3i seed, @Nonnull Set<String> oreKeys)
    {
        List<Vector3i> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Deque<Vector3i> queue = new ArrayDeque<>();

        queue.add(seed);
        visited.add(seed.x + "," + seed.y + "," + seed.z);

        while (!queue.isEmpty() && result.size() < MAX_VEIN_BLOCKS)
        {
            Vector3i current = queue.poll();
            if (!isOre(world, current.x, current.y, current.z, oreKeys))
                continue;

            result.add(current);

            for (int[] d : FACE_NEIGHBOURS)
            {
                int nx = current.x + d[0];
                int ny = current.y + d[1];
                int nz = current.z + d[2];
                String key = nx + "," + ny + "," + nz;
                if (!visited.contains(key))
                {
                    visited.add(key);
                    queue.add(new Vector3i(nx, ny, nz));
                }
            }
        }

        return result;
    }

    private static boolean isOre(@Nonnull World world, int x, int y, int z, @Nonnull Set<String> oreKeys)
    {
        int blockId = world.getBlock(x, y, z);
        if (blockId == 0)
            return false;
        BlockType bt = BlockType.getAssetMap().getAsset(blockId);
        return bt != null && oreKeys.contains(bt.getId());
    }

    /** Lazily loads and caches the ore block key set. Thread-safe via double-checked locking. */
    @Nonnull
    private static Set<String> getOreKeys()
    {
        if (cachedOreKeys == null)
        {
            synchronized (MineOreDetector.class)
            {
                if (cachedOreKeys == null)
                {
                    BlockTypeListAsset asset = BlockTypeListAsset.getAssetMap().getAsset(ORE_BLOCK_LIST_KEY);
                    cachedOreKeys = asset != null ? Collections.unmodifiableSet(new HashSet<>(asset.getBlockTypeKeys())) : Collections.emptySet();
                    DebugLog.fine(DebugCategory.MINER_JOB, "[MineOreDetector] Loaded %d ore block types.", cachedOreKeys.size());
                }
            }
        }
        return cachedOreKeys;
    }
}
