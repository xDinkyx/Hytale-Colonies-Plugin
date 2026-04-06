package com.hytalecolonies.systems.jobs;

import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.WoodsmanJobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.systems.jobs.handlers.WoodsmanHandlers;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hytalecolonies.utils.ColonistLeashUtil;

/**
 * Reacts to {@link JobComponent#blockBrokenNotification} for woodsmen in
 * {@link JobState#Working}. Finds the next connected trunk block and dispatches
 * navigation, or transitions to {@link JobState#CollectingDrops} when all base
 * trunks are felled.
 *
 * <p>Per-tick block damage is applied by the NPC role JSON pipeline
 * ({@code Colonist_Woodsman.json} HarvestBlock action). This system only
 * handles state transitions after a block breaks.
 */
public class WoodsmanWorkingSystem extends EntityTickingSystem<EntityStore> {

    private static final Query<EntityStore> QUERY = Query.and(
            WoodsmanJobComponent.getComponentType(),
            JobComponent.getComponentType());

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        JobComponent job = archetypeChunk.getComponent(index, JobComponent.getComponentType());
        if (job == null || job.getCurrentTask() != JobState.Working) return;
        if (!job.blockBrokenNotification) return;

        // Clear immediately so a second notification in the same cycle is ignored.
        job.blockBrokenNotification = false;

        Ref<EntityStore> colonistRef = archetypeChunk.getReferenceTo(index);
        String npcId = DebugLog.npcId(colonistRef, store);

        JobTargetComponent jobTarget = store.getComponent(colonistRef, JobTargetComponent.getComponentType());
        if (jobTarget == null || jobTarget.targetPosition == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB,
                    "[WoodsmanWorking] [%s] No JobTargetComponent or target is null -- resetting to Idling.", npcId);
            job.setCurrentTask(JobState.Idling);
            return;
        }

        Vector3i treeBase = jobTarget.targetPosition;
        World world = store.getExternalData().getWorld();

        Set<String> allowedTreeTypes = null;
        Vector3i workStationPos = job.getWorkStationBlockPosition();
        if (workStationPos != null) {
            Ref<ChunkStore> wsRef = BlockModule.getBlockEntity(world, workStationPos.x, workStationPos.y, workStationPos.z);
            WorkStationComponent workStation = wsRef != null
                    ? wsRef.getStore().getComponent(wsRef, WorkStationComponent.getComponentType())
                    : null;
            if (workStation != null)
                allowedTreeTypes = workStation.getAllowedTreeTypes();
        }

        @Nullable Vector3i nextBase = allowedTreeTypes != null
                ? WoodsmanHandlers.findNextBaseBlock(treeBase, allowedTreeTypes, world)
                : null;
        final boolean goCollect = nextBase == null;

        final Vector3i finalTreeBase = treeBase;
        final Vector3i finalNextBase = nextBase;
        EntityStore entityStore = world.getEntityStore();

        DebugLog.info(DebugCategory.WOODSMAN_JOB,
                "[WoodsmanWorking] [%s] Block broken at %s. %s",
                npcId, treeBase,
                goCollect ? "No adjacent base blocks -- collecting drops."
                          : "Next base at " + nextBase + " -- traveling there.");

        world.execute(() -> {
            // Guard against duplicate callbacks queued in the same cycle.
            JobComponent liveJob = entityStore.getStore().getComponent(colonistRef, JobComponent.getComponentType());
            if (liveJob == null || liveJob.getCurrentTask() != JobState.Working) return;

            ClaimBlockUtil.unclaimBlock(world, finalTreeBase);

            JobTargetComponent liveTarget = entityStore.getStore().getComponent(colonistRef, JobTargetComponent.getComponentType());
            if (liveTarget == null) return;

            if (goCollect) {
                ColonistLeashUtil.setLeashToBlockCenter(colonistRef, entityStore.getStore(), finalTreeBase);
                liveTarget.setTargetPosition(null);
                liveJob.collectingDropsSince = System.currentTimeMillis();
                liveJob.setCurrentTask(JobState.CollectingDrops);
            } else {
                liveTarget.setTargetPosition(finalNextBase);
                entityStore.getStore().tryRemoveComponent(colonistRef, MoveToTargetComponent.getComponentType());
                entityStore.getStore().addComponent(colonistRef, MoveToTargetComponent.getComponentType(),
                        new MoveToTargetComponent(new Vector3d(finalNextBase.x + 0.5, finalNextBase.y, finalNextBase.z + 0.5)));
                liveJob.setCurrentTask(JobState.TravelingToJob);
            }
        });
    }
}
