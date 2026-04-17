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
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.HytaleColoniesPlugin;
import com.hytalecolonies.components.jobs.ConstructionOrderComponent;
import com.hytalecolonies.components.jobs.ConstructorJobComponent;
import com.hytalecolonies.components.jobs.ConstructorWorkStationComponent;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobRunCounterComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hytalecolonies.utils.ColonistStateUtil;
import com.hytalecolonies.utils.ConstructorUtil;
import com.hytalecolonies.utils.JobNavigationUtil;
import com.hytalecolonies.utils.WorkStationUtil;


/**
 * Periodic check (every 2 s) for constructor colonists in {@link JobState#WaitingForWork}.
 * Dispatches them to the correct work phase given the active construction order:
 * <ul>
 *   <li>Clearing targets exist → claim first target → {@link JobState#WorkingClearing}</li>
 *   <li>Clearing done, build targets exist → {@link JobState#WorkingRetrievingBlocks}</li>
 *   <li>No targets at all → mark order complete, clear workstation pointer</li>
 * </ul>
 *
 * <p>Order assignment is handled separately by {@link ConstructionOrderDispatchSystem}.
 */
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

        if (HytaleColoniesPlugin.getInstance().getDebugConfig().get().isDrawConstructorOrders())
        {
            Vector3i drawWsPos = job.getWorkStationBlockPosition();
            if (drawWsPos != null)
            {
                World drawWorld = store.getExternalData().getWorld();
                ConstructorWorkStationComponent drawWs = WorkStationUtil.getConstructorWorkStationAt(drawWorld, drawWsPos);
                if (drawWs != null && drawWs.activeOrderPosition != null)
                {
                    ConstructionOrderComponent drawOrder = WorkStationUtil.getConstructionOrderForWorkstation(drawWorld, drawWsPos);
                    BlockSelection drawPrefab = ConstructorUtil.loadPrefab(drawOrder);
                    ConstructorUtil.drawConstructionOrderOverlay(drawOrder, drawPrefab, drawWorld);
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

        if (ws.activeOrderPosition == null)
        {
            DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorJob] [%s] Idle -- no order assigned yet.", npcId);
            return;
        }

        ConstructionOrderComponent order = WorkStationUtil.getConstructionOrderForWorkstation(world, wsPos);
        BlockSelection prefab = ConstructorUtil.loadPrefab(order);
        if (prefab == null)
        {
            DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorJob] [%s] Cannot load prefab for active order.", npcId);
            return;
        }

        // 1. Check for clearing targets first.
        Vector3i nextClear = ConstructorUtil.findNextClearingTarget(order, world, prefab);
        if (nextClear != null)
        {
            UUIDComponent uuid = store.getComponent(colonistRef, UUIDComponent.getComponentType());
            if (uuid == null)
                return;
            final UUID colonistUuid = uuid.getUuid();
            final Vector3i target = nextClear;
            EntityStore entityStore = world.getEntityStore();
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorJob] [%s] Dispatching to clearing block at %s.", npcId, target);
            world.execute(() -> {
                JobComponent liveJob = entityStore.getStore().getComponent(colonistRef, JobComponent.getComponentType());
                if (liveJob == null || liveJob.getCurrentTask() != JobState.WaitingForWork)
                    return;
                if (!ClaimBlockUtil.claimBlock(world, target, colonistUuid, "Clear"))
                {
                    DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorJob] [%s] Claim of %s failed (race) -- retrying next cycle.", npcId, target);
                    return;
                }
                JobNavigationUtil.setJobTarget(entityStore.getStore(), colonistRef, target);
                JobNavigationUtil.dispatchNavigation(entityStore.getStore(), colonistRef, target);
                liveJob.workAvailable = true;
                ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.WorkingClearing);
            });
            return;
        }

        // 2. Clearing done -- check if build targets exist.
        Vector3i nextBuild = ConstructorUtil.findNextBuildTarget(order, world, prefab);
        if (nextBuild == null)
        {
            // Order complete -- clear workstation pointer so it can accept the next queued order.
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorJob] [%s] Construction order complete -- freeing workstation.", npcId);
            final Vector3i orderPos = ws.activeOrderPosition;
            EntityStore entityStore = world.getEntityStore();
            world.execute(() -> {
                ConstructorWorkStationComponent liveWs = WorkStationUtil.getConstructorWorkStationAt(world, wsPos);
                if (liveWs != null)
                    liveWs.activeOrderPosition = null;
                ConstructionOrderComponent liveOrder = orderPos != null ? WorkStationUtil.getConstructionOrderAt(world, orderPos) : null;
                if (liveOrder != null)
                    liveOrder.status = "Complete";
                JobComponent liveJob = entityStore.getStore().getComponent(colonistRef, JobComponent.getComponentType());
                if (liveJob != null)
                    liveJob.workAvailable = true;
            });
            return;
        }

        // 3. Build targets exist but materials need collecting first.
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
