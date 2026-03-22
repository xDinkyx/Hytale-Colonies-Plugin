package com.hytalecolonies.systems.treescan;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.debug.DebugTiming;
import com.hytalecolonies.systems.jobs.WorkstationInitSystem;
import com.hytalecolonies.components.jobs.JobType;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.world.HarvestableTreeComponent;
import com.hytalecolonies.utils.BlockStateInfoUtil;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.buildertool.config.BlockTypeListAsset;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Periodically scans a configurable chunk radius around each Woodsman
 * workstation for new TreeWood blocks (e.g. saplings that have grown).
 * The initial full scan is handled by {@link WorkstationInitSystem} on
 * workstation load. Block-break / block-place updates are handled reactively
 * by {@link TreeBlockChangeEventSystem}. This system therefore only needs to
 * run infrequently to catch world-generated growth events that have no
 * dedicated event hook.
 */
public class TreeScannerSystem extends DelayedEntitySystem<ChunkStore> {

    static final int SCAN_RADIUS_CHUNKS = 1;
    public static final float SCAN_DELAY_SECONDS = 60.0f;
    private static final String TREE_WOOD_LIST_ID = "TreeWood";

    private final Query<ChunkStore> query = Query.and(
            WorkStationComponent.getComponentType(),
            BlockModule.BlockStateInfo.getComponentType());

    // Swap to new TreeDetectorBFS() to use the flood-fill algorithm instead.
    private final ITreeDetector treeDetector = new TreeDetectorBFS();

    /**
     * Lazy-initialised cache — assets are not guaranteed loaded at construction
     * time.
     */
    private Set<String> treeWoodBlockKeys;

    /**
     * Per-chunk wood block count from the last periodic scan.
     * Key: chunk index ({@link ChunkUtil#indexChunk}).
     * Value: total wood-block count across all sections.
     * Chunks whose count matches the cached value are skipped during the
     * periodic scan so unchanged ticks are near-free regardless of world size.
     */
    private final Map<Long, Integer> chunkWoodCountCache = new HashMap<>();

    public TreeScannerSystem() {
        super(SCAN_DELAY_SECONDS); // Run every 60 s — count-comparison ensures BFS only fires for chunks whose wood count changed
    }

    @Override
    public void tick(float dt, int index,
            @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
            @Nonnull Store<ChunkStore> chunkStore,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        WorkStationComponent workStation = archetypeChunk.getComponent(index, WorkStationComponent.getComponentType());
        assert workStation != null;

        if (workStation.getJobType() != JobType.Woodsman)
            return;

        BlockModule.BlockStateInfo blockStateInfo = archetypeChunk.getComponent(index,
                BlockModule.BlockStateInfo.getComponentType());
        if (blockStateInfo == null)
            return;

        Vector3i workStationPos = new BlockStateInfoUtil().GetBlockWorldPosition(blockStateInfo, commandBuffer);

        DebugLog.fine(DebugCategory.TREE_SCANNER, "[TreeScanner Periodic] Growth scan around workstation at %s.", workStationPos);
        try (var t = DebugTiming.measure("TreeScanner.tick@" + workStationPos, 500)) {
            scanForTreeWoodBlocks(workStationPos, chunkStore, commandBuffer);
        }
    }

    /** Orchestrates a full scan cycle: collect candidates → detect trees → register → debug draw. */
    public void scanForTreeWoodBlocks(Vector3i centerPos, Store<ChunkStore> chunkStore,
            CommandBuffer<ChunkStore> commandBuffer) {
        World world = chunkStore.getExternalData().getWorld();
        Set<String> treeWoodKeys = getTreeWoodBlockKeys();

        List<Vector3i> segmentBottoms;
        try (var t = DebugTiming.measure("TreeScanner.collectSegmentBottoms", 100)) {
            segmentBottoms = collectSegmentBottoms(world, centerPos, treeWoodKeys);
        }

        List<TreeDetectorBFS.TreeCandidate> confirmedTrees;
        try (var t = DebugTiming.measure("TreeScanner.detectTrees(" + segmentBottoms.size() + " candidates)", 200)) {
            confirmedTrees = detectTrees(segmentBottoms, world);
        }

        DebugLog.fine(DebugCategory.TREE_SCANNER,
                "[TreeScanner] Found %d trees within %d chunk radius of workstation at %s.",
                confirmedTrees.size(), SCAN_RADIUS_CHUNKS, centerPos);

        if (HytaleColoniesPlugin.getInstance().getDebugConfig().get().isDrawTreeDetection()) {
            debugDrawTrees(world, confirmedTrees);
        }
        registerHarvestableTrees(confirmedTrees, world, chunkStore, commandBuffer);
    }

