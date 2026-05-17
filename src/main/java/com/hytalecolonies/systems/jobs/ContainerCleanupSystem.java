package com.hytalecolonies.systems.jobs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ColonistStateUtil;
import com.hytalecolonies.utils.WorkStationUtil;

/**
 * Clears the cached delivery target when a container block entity is removed.
 */
public class ContainerCleanupSystem extends RefSystem<ChunkStore> {

    private final ComponentType<ChunkStore, ItemContainerBlock> itemContainerType = ItemContainerBlock
            .getComponentType();
    private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> blockStateInfoType = BlockModule.BlockStateInfo
            .getComponentType();
    private final Query<ChunkStore> query = Query.and(itemContainerType, blockStateInfoType);

    private final Query<EntityStore> colonistQuery = Query.and(
            ColonistComponent.getComponentType(),
            JobComponent.getComponentType());

    @Override
    public Query<ChunkStore> getQuery() {
        return query;
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason,
            @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref,
                               @Nonnull RemoveReason reason,
                               @Nonnull Store<ChunkStore> store,
                               @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        DebugLog.info(DebugCategory.COLONIST_DELIVERY, "[ContainerCleanupSystem] Fired -- reason=%s.", reason);

        if (reason == RemoveReason.UNLOAD)
            return;

        BlockModule.BlockStateInfo blockStateInfo = commandBuffer.getComponent(ref, blockStateInfoType);
        Ref<ChunkStore> chunkRef = blockStateInfo.getChunkRef();
        BlockChunk blockChunk = commandBuffer.getComponent(chunkRef, BlockChunk.getComponentType());

        // ToDo: Helper method to get world position from BlockStateInfo. Use the one from BlockStateInfoUtil. 
        int idx = blockStateInfo.getIndex();
        int localX = ChunkUtil.xFromBlockInColumn(idx);
        int localY = ChunkUtil.yFromBlockInColumn(idx);
        int localZ = ChunkUtil.zFromBlockInColumn(idx);
        Vector3i blockPos = new Vector3i(
                ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localX),
                localY,
                ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localZ));

        DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                "[ContainerCleanupSystem] Container removed at %s -- scanning colonists.", blockPos);

        Store<EntityStore> entityStore = store.getExternalData().getWorld().getEntityStore().getStore();
        entityStore.forEachChunk(colonistQuery, (chunk, cb) -> {
            for (int i = 0; i < chunk.size(); i++) {
                JobComponent colonistJob = chunk.getComponent(i, JobComponent.getComponentType());
                if (colonistJob == null)
                    continue;
                Ref<EntityStore> colonistRef = chunk.getReferenceTo(i);
                WorkStationComponent workStation = WorkStationUtil.getWorkStation(entityStore, colonistRef);

                DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                        "[ContainerCleanupSystem] [%s] Colonist state=%s deliveryTarget=%s.",
                        DebugLog.npcId(colonistRef, entityStore), colonistJob.getCurrentTask(),
                        workStation != null ? workStation.deliveryContainerPosition : null);

                if (colonistJob.getCurrentTask() != JobState.DeliveringItems)
                    continue;
                if (workStation == null || !blockPos.equals(workStation.deliveryContainerPosition))
                    continue;

                // Clear the cached workstation delivery target.
                workStation.deliveryContainerPosition = null;
                Vector3i wsPos = colonistJob.getWorkStationBlockPosition();
                if (wsPos != null) {
                    cb.addComponent(colonistRef, MoveToTargetComponent.getComponentType(),
                            new MoveToTargetComponent(new Vector3d(wsPos.x + 0.5, wsPos.y, wsPos.z + 0.5)));
                }

                ColonistStateUtil.setJobState(colonistRef, entityStore, colonistJob, JobState.TravelingToWorkstation);
                DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                        "[ContainerCleanupSystem] [%s] Redirected colonist to workstation.",
                        DebugLog.npcId(colonistRef, entityStore));
            }
        });
    }
}
