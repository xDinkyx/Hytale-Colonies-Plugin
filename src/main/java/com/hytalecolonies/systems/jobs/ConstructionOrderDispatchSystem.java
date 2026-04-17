package com.hytalecolonies.systems.jobs;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hytalecolonies.ConstructionOrderQueue;
import com.hytalecolonies.components.jobs.ConstructionOrderComponent;
import com.hytalecolonies.components.jobs.ConstructorWorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.BlockStateInfoUtil;
import com.hytalecolonies.utils.WorkStationUtil;

/**
 * Periodically assigns pending orders from {@link ConstructionOrderQueue} to idle
 * Constructor workstations. One order per idle workstation per cycle.
 */
public class ConstructionOrderDispatchSystem extends DelayedEntitySystem<ChunkStore> {

    private static final Query<ChunkStore> QUERY = Query.and(
            ConstructorWorkStationComponent.getComponentType(),
            BlockModule.BlockStateInfo.getComponentType());

    public ConstructionOrderDispatchSystem() {
        super(3.0f);
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<ChunkStore> store) {
        if (ConstructionOrderQueue.get().isEmpty()) return;
        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                "[ConstructionOrderDispatch] Queue has %d pending order(s) -- scanning workstations.",
                ConstructionOrderQueue.get().size());
        super.tick(dt, systemIndex, store);
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
                     @Nonnull Store<ChunkStore> chunkStore,
                     @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        ConstructorWorkStationComponent ws = archetypeChunk.getComponent(index,
                ConstructorWorkStationComponent.getComponentType());
        if (ws == null || ws.activeOrderPosition != null) {
            if (ws != null) {
                DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB,
                        "[ConstructionOrderDispatch] Workstation already has an active order at %s -- skipping.",
                        ws.activeOrderPosition);
            }
            return;
        }

        BlockModule.BlockStateInfo blockStateInfo = archetypeChunk.getComponent(index,
                BlockModule.BlockStateInfo.getComponentType());
        if (blockStateInfo == null || !blockStateInfo.getChunkRef().isValid()) {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                    "[ConstructionOrderDispatch] Workstation has no valid BlockStateInfo -- skipping.");
            return;
        }

        Vector3i wsPos = new BlockStateInfoUtil().GetBlockWorldPosition(blockStateInfo, commandBuffer);
        World world = chunkStore.getExternalData().getWorld();

        world.execute(() -> {
            ConstructorWorkStationComponent liveWs = WorkStationUtil.getConstructorWorkStationAt(world, wsPos);
            if (liveWs == null || liveWs.activeOrderPosition != null) return;
            Vector3i nextOrderPos = ConstructionOrderQueue.get().poll();
            if (nextOrderPos == null) return;
            liveWs.activeOrderPosition = nextOrderPos;
            ConstructionOrderComponent liveOrder = WorkStationUtil.getConstructionOrderAt(world, nextOrderPos);
            if (liveOrder != null) liveOrder.status = "InProgress";
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                    "[ConstructionOrderDispatch] Assigned order at %s to workstation %s (queue remaining: %d).",
                    nextOrderPos, wsPos, ConstructionOrderQueue.get().size());
        });
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return QUERY;
    }
}