    /**
     * Scans the chunk grid in {@value #SCAN_RADIUS_CHUNKS}-chunk radius around
     * {@code centerPos} and returns every trunk segment bottom it finds.
     * A segment bottom is a trunk block with a non-wood block directly below it.
     */
    // ToDo: We should probably do bounds checking.
    private List<Vector3i> collectSegmentBottoms(World world, Vector3i centerPos, Set<String> treeWoodKeys) {
        int centerChunkX = ChunkUtil.chunkCoordinate(centerPos.x);
        int centerChunkZ = ChunkUtil.chunkCoordinate(centerPos.z);
        List<Vector3i> segmentBottoms = new ArrayList<>();

        for (int cx = centerChunkX - SCAN_RADIUS_CHUNKS; cx <= centerChunkX + SCAN_RADIUS_CHUNKS; cx++) {
            for (int cz = centerChunkZ - SCAN_RADIUS_CHUNKS; cz <= centerChunkZ + SCAN_RADIUS_CHUNKS; cz++) {
                WorldChunk worldChunk = world.getChunkIfInMemory(ChunkUtil.indexChunk(cx, cz));
                if (worldChunk == null)
                    continue;

                BlockChunk blockChunk = worldChunk.getBlockChunk();
                if (blockChunk == null)
                    continue;

                if (!chunkContainsTreeWood(blockChunk, treeWoodKeys))
                    continue;

                scanChunkForTreeWood(cx, cz, blockChunk, treeWoodKeys, segmentBottoms);
            }
        }
        return segmentBottoms;
    }

    /**
     * Runs the tree detector over each segment bottom, deduplicating via
     * {@code consumedBlocks}, and returns only the confirmed trees.
     */
    private List<TreeDetectorBFS.TreeCandidate> detectTrees(List<Vector3i> segmentBottoms, World world) {
        Set<Long> consumedBlocks = new HashSet<>();
        List<TreeDetectorBFS.TreeCandidate> confirmedTrees = new ArrayList<>();

        for (Vector3i candidate : segmentBottoms) {
            if (treeDetector instanceof TreeDetectorBFS bfsDetector) {
                if (consumedBlocks.contains(TreeDetectorBFS.pack(candidate)))
                    continue;

                TreeDetectorBFS.TreeCandidate result = bfsDetector.evaluate(candidate, world);
                consumedBlocks.addAll(result.visitedWoodPacked());
                if (result.isTree()) {
                    confirmedTrees.add(result);
                }
            } else {
                if (treeDetector.isTreeBase(candidate, world)) {
                    confirmedTrees.add(new TreeDetectorBFS.TreeCandidate(
                            true, candidate, 0, 0, Collections.emptySet()));
                }
            }
        }
        return confirmedTrees;
    }

    /**
     * Iterates confirmed trees, calling {@link #ensureHarvestableBlockEntity} for
     * each one, then logs a summary.
     */
    private void registerHarvestableTrees(
            List<TreeDetectorBFS.TreeCandidate> confirmedTrees, World world,
            Store<ChunkStore> chunkStore, CommandBuffer<ChunkStore> commandBuffer) {
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed  = 0;

        for (TreeDetectorBFS.TreeCandidate tree : confirmedTrees) {
            int result = ensureHarvestableBlockEntity(tree, world, chunkStore, commandBuffer);
            if      (result > 0) created++;
            else if (result == 0) updated++;
            else if (result == -1) skipped++;
            else failed++;
        }

        DebugLog.fine(DebugCategory.TREE_SCANNER,
                "[TreeScanner] HarvestableTree registration — created: %d, updated: %d, already registered: %d, failed: %d.",
                created, updated, skipped, failed);
    }

