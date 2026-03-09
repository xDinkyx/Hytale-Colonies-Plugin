package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hytalecolonies.components.jobs.JobType;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.utils.BlockStateInfoUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.buildertool.config.BlockTypeListAsset;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.ints.IntSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Periodically scans a configurable chunk radius around each Woodsman
 * workstation
 * for TreeWood blocks. Results are logged for now; they will feed the
 * harvestable-tree
 * registry used by woodcutter NPCs in a future step.
 */
public class TreeScannerSystem extends DelayedEntitySystem<ChunkStore> {

    private static final int SCAN_RADIUS_CHUNKS = 8;
    private static final String TREE_WOOD_LIST_ID = "TreeWood";

    private final Query<ChunkStore> query = Query.and(
            WorkStationComponent.getComponentType(),
            BlockModule.BlockStateInfo.getComponentType());

    /** Guards against overlapping scans. */
    // ToDo: Store per workstation.
    private volatile boolean isRunning = false;

    /**
     * Lazy-initialised cache — assets are not guaranteed loaded at construction
     * time.
     */
    private Set<String> treeWoodBlockKeys;

    public TreeScannerSystem() {
        super(10.0f); // Run once every 10 seconds
    }

    @Override
    public void tick(float dt, int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> chunkStore,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        if (isRunning) {
            HytaleColoniesPlugin.LOGGER.atInfo().log("[TreeScanner] Previous scan still running, skipping tick.");
            return;
        }

        WorkStationComponent workStation = archetypeChunk.getComponent(index, WorkStationComponent.getComponentType());
        assert workStation != null;

        if (workStation.getJobType() != JobType.Woodsman)
            return;

        BlockModule.BlockStateInfo blockStateInfo = archetypeChunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
        if (blockStateInfo == null)
            return;

        Vector3i workStationPos = new BlockStateInfoUtil().GetBlockWorldPosition(blockStateInfo, commandBuffer);

        isRunning = true; // Set running flag before starting the scan to prevent overlaps

        try {
            scanForTreeWoodBlocks(workStationPos, chunkStore);
        } finally {
            isRunning = false;
        }
    }

    private void scanForTreeWoodBlocks(Vector3i centerPos, Store<ChunkStore> chunkStore) {
        Set<String> treeWoodKeys = getTreeWoodBlockKeys();
        World world = chunkStore.getExternalData().getWorld();

        int centerChunkX = ChunkUtil.chunkCoordinate(centerPos.x);
        int centerChunkZ = ChunkUtil.chunkCoordinate(centerPos.z);

        List<Vector3i> foundBlocks = new ArrayList<>();

        // ToDo: We should probably do bounds checking.
        for (int cx = centerChunkX - SCAN_RADIUS_CHUNKS; cx <= centerChunkX + SCAN_RADIUS_CHUNKS; cx++) {
            for (int cz = centerChunkZ - SCAN_RADIUS_CHUNKS; cz <= centerChunkZ + SCAN_RADIUS_CHUNKS; cz++) {
                WorldChunk worldChunk = world.getChunkIfInMemory(ChunkUtil.indexChunk(cx, cz));
                if (worldChunk == null)
                    continue;

                BlockChunk blockChunk = worldChunk.getBlockChunk();
                if (blockChunk == null)
                    continue;

                // Quick pre-filter: skip chunks that contain no TreeWood blocks at all.
                if (!chunkContainsTreeWood(blockChunk, treeWoodKeys))
                    continue;

                scanChunkForTreeWood(cx, cz, blockChunk, treeWoodKeys, foundBlocks);
            }
        }

        HytaleColoniesPlugin.LOGGER.atInfo().log(
                "[TreeScanner] Found %d TreeWood blocks within %d chunk radius of workstation at %s.",
                foundBlocks.size(), SCAN_RADIUS_CHUNKS, centerPos);
    }

    /**
     * Uses the chunk's unique block ID set to quickly determine whether any
     * TreeWood block type is present before doing a full per-block iteration.
     */
    private boolean chunkContainsTreeWood(BlockChunk blockChunk, Set<String> treeWoodKeys) {
        IntSet blockIds = blockChunk.blocks();
        for (int blockId : blockIds) {
            BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
            if (blockType != null && treeWoodKeys.contains(blockType.getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Iterates every block in the chunk section-by-section and collects world
     * positions of all TreeWood blocks.
     */
    private void scanChunkForTreeWood(int chunkX, int chunkZ, BlockChunk blockChunk,
            Set<String> treeWoodKeys, List<Vector3i> foundBlocks) {
        BlockSection[] sections = blockChunk.getChunkSections();
        for (int sectionIdx = 0; sectionIdx < sections.length; sectionIdx++) {
            BlockSection section = sections[sectionIdx];
            if (section.isSolidAir())
                continue;

            int sectionBaseY = sectionIdx * 32;
            for (int localY = 0; localY < 32; localY++) {
                int worldY = sectionBaseY + localY;
                for (int localX = 0; localX < 32; localX++) {
                    for (int localZ = 0; localZ < 32; localZ++) {
                        int blockId = section.get(localX, worldY, localZ);
                        if (blockId == 0)
                            continue;

                        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
                        if (blockType == null || !treeWoodKeys.contains(blockType.getId()))
                            continue;

                        int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkX, localX);
                        int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkZ, localZ);
                        foundBlocks.add(new Vector3i(worldX, worldY, worldZ));
                    }
                }
            }
        }
    }

    /** Lazy-loads and caches the TreeWood block key set from the asset registry. */
    private Set<String> getTreeWoodBlockKeys() {
        if (treeWoodBlockKeys == null) {
            BlockTypeListAsset treeWoodList = BlockTypeListAsset.getAssetMap().getAsset(TREE_WOOD_LIST_ID);
            treeWoodBlockKeys = treeWoodList != null ? treeWoodList.getBlockTypeKeys() : Collections.emptySet();
        }
        return treeWoodBlockKeys;
    }

    @Override
    public @Nullable Query<ChunkStore> getQuery() {
        return query;
    }
}
