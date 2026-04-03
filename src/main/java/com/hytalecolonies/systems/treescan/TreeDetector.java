package com.hytalecolonies.systems.treescan;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.buildertool.config.BlockTypeListAsset;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

/**
 * Determines whether a candidate wood block is the base of a harvestable tree.
 *
 * <p><b>Algorithm (simple vertical stack check):</b>
 * <ol>
 *   <li>The block directly below the candidate must be a Soil type, indicating
 *       the trunk grew from ground level rather than being mid-structure.</li>
 *   <li>The candidate and at least {@value #MIN_STACK_HEIGHT} blocks directly above
 *       it must all be TreeWood, confirming a trunk of meaningful height.</li>
 * </ol>
 *
 * <p>This intentionally excludes player-placed constructions because:
 * <ul>
 *   <li>Player-built log walls/cabins rarely have a soil block directly beneath a
 *       bottom log, and</li>
 *   <li>The stack check means a single floating log or a 2-block-high decorative
 *       pillar is ignored.</li>
 * </ul>
 *
 * <p>Note: a future upgrade should also check {@code PlacedByInteractionComponent}
 * absence to catch edge cases where a player stacks wood on natural-looking soil.
 */
public class TreeDetector implements ITreeDetector {

    /**
     * Minimum number of consecutive vertical TreeWood blocks (including the
     * candidate itself) needed to qualify the position as a tree base.
     */
    private static final int MIN_STACK_HEIGHT = 3;

    private static final String TREE_WOOD_LIST_ID = "TreeWood";
    private static final String SOILS_LIST_ID = "Soils";

    // Lazy-loaded caches -- assets are not guaranteed to be ready at construction time.
    private Set<String> treeWoodKeys;
    private Set<String> soilKeys;

    /**
     * Returns {@code true} if the block at {@code candidatePos} is the base of a
     * natural, harvestable tree.
     *
     * @param candidatePos world position of the lowest wood block to test
     * @param world        the world in which to look up adjacent blocks
     */
    public boolean isTreeBase(Vector3i candidatePos, World world) {
        // The block directly below must be soil.
        String belowKey = getBlockKey(world, candidatePos.x, candidatePos.y - 1, candidatePos.z);
        if (belowKey == null || !getSoilKeys().contains(belowKey)) {
            return false;
        }

        // The candidate and the blocks above it must form a wood stack of minimum height.
        Set<String> woodKeys = getTreeWoodKeys();
        for (int i = 0; i < MIN_STACK_HEIGHT; i++) {
            String key = getBlockKey(world, candidatePos.x, candidatePos.y + i, candidatePos.z);
            if (key == null || !woodKeys.contains(key)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Reads the block type key at a given world position.
     *
     * @return the block's string ID, or {@code null} if the position is out of
     *         bounds, the chunk is not loaded, or the block is air
     */
    @Nullable
    public static String getBlockKey(World world, int wx, int wy, int wz) {
        if (wy < 0 || wy >= ChunkUtil.HEIGHT) return null;

        int chunkX = ChunkUtil.chunkCoordinate(wx);
        int chunkZ = ChunkUtil.chunkCoordinate(wz);
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunk(chunkX, chunkZ));
        if (chunk == null) return null;

        BlockChunk blockChunk = chunk.getBlockChunk();
        if (blockChunk == null) return null;

        // Mask to local 0-31 range -- works correctly for negative world coordinates.
        int localX = wx & ChunkUtil.SIZE_MASK;
        int localZ = wz & ChunkUtil.SIZE_MASK;

        BlockSection section = blockChunk.getSectionAtBlockY(wy);
        int blockId = section.get(localX, wy, localZ);
        if (blockId == 0) return null;

        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        return blockType != null ? blockType.getId() : null;
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

    private Set<String> getSoilKeys() {
        if (soilKeys == null) {
            BlockTypeListAsset asset = BlockTypeListAsset.getAssetMap().getAsset(SOILS_LIST_ID);
            soilKeys = asset != null ? asset.getBlockTypeKeys() : Collections.emptySet();
        }
        return soilKeys;
    }
}