    /**
     * Ensures a {@link HarvestableTreeComponent} exists on the block entity at the
     * tree's base position, creating the block entity if necessary.
     *
     * <p>When {@code commandBuffer} is non-null the entity creation / component
     * add is deferred through it (safe inside an ECS tick).  When it is
     * {@code null} the store is mutated directly — only call with a {@code null}
     * buffer from a {@code world.execute()} callback on the world thread.
     *
     * @return {@code 1}  — new block entity created with component<br>
     *         {@code 0}  — existing block entity updated with component<br>
     *         {@code -1} — component already present, nothing changed<br>
     *         {@code -2} — failed (missing block type or chunk)
     */
    int ensureHarvestableBlockEntity(
            TreeDetectorBFS.TreeCandidate tree,
            World world,
            Store<ChunkStore> chunkStore,
            @Nullable CommandBuffer<ChunkStore> commandBuffer) {
        Vector3i base = tree.base();

        int blockId = world.getBlock(base);
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) {
            DebugLog.warning(DebugCategory.TREE_SCANNER,
                    "[TreeScanner] No block type found at tree base %s — skipping registration.", base);
            return -2;
        }

        WorldChunk baseChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(base.x, base.z));
        if (baseChunk == null) {
            DebugLog.fine(DebugCategory.TREE_SCANNER,
                    "[TreeScanner] Chunk not in memory for tree base %s — skipping registration.", base);
            return -2;
        }

        Ref<ChunkStore> chunkRef = baseChunk.getReference();
        BlockComponentChunk blockComponentChunk = chunkStore.getComponent(
                chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null) {
            DebugLog.warning(DebugCategory.TREE_SCANNER,
                    "[TreeScanner] No BlockComponentChunk at tree base %s — skipping registration.", base);
            return -2;
        }

        int blockIndex = ChunkUtil.indexBlockInColumn(base.x, base.y, base.z);
        Ref<ChunkStore> existingRef = blockComponentChunk.getEntityReference(blockIndex);

        if (existingRef != null && existingRef.isValid()) {
            if (chunkStore.getComponent(existingRef, HarvestableTreeComponent.getComponentType()) != null) {
                DebugLog.fine(DebugCategory.TREE_SCANNER,
                        "[TreeScanner] Tree at %s already registered — skipping.", base);
                return -1;
            }
            // Block entity exists (e.g. from another plugin) but lacks our component — add it.
            HarvestableTreeComponent newComp = new HarvestableTreeComponent(blockType.getId(), tree.woodCount(), base);
            if (commandBuffer != null) {
                commandBuffer.addComponent(existingRef, HarvestableTreeComponent.getComponentType(), newComp);
            } else {
                chunkStore.putComponent(existingRef, HarvestableTreeComponent.getComponentType(), newComp);
            }
            DebugLog.info(DebugCategory.TREE_SCANNER,
                    "[TreeScanner] Added HarvestableTreeComponent to existing block entity at %s (%s, %d wood blocks).",
                    base, blockType.getId(), tree.woodCount());
            return 0;
        }

        // Plain blocks have no block entity — create one now.
        // BlockStateInfo is the mandatory anchor; BlockStateInfoRefSystem wires the
        // new Ref<ChunkStore> into BlockComponentChunk on AddReason.SPAWN automatically.
        Holder<ChunkStore> holder = ChunkStore.REGISTRY.newHolder();
        holder.putComponent(BlockModule.BlockStateInfo.getComponentType(),
                new BlockModule.BlockStateInfo(blockIndex, chunkRef));
        holder.putComponent(HarvestableTreeComponent.getComponentType(),
                new HarvestableTreeComponent(blockType.getId(), tree.woodCount(), base));

        if (commandBuffer != null) {
            commandBuffer.addEntity(holder, AddReason.SPAWN);
        } else {
            chunkStore.addEntity(holder, AddReason.SPAWN);
        }
        DebugLog.info(DebugCategory.TREE_SCANNER,
                "[TreeScanner] Created block entity with HarvestableTreeComponent at %s (%s, %d wood blocks).",
                base, blockType.getId(), tree.woodCount());
        return 1;
    }

    /** Draws all BFS wood blocks in a per-tree cycling colour with a white base marker. */
    private void debugDrawTrees(World world, List<TreeDetectorBFS.TreeCandidate> confirmedTrees) {
        int colorIdx = 0;
        for (TreeDetectorBFS.TreeCandidate tree : confirmedTrees) {
            var treeColor = DebugUtils.INDEXED_COLORS[colorIdx % DebugUtils.INDEXED_COLORS.length];
            colorIdx++;
            for (long packed : tree.visitedWoodPacked()) {
                Vector3i pos = TreeDetectorBFS.unpack(packed);
                Vector3d cubePos = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
                DebugUtils.addCube(world, cubePos, treeColor, 1.2, SCAN_DELAY_SECONDS);
            }
            Vector3d basePos = tree.base().toVector3d().add(0.5, 0.5, 0.5);
            DebugUtils.addCube(world, basePos, DebugUtils.COLOR_WHITE, 1.4, SCAN_DELAY_SECONDS);
        }
    }

    /**
     * Counts all tree-wood blocks in the chunk via the palette-based
     * {@link BlockChunk#blockCounts()} map — O(unique block types), not O(volume).
     */
    private int countWoodBlocksInChunk(BlockChunk blockChunk, Set<String> treeWoodKeys) {
        int total = 0;
        for (Int2IntMap.Entry entry : blockChunk.blockCounts().int2IntEntrySet()) {
            BlockType bt = BlockType.getAssetMap().getAsset(entry.getIntKey());
            if (bt != null && treeWoodKeys.contains(bt.getId())) {
                total += entry.getIntValue();
            }
        }
        return total;
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
     * Iterates every block in the chunk section-by-section and adds all
     * <em>segment bottoms</em> to {@code segmentBottoms}.
     *
     * <p>
     * A segment bottom is a TreeWood block whose block directly below is NOT
     * a TreeWood block. These are the independent trunk-start candidates.
     */
    private void scanChunkForTreeWood(
            int chunkX, int chunkZ,
            BlockChunk blockChunk,
            Set<String> treeWoodKeys,
            List<Vector3i> segmentBottoms) {
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

                        // Only trunk/roots blocks are valid segment bottoms — branches should never
                        // be a tree base candidate (a low-hanging branch would otherwise be mistaken
                        // for the trunk base).
                        if (blockType.getId().contains("_Branch_"))
                            continue;

                        // Check if the block directly below is also wood.
                        // If it is, this block is mid-trunk — not a segment bottom.
                        if (worldY > 0) {
                            BlockSection belowSection = blockChunk.getSectionAtBlockY(worldY - 1);
                            int belowId = belowSection.get(localX, worldY - 1, localZ);
                            if (belowId != 0) {
                                BlockType belowType = BlockType.getAssetMap().getAsset(belowId);
                                if (belowType != null && treeWoodKeys.contains(belowType.getId()))
                                    continue; // mid-trunk, skip
                            }
                        }

                        int worldX = ChunkUtil.worldCoordFromLocalCoord(chunkX, localX);
                        int worldZ = ChunkUtil.worldCoordFromLocalCoord(chunkZ, localZ);
                        segmentBottoms.add(new Vector3i(worldX, worldY, worldZ));
                    }
                }
            }
        }
    }

    /**
     * Called (from {@link TreeBlockChangeEventSystem}) after a tree-wood block at
     * {@code pos} has been removed from the world.
     *
     * <p>If there is remaining wood below {@code pos} the tree still exists but
     * has lost blocks; re-evaluate via BFS and update (or remove) the
     * {@link HarvestableTreeComponent} at the surviving base.  If there is no
     * wood below, the base itself was broken and its block entity (with our
     * component) is auto-removed by the server — nothing to do.
     *
     * <p>Must be called on the world thread (e.g. inside a {@code world.execute()}
     * callback).
     */
    void onTreeWoodBlockRemoved(Vector3i pos, World world, Store<ChunkStore> chunkStore) {
        if (pos.y <= 0) return;

        Set<String> woodKeys = getTreeWoodBlockKeys();

        // Check if there is any wood directly below the broken block.
        // If not, the base was at pos and its block entity is auto-cleaned by the engine.
        int belowBlockId = world.getBlock(new Vector3i(pos.x, pos.y - 1, pos.z));
        BlockType belowType = BlockType.getAssetMap().getAsset(belowBlockId);
        if (belowType == null || !woodKeys.contains(belowType.getId())) return;

        // Walk down to find the lowest surviving wood block (the surviving tree base).
        int baseY = pos.y - 1;
        while (baseY > 0) {
            int belowId = world.getBlock(new Vector3i(pos.x, baseY - 1, pos.z));
            BlockType btBelow = BlockType.getAssetMap().getAsset(belowId);
            if (btBelow == null || !woodKeys.contains(btBelow.getId())) break;
            baseY--;
        }

        Vector3i basePos = new Vector3i(pos.x, baseY, pos.z);
        WorldChunk baseChunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(basePos.x, basePos.z));
        if (baseChunk == null) return;

        Ref<ChunkStore> blockRef = baseChunk.getBlockComponentEntity(basePos.x, basePos.y, basePos.z);
        if (blockRef == null || !blockRef.isValid()) return;

        HarvestableTreeComponent existingComp = chunkStore.getComponent(blockRef,
                HarvestableTreeComponent.getComponentType());
        if (existingComp == null) return;

        // Re-evaluate whether the remaining structure is still a valid tree.
        TreeDetectorBFS.TreeCandidate result = new TreeDetectorBFS().evaluate(basePos, world);
        if (result.isTree()) {
            HarvestableTreeComponent updated = existingComp.clone();
            updated.setWoodCount(result.woodCount());
            chunkStore.putComponent(blockRef, HarvestableTreeComponent.getComponentType(), updated);
            DebugLog.info(DebugCategory.TREE_SCANNER,
                    "[TreeScanner] Tree at %s updated — %d wood blocks remaining after break.", basePos, result.woodCount());
        } else {
            chunkStore.removeComponent(blockRef, HarvestableTreeComponent.getComponentType());
            DebugLog.info(DebugCategory.TREE_SCANNER,
                    "[TreeScanner] Tree at %s removed — no valid structure remains.", basePos);
        }
    }

    /**
     * Called (from {@link TreeBlockChangeEventSystem}) after a tree-wood block has
     * been placed at {@code pos}.
     *
     * <p>Walks down to find the segment bottom of the new trunk, runs BFS, and
     * registers a new {@link HarvestableTreeComponent} if the structure qualifies
     * as a tree and is not already registered.
     *
     * <p>Must be called on the world thread (e.g. inside a {@code world.execute()}
     * callback).
     */
    void onTreeWoodBlockAdded(Vector3i pos, World world, Store<ChunkStore> chunkStore) {
        int blockId = world.getBlock(pos);
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) return;

        Set<String> woodKeys = getTreeWoodBlockKeys();
        if (!woodKeys.contains(blockType.getId())) return;
        // Branch blocks cannot be tree bases.
        if (blockType.getId().contains("_Branch_")) return;

        // Walk down to find the segment bottom — the lowest connected wood block.
        int baseY = pos.y;
        while (baseY > 0) {
            int belowId = world.getBlock(new Vector3i(pos.x, baseY - 1, pos.z));
            BlockType btBelow = BlockType.getAssetMap().getAsset(belowId);
            if (btBelow == null || !woodKeys.contains(btBelow.getId())) break;
            baseY--;
        }

        Vector3i segmentBottom = new Vector3i(pos.x, baseY, pos.z);
        TreeDetectorBFS.TreeCandidate result = new TreeDetectorBFS().evaluate(segmentBottom, world);
        if (!result.isTree()) return;

        ensureHarvestableBlockEntity(result, world, chunkStore, null /* direct store mutation */);
    }

    /**
     * Marks the chunk containing the given world position as dirty so the next
     * periodic scan will re-evaluate it regardless of whether its current wood
     * count differs.  Should be called by event handlers after they have already
     * handled the immediate change, as a signal to the periodic scan to verify.
     */
    void invalidateChunkCacheAt(int worldX, int worldZ) {
        long key = ChunkUtil.indexChunkFromBlock(worldX, worldZ);
        chunkWoodCountCache.remove(key);
    }

    /** Lazy-loads and caches the TreeWood block key set from the asset registry. */
    Set<String> getTreeWoodBlockKeys() {
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
