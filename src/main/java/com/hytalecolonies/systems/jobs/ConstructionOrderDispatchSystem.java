package com.hytalecolonies.systems.jobs;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hytalecolonies.ConstructionOrderQueue;
import com.hytalecolonies.ConstructionOrderStore;
import com.hytalecolonies.components.jobs.ConstructorWorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.BlockStateInfoUtil;

/** Assigns pending orders from {@link ConstructionOrderQueue} to idle Constructor workstations every 3 s. */
public class ConstructionOrderDispatchSystem extends DelayedEntitySystem<ChunkStore>
{

    private static final Query<ChunkStore> QUERY = Query.and(ConstructorWorkStationComponent.getComponentType(), BlockModule.BlockStateInfo.getComponentType());

    public ConstructionOrderDispatchSystem()
    {
        super(3.0f);
    }

    @Override public void tick(float dt, int systemIndex, @Nonnull Store<ChunkStore> store)
    {
        if (ConstructionOrderQueue.get().isEmpty())
            return;
        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                      "[ConstructionOrderDispatch] Queue has %d pending order(s) -- scanning workstations.",
                      ConstructionOrderQueue.get().size());
        super.tick(dt, systemIndex, store);
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
                     @Nonnull Store<ChunkStore> chunkStore,
                     @Nonnull CommandBuffer<ChunkStore> commandBuffer)
    {
        ConstructorWorkStationComponent ws = archetypeChunk.getComponent(index, ConstructorWorkStationComponent.getComponentType());
        if (ws != null && ws.activeOrderId != null)
        {
            DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB, "[ConstructionOrderDispatch] Workstation already has active order %s -- skipping.", ws.activeOrderId);
            return;
        }

        BlockModule.BlockStateInfo blockStateInfo = archetypeChunk.getComponent(index, BlockModule.BlockStateInfo.getComponentType());
        if (blockStateInfo == null || !blockStateInfo.getChunkRef().isValid())
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructionOrderDispatch] Workstation has no valid BlockStateInfo -- skipping.");
            return;
        }

        Vector3i wsPos = new BlockStateInfoUtil().GetBlockWorldPosition(blockStateInfo, commandBuffer);
        World world = chunkStore.getExternalData().getWorld();

        world.execute(() -> executeAssignOrderToWorkstationOnWorldThread(world, wsPos));
    }

    private static void executeAssignOrderToWorkstationOnWorldThread(@Nonnull World world, @Nonnull Vector3i wsPos)
    {
        Ref<ChunkStore> wsRef = BlockModule.getBlockEntity(world, wsPos.x, wsPos.y, wsPos.z);
        if (wsRef == null || !wsRef.isValid())
            return;
        Store<ChunkStore> cs = world.getChunkStore().getStore();
        ConstructorWorkStationComponent liveWs = cs.getComponent(wsRef, ConstructorWorkStationComponent.getComponentType());
        if (liveWs == null || liveWs.activeOrderId != null)
            return;

        UUID nextId = ConstructionOrderQueue.get().poll();
        if (nextId == null)
            return;

        ConstructionOrderStore.Entry entry = ConstructionOrderStore.get().get(nextId);
        if (entry == null)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructionOrderDispatch] Order %s not found in store -- discarding.", nextId);
            return;
        }

        liveWs.activeOrderId = nextId;
        ConstructionOrderStore.get().markInProgress(nextId);
        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                      "[ConstructionOrderDispatch] Assigned order %s (prefab: %s) to workstation %s (queue remaining: %d).",
                      nextId,
                      entry.prefabId,
                      wsPos,
                      ConstructionOrderQueue.get().size());
    }

    @Override public Query<ChunkStore> getQuery()
    {
        return QUERY;
    }
}
