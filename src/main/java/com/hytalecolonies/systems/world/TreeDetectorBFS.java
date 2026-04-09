package com.hytalecolonies.systems.world;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.buildertool.config.BlockTypeListAsset;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Determines whether a candidate wood block is the base of a harvestable tree
 * using a 6-connected BFS flood-fill over the wood component.
 *
 * <p><b>Algorithm:</b>
 * <ol>
 *   <li>BFS expands through all face-connected TreeWood blocks reachable from
 *       {@code candidatePos}, up to {@value #MAX_WOOD_VISITED} blocks.
 *       Exceeding the cap signals a player-built structure or just a really cool old tree (rejected).</li>
 *   <li>For each visited wood block, the neighbours are tested for
 *       TreeLeaves to build a deduplicated leaf count.</li>
 *   <li>The component is accepted as a tree if it meets the minimum wood and
 *       leaf thresholds.</li>
 *   <li>The BFS naturally identifies the {@code lowestBase} -- the lowest y
 *       position in the connected component -- which is not necessarily the
 *       block the scanner handed in.</li>
 * </ol>
 */
public class TreeDetectorBFS implements ITreeDetector {

    /**
     * Abort BFS if more than this many connected wood blocks are found.
     * Wide-base trees (2Ã—2 or 3Ã—3 trunks) with branches can easily exceed 50 blocks, so a
     * generous cap is needed. Player-built structures are distinguished by the leaf check
     * (MIN_LEAF_BLOCKS) rather than by wood count alone.
     */
    private static final int MAX_WOOD_VISITED = 300;
    private static final int MIN_WOOD_BLOCKS  = 4;
    private static final int MIN_LEAF_BLOCKS  = 8;

    private static final String TREE_WOOD_LIST_ID   = "TreeWood";
    private static final String TREE_LEAVES_LIST_ID = "TreeLeaves";

    // 6 face-connected directions (Â±X, Â±Y, Â±Z)
    private static final int[][] NEIGHBORS = {
        { 1, 0, 0}, {-1, 0, 0},
        { 0, 1, 0}, { 0,-1, 0},
        { 0, 0, 1}, { 0, 0,-1}
    };

    // Lazy-loaded caches.
    private Set<String> treeWoodKeys;
    private Set<String> treeLeafKeys;

    @Override
    public boolean isTreeBase(Vector3i candidatePos, World world) {
        return evaluate(candidatePos, world).isTree();
    }

    /**
     * Runs the full BFS and returns a {@link TreeCandidate} with the result
     * and diagnostic counts. Useful for debug/logging beyond a boolean answer.
     */
    @Nonnull
    public TreeCandidate evaluate(Vector3i start, World world) {
        Set<String> woodKeys = getTreeWoodKeys();
        Set<String> leafKeys = getTreeLeafKeys();

        Set<Long>       visitedWood = new HashSet<>();
        Set<Long>       visitedLeaf = new HashSet<>();
        Deque<Vector3i> queue       = new ArrayDeque<>();

        visitedWood.add(pack(start));
        queue.add(start);

        // lowestBase   -- minimum Y across all wood blocks (for consumedBlocks bookkeeping).
        // lowestTrunkBase -- minimum Y restricted to trunk/roots blocks (never a branch).
        Vector3i lowestBase      = start;
        Vector3i lowestTrunkBase = null;

        String startKey = TreeDetector.getBlockKey(world, start.x, start.y, start.z);
        if (startKey != null && !isBranchBlock(startKey)) {
            lowestTrunkBase = start;
        }

        // BFS is restricted to the species of the starting block so that adjacent
        // trees of different types (e.g. Beech touching Oak) are never merged into
        // one component.
        String startSpecies = startKey != null ? extractSpecies(startKey) : null;

        while (!queue.isEmpty() && visitedWood.size() <= MAX_WOOD_VISITED) {
            Vector3i current = queue.poll();

            if (isLower(current, lowestBase)) lowestBase = current;

            // Look up the current block's key so we know whether we are in a branch.
            String currentKey = TreeDetector.getBlockKey(world, current.x, current.y, current.z);
            boolean currentIsBranch = currentKey != null && isBranchBlock(currentKey);

            if (!currentIsBranch && (lowestTrunkBase == null || isLower(current, lowestTrunkBase))) {
                lowestTrunkBase = current;
            }

            for (int[] d : NEIGHBORS) {
                int nx = current.x + d[0];
                int ny = current.y + d[1];
                int nz = current.z + d[2];

                String key = TreeDetector.getBlockKey(world, nx, ny, nz);
                if (key == null) continue;

                long packed = pack(nx, ny, nz);
                if (woodKeys.contains(key)) {
                    // Reject blocks of a different tree species (e.g. Beech root touching Oak root).
                    if (startSpecies != null && !extractSpecies(key).equals(startSpecies)) continue;

                    // Block branch->trunk expansion: branches can only spread to other branches,
                    // never back into a trunk. This prevents BFS from crossing into a neighbouring
                    // tree's trunk via touching branch blocks.
                    if (currentIsBranch && !isBranchBlock(key)) continue;

                    if (visitedWood.add(packed)) {
                        queue.add(new Vector3i(nx, ny, nz));
                    }
                } else if (leafKeys.contains(key)) {
                    visitedLeaf.add(packed); // deduplicated -- a leaf adjacent to 3 wood blocks counts once
                }
            }
        }

        boolean hitCap  = visitedWood.size() > MAX_WOOD_VISITED;
        boolean isTree  = !hitCap
                && visitedWood.size() >= MIN_WOOD_BLOCKS
                && visitedLeaf.size() >= MIN_LEAF_BLOCKS;

        // Report the lowest trunk/roots block as the tree base.  Fall back to the
        // overall lowest wood block only if no trunk was encountered (safety net).
        Vector3i reportedBase = (lowestTrunkBase != null) ? lowestTrunkBase : lowestBase;

        return new TreeCandidate(isTree, reportedBase, visitedWood.size(), visitedLeaf.size(),
                Collections.unmodifiableSet(visitedWood));
    }

    /**
     * Returns {@code true} if the block key belongs to a branch block
     * (i.e. contains {@code "_Branch_"} -- e.g. {@code Wood_Oak_Branch_Short}).
     */
    private static boolean isBranchBlock(String key) {
        return key.contains("_Branch_");
    }

    /**
     * Extracts the species prefix from a TreeWood block key.
     *
     * <p>Block keys follow the pattern {@code Wood_{Species}_{Type}}, where known
     * types are {@code Trunk}, {@code Trunk_Full}, {@code Roots},
     * {@code Branch_Short}, {@code Branch_Long}, and {@code Branch_Corner}.
     * For example, {@code Wood_Oak_Trunk} -> {@code "Wood_Oak"} and
     * {@code Wood_Wisteria_Wild_Branch_Short} -> {@code "Wood_Wisteria_Wild"}.
     *
     * <p>Note: {@code _Trunk_Full} must be checked before {@code _Trunk} because
     * the shorter suffix is a substring of the longer one.
     *
     * @param blockKey a non-null TreeWood block key
     * @return the species prefix, or {@code blockKey} unchanged if no known suffix matched
     */
    private static String extractSpecies(String blockKey) {
        // Order matters: more-specific suffixes before their substrings.
        if (blockKey.endsWith("_Trunk_Full"))    return blockKey.substring(0, blockKey.length() - "_Trunk_Full".length());
        if (blockKey.endsWith("_Trunk"))         return blockKey.substring(0, blockKey.length() - "_Trunk".length());
        if (blockKey.endsWith("_Roots"))         return blockKey.substring(0, blockKey.length() - "_Roots".length());
        if (blockKey.endsWith("_Branch_Short"))  return blockKey.substring(0, blockKey.length() - "_Branch_Short".length());
        if (blockKey.endsWith("_Branch_Long"))   return blockKey.substring(0, blockKey.length() - "_Branch_Long".length());
        if (blockKey.endsWith("_Branch_Corner")) return blockKey.substring(0, blockKey.length() - "_Branch_Corner".length());
        return blockKey; // unknown suffix -- treat the full key as the species
    }

    /**
     * Canonical ordering for the lowest-base tiebreaker: Y ascending, then X ascending,
     * then Z ascending. Using this consistently ensures that a flat multi-block base (all
     * blocks at the same Y) always resolves to the same corner regardless of BFS start
     * order, preventing duplicate block entities across scan invocations.
     */
    private static boolean isLower(Vector3i candidate, Vector3i current) {
        if (candidate.y != current.y) return candidate.y < current.y;
        if (candidate.x != current.x) return candidate.x < current.x;
        return candidate.z < current.z;
    }

    // -------------------------------------------------------------------------
    // Pack (x, y, z) into a single long for visited-set membership tests.
    // Uses 21-bit fields: y uses bits 0-20, z uses 21-41, x uses 42-62.
    // -------------------------------------------------------------------------

    public static long pack(Vector3i v) {
        return pack(v.x, v.y, v.z);
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42)
             | ((long) (z & 0x1FFFFF) << 21)
             |  (long) (y & 0x1FFFFF);
    }

    /** Unpacks a position packed by {@link #pack(int, int, int)} back to a {@link Vector3i}. */
    public static Vector3i unpack(long packed) {
        // Each axis occupies 21 bits (bits 0-20 = y, 21-41 = z, 42-62 = x; bit 63 unused).
        // Sign-extend each field by shifting its MSB to bit 63, then arithmetic-shifting back.
        int y = (int) ((packed << 43) >> 43); // bit 20 -> bit 63, then back
        int z = (int) ((packed << 22) >> 43); // bit 41 -> bit 63, then back
        int x = (int) ((packed <<  1) >> 43); // bit 62 -> bit 63, then back
        return new Vector3i(x, y, z);
    }

    // -------------------------------------------------------------------------
    // Asset key set helpers
    // -------------------------------------------------------------------------

    private Set<String> getTreeWoodKeys() {
        if (treeWoodKeys == null) {
            BlockTypeListAsset asset = BlockTypeListAsset.getAssetMap().getAsset(TREE_WOOD_LIST_ID);
            treeWoodKeys = asset != null ? asset.getBlockTypeKeys() : Collections.emptySet();
        }
        return treeWoodKeys;
    }

    private Set<String> getTreeLeafKeys() {
        if (treeLeafKeys == null) {
            BlockTypeListAsset asset = BlockTypeListAsset.getAssetMap().getAsset(TREE_LEAVES_LIST_ID);
            treeLeafKeys = asset != null ? asset.getBlockTypeKeys() : Collections.emptySet();
        }
        return treeLeafKeys;
    }

    // -------------------------------------------------------------------------
    // Result record
    // -------------------------------------------------------------------------

    /**
     * Full result of a BFS tree evaluation.
     *
     * @param isTree             whether the component qualifies as a harvestable tree
     * @param base               lowest wood position found in the connected component
     * @param woodCount          number of connected wood blocks visited
     * @param leafCount          number of leaf blocks inthe component
     * @param visitedWoodPacked  packed positions of every wood block visited -- used by the
     *                           scanner to skip already-processed blocks
     */
    public record TreeCandidate(boolean isTree, Vector3i base, int woodCount, int leafCount,
                                Set<Long> visitedWoodPacked) {}
}
