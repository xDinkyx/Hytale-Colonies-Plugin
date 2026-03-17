package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.components.jobs.JobType;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.BlockStateInfoUtil;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Performs the initial tree scan around a Woodsman workstation the moment the
 * workstation entity is loaded or created.
 *
 * <p>This replaces the old approach of waiting for the first
 * {@link TreeScannerSystem} periodic tick. The periodic system is kept but runs
 * every 60 s solely to catch sapling growth (no API hook exists for those
 * world-generated block changes). It skips chunks whose wood count is unchanged
 * since the last scan so the per-tick overhead is near-free.
 */
public class WorkstationTreeInitSystem extends RefChangeSystem<ChunkStore, WorkStationComponent> {

    private final TreeScannerSystem scanner;

    public WorkstationTreeInitSystem(TreeScannerSystem scanner) {
        this.scanner = scanner;
    }

    @Override
    public ComponentType<ChunkStore, WorkStationComponent> componentType() {
        return WorkStationComponent.getComponentType();
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return WorkStationComponent.getComponentType();
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull WorkStationComponent workStation,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {

        if (workStation.getJobType() != JobType.Woodsman) return;

        BlockModule.BlockStateInfo blockStateInfo = store.getComponent(
                ref, BlockModule.BlockStateInfo.getComponentType());
        if (blockStateInfo == null) {
            DebugLog.log(DebugCategory.TREE_SCANNER, Level.WARNING,
                    "[TreeScanner Init] WorkStationComponent added without BlockStateInfo — skipping initial scan.");
            return;
        }

        Vector3i workStationPos = new BlockStateInfoUtil().GetBlockWorldPosition(blockStateInfo, commandBuffer);
        DebugLog.log(DebugCategory.TREE_SCANNER, Level.INFO,
                "[TreeScanner Init] Initial scan triggered for Woodsman workstation at %s.", workStationPos);
        scanner.scanForTreeWoodBlocks(workStationPos, store, commandBuffer);
    }

    @Override
    public void onComponentSet(
            @Nonnull Ref<ChunkStore> ref,
            @Nullable WorkStationComponent oldComponent,
            @Nonnull WorkStationComponent newComponent,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        // No action needed on update.
    }

    @Override
    public void onComponentRemoved(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull WorkStationComponent component,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        // No action needed on removal.
    }
}
