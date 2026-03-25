package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.components.jobs.JobType;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.debug.DebugTiming;
import com.hytalecolonies.systems.treescan.TreeScannerSystem;
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

/**
 * Performs initial setup for any workstation the moment its
 * {@link WorkStationComponent}
 * is loaded or created. Dispatches to per-{@link JobType} initialization logic
 * so each job type can seed whatever state it needs.
 * <p>
 * Woodsman: triggers an immediate tree scan via {@link TreeScannerSystem}.
 * Other job types: no-op for now; add cases as needed.
 */
public class WorkstationInitSystem extends RefChangeSystem<ChunkStore, WorkStationComponent> {

    private final TreeScannerSystem treeScannerSystem;

    public WorkstationInitSystem(TreeScannerSystem treeScannerSystem) {
        this.treeScannerSystem = treeScannerSystem;
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
        switch (workStation.getJobType()) {
            case Woodsman -> initWoodsman(ref, workStation, store, commandBuffer);
            case Miner -> DebugLog.info(DebugCategory.MINER_JOB,
                    "[WorkstationInit] Miner workstation placed — no initial scan needed for phase 1.");
            case Farmer, Builder -> {
                /* no-op: initialization not yet implemented */ }
        }
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

    // ===== Per-type initialization =====

    private void initWoodsman(
            Ref<ChunkStore> ref,
            WorkStationComponent workStation,
            Store<ChunkStore> store,
            CommandBuffer<ChunkStore> commandBuffer) {

        BlockModule.BlockStateInfo blockStateInfo = store.getComponent(
                ref, BlockModule.BlockStateInfo.getComponentType());
        if (blockStateInfo == null) {
            DebugLog.warning(DebugCategory.TREE_SCANNER,
                    "[WorkstationInit] WorkStationComponent added without BlockStateInfo — skipping initial scan.");
            return;
        }

        Vector3i workStationPos = new BlockStateInfoUtil().GetBlockWorldPosition(blockStateInfo, commandBuffer);
        DebugLog.info(DebugCategory.TREE_SCANNER,
                "[WorkstationInit] Initial tree scan triggered for Woodsman workstation at %s.", workStationPos);
        try (var t = DebugTiming.measure("WorkstationInit.initialTreeScan@" + workStationPos, 1000)) {
            treeScannerSystem.scanForTreeWoodBlocks(workStationPos, store, commandBuffer);
        }
    }
}
