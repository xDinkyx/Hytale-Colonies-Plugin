package com.hytalecolonies.systems.jobs;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.ConstructionOrderStore;
import com.hytalecolonies.HytaleColoniesPlugin;
import com.hytalecolonies.components.jobs.ConstructorJobComponent;
import com.hytalecolonies.components.jobs.ConstructorWorkStationComponent;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobRunCounterComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ColonistStateUtil;
import com.hytalecolonies.utils.ConstructorUtil;
import com.hytalecolonies.utils.JobNavigationUtil;
import com.hytalecolonies.utils.WorkStationUtil;


/** Every 2 s, dispatches {@link JobState#WaitingForWork} constructors to clearing, retrieving, or marks the order complete. */
public class ConstructorJobCheckSystem extends DelayedEntitySystem<EntityStore>
{
    private static final Query<EntityStore> QUERY =
            Query.and(ConstructorJobComponent.getComponentType(), JobRunCounterComponent.getComponentType(), JobComponent.getComponentType());

    public ConstructorJobCheckSystem()
    {
        super(2.0f);
    }

    @Override
    public void
    tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk, @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer)
    {
        JobComponent job = chunk.getComponent(index, JobComponent.getComponentType());
        if (job == null)
            return;

        // Draw order footprint when debug overlay is enabled.
        if (HytaleColoniesPlugin.getInstance().getDebugConfig().get().isDrawConstructorOrders())
        {
            Vector3i drawWsPos = job.getWorkStationBlockPosition();
            if (drawWsPos != null)
            {
                World drawWorld = store.getExternalData().getWorld();
                ConstructionOrderStore.Entry drawOrder = WorkStationUtil.getConstructionOrderForWorkstation(drawWorld, drawWsPos);
                if (drawOrder != null)
                {
                    BlockSelection drawPrefab = ConstructorUtil.loadPrefab(drawOrder);
                    // world.getBlock() can trigger chunk loading which mutates the EntityStore.
                    // Must not be called from inside a system tick -- defer to world.execute().
                    drawWorld.execute(() -> ConstructorUtil.drawConstructionOrderOverlay(drawOrder, drawPrefab, drawWorld));
                }
            }
        }

        if (job.getCurrentTask() != JobState.WaitingForWork)
            return;

        Ref<EntityStore> colonistRef = chunk.getReferenceTo(index);
        String npcId = DebugLog.npcId(colonistRef, store);

        Vector3i wsPos = job.getWorkStationBlockPosition();
        if (wsPos == null)
            return;

        World world = store.getExternalData().getWorld();
        WorkStationComponent wsBase = WorkStationUtil.getWorkStationAt(world, wsPos);
        if (wsBase == null)
            return;
        ConstructorWorkStationComponent ws = WorkStationUtil.getConstructorWorkStationAt(world, wsPos);
        if (ws == null)
            return;

        if (ws.activeOrderId == null)
        {
            DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorJob] [%s] Idle -- no order assigned yet.", npcId);
            return;
        }

        ConstructionOrderStore.Entry order = ConstructionOrderStore.get().get(ws.activeOrderId);
        if (order == null)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                             "[ConstructorJob] [%s] Order %s not found in store -- clearing workstation pointer.",
                             npcId,
                             ws.activeOrderId);
            final Vector3i staleWsPos = wsPos;
            world.execute(() -> {
                Ref<ChunkStore> wsRef2 = BlockModule.getBlockEntity(world, staleWsPos.x, staleWsPos.y, staleWsPos.z);
                if (wsRef2 == null || !wsRef2.isValid())
                    return;
                var cs2 = world.getChunkStore().getStore();
                ConstructorWorkStationComponent staleWs = cs2.getComponent(wsRef2, ConstructorWorkStationComponent.getComponentType());
                if (staleWs == null)
                    return;
                staleWs.activeOrderId = null;
            });
            return;
        }

        BlockSelection prefab = ConstructorUtil.loadPrefab(order);
        if (prefab == null)
            return;

        // Clearing phase.
        Vector3i nextClear = ConstructorUtil.findNextClearingTarget(order, world, prefab);
        if (nextClear != null)
        {
            UUIDComponent uuid = store.getComponent(colonistRef, UUIDComponent.getComponentType());
            if (uuid == null)
                return;
            final UUID colonistUuid = uuid.getUuid();
            EntityStore entityStore = world.getEntityStore();
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorJob] [%s] Clearing needed -- dispatching.", npcId);
            world.execute(() -> {
                JobComponent liveJob = entityStore.getStore().getComponent(colonistRef, JobComponent.getComponentType());
                if (liveJob == null || liveJob.getCurrentTask() != JobState.WaitingForWork)
                    return;
                // Find and claim atomically on the world thread to avoid two colonists racing to grab the same block.
                Vector3i claimed = ConstructorUtil.claimNextClearingTarget(order, world, prefab, colonistUuid);
                if (claimed == null)
                {
                    DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorJob] [%s] No claimable clearing block (all taken) -- will retry.", npcId);
                    return;
                }
                JobNavigationUtil.setJobTarget(entityStore.getStore(), colonistRef, claimed);
                JobNavigationUtil.dispatchNavigation(entityStore.getStore(), colonistRef, claimed);
                liveJob.workAvailable = true;
                // Reset counter so quota is not immediately exceeded on re-dispatch.
                JobRunCounterComponent liveCounter = entityStore.getStore().getComponent(colonistRef, JobRunCounterComponent.getComponentType());
                if (liveCounter != null)
                    liveCounter.count = 0;
                ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WorkingClearing);
                DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorJob] [%s] Dispatched to clearing block at %s.", npcId, claimed);
            });

            return;
        }

        // All clearing done -- check if anything still needs to be built.
        Vector3i nextBuild = ConstructorUtil.findNextBuildTarget(order, world, prefab);
        if (nextBuild == null)
        {
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorJob] [%s] Construction order %s complete -- freeing workstation.", npcId, order.id);
            final UUID orderId = order.id;
            EntityStore entityStore = world.getEntityStore();
            world.execute(() -> {
                Ref<ChunkStore> wsRef = BlockModule.getBlockEntity(world, wsPos.x, wsPos.y, wsPos.z);
                if (wsRef != null && wsRef.isValid())
                {
                    var cs = world.getChunkStore().getStore();
                    ConstructorWorkStationComponent liveWs = cs.getComponent(wsRef, ConstructorWorkStationComponent.getComponentType());
                    if (liveWs != null)
                        liveWs.activeOrderId = null;
                }
                ConstructionOrderStore.get().remove(orderId);
                JobComponent liveJob = entityStore.getStore().getComponent(colonistRef, JobComponent.getComponentType());
                if (liveJob != null)
                    liveJob.workAvailable = true;
            });
            return;
        }

        // Clearing done, build targets exist -- retrieve materials.
        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorJob] [%s] Clearing done, build targets exist -- WorkingRetrievingBlocks.", npcId);
        EntityStore entityStore = world.getEntityStore();
        world.execute(() -> {
            JobComponent liveJob = entityStore.getStore().getComponent(colonistRef, JobComponent.getComponentType());
            if (liveJob == null || liveJob.getCurrentTask() != JobState.WaitingForWork)
                return;
            liveJob.workAvailable = true;
            ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WorkingRetrievingBlocks);
        });
    }

    @Override public @Nullable Query<EntityStore> getQuery()
    {
        return QUERY;
    }
}
