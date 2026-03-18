package com.hytalecolonies.systems.treescan;

import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Reacts to players breaking or placing tree-wood blocks and immediately
 * updates the {@link com.hytalecolonies.components.world.HarvestableTreeComponent}
 * registry rather than waiting for the next periodic scan.
 *
 * <h3>Block-break</h3>
 * When a tree-wood block is broken the surviving trunk structure (if any) is
 * re-evaluated via BFS and the component's wood count updated.  If no valid
 * tree remains the component is removed.  If the broken block was the base the
 * server auto-removes the block entity (and our component with it).
 *
 * <h3>Block-place</h3>
 * When a tree-wood block is placed a targeted BFS is run from the segment
 * bottom of the new trunk.  If the structure qualifies as a tree and is not
 * already registered a new component is created.
 *
 * <h3>Sapling growth</h3>
 * Growth events have no dedicated hook in the Hytale API so they are still
 * handled by the slow periodic scan in {@link TreeScannerSystem}.
 */
public class TreeBlockChangeEventSystem {

    // -------------------------------------------------------------------------
    // Block-break handler
    // -------------------------------------------------------------------------

    public static class OnBreak extends EntityEventSystem<EntityStore, BreakBlockEvent> {

        private final TreeScannerSystem scanner;

        public OnBreak(TreeScannerSystem scanner) {
            super(BreakBlockEvent.class);
            this.scanner = scanner;
        }

        @Override
        public void handle(
                int index,
                @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer,
                @Nonnull BreakBlockEvent event) {

            BlockType blockType = event.getBlockType();
            if (!scanner.getTreeWoodBlockKeys().contains(blockType.getId())) return;

            Vector3i pos = event.getTargetBlock();
            World world = store.getExternalData().getWorld();

            // Schedule post-break processing so the block is actually removed when we run.
            world.execute(() -> {
                Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
                scanner.onTreeWoodBlockRemoved(pos, world, chunkStore);
                scanner.invalidateChunkCacheAt(pos.x, pos.z);
            });
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Block-place handler
    // -------------------------------------------------------------------------

    public static class OnPlace extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

        private final TreeScannerSystem scanner;

        public OnPlace(TreeScannerSystem scanner) {
            super(PlaceBlockEvent.class);
            this.scanner = scanner;
        }

        @Override
        public void handle(
                int index,
                @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer,
                @Nonnull PlaceBlockEvent event) {

            // PlaceBlockEvent fires before the block is placed; we cannot know the
            // block type yet.  Schedule a check to run after placement completes.
            Vector3i pos = event.getTargetBlock();
            World world = store.getExternalData().getWorld();

            world.execute(() -> {
                // Verify the placed block is actually a tree-wood type before running BFS.
                int blockId = world.getBlock(pos);
                BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
                if (blockType == null) return;
                if (!scanner.getTreeWoodBlockKeys().contains(blockType.getId())) return;

                DebugLog.log(DebugCategory.TREE_SCANNER,
                        "[TreeScanner] Tree-wood block placed at %s — checking for new tree.", pos);
                Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
                scanner.onTreeWoodBlockAdded(pos, world, chunkStore);
                scanner.invalidateChunkCacheAt(pos.x, pos.z);
            });
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }
}
