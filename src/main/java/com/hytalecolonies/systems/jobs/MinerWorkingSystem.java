package com.hytalecolonies.systems.jobs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobRunCounterComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.components.jobs.MinerWorkStationComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.MinerUtil;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hytalecolonies.utils.ColonistLeashUtil;
import com.hytalecolonies.utils.ColonistStateUtil;

/**
 * Reacts to {@link JobComponent#blockBrokenNotification} for miners in {@link JobState#Working}.
 * Increments the run counter and transitions to the next block (TravelingToJob) or
 * CollectingDrops when the quota is reached or the shaft is exhausted.
 */
public class MinerWorkingSystem extends EntityTickingSystem<EntityStore> {

    private static final Query<EntityStore> QUERY = Query.and(
            MinerJobComponent.getComponentType(),
            JobRunCounterComponent.getComponentType(),
            JobComponent.getComponentType()
    );

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
        if (job == null || job.getCurrentTask() != JobState.Working) 
            return;
        if (!job.blockBrokenNotification) 
            return;

        // Clear immediately so a second notification in the same cycle is ignored until world.execute settles.
        job.blockBrokenNotification = false;

        Ref<EntityStore> colonistRef = archetypeChunk.getReferenceTo(index);

        JobRunCounterComponent counter = archetypeChunk.getComponent(index, JobRunCounterComponent.getComponentType());
        if (counter == null) 
            return;

        counter.count++;

        Vector3i workStationPos = job.getWorkStationBlockPosition();
        if (workStationPos == null) {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }

        World world = store.getExternalData().getWorld();
        Ref<ChunkStore> wsRef = BlockModule.getBlockEntity(world, workStationPos.x, workStationPos.y, workStationPos.z);
        WorkStationComponent workStation = wsRef != null
                ? wsRef.getStore().getComponent(wsRef, WorkStationComponent.getComponentType())
                : null;
        if (workStation == null) {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }
        MinerWorkStationComponent minerConfig = wsRef.getStore().getComponent(wsRef, MinerWorkStationComponent.getComponentType());
        if (minerConfig == null) {
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }
        String npcId = DebugLog.npcId(colonistRef, store);

        boolean quotaReached = counter.count >= workStation.blocksPerRun;
        // Optimistic scan on entity-tick thread; world.execute handles claim races.
        @Nullable Vector3i nextBlock = quotaReached ? null : MinerUtil.findNextMineBlock(minerConfig, world);
        final boolean goCollect = quotaReached || nextBlock == null;

        // Capture before crossing into world.execute.
        JobTargetComponent jobTarget = store.getComponent(colonistRef, JobTargetComponent.getComponentType());
        @Nullable final Vector3i currentTargetPos = jobTarget != null ? jobTarget.targetPosition : null;

        EntityStore entityStore = world.getEntityStore();

        DebugLog.info(DebugCategory.MINER_JOB,
                "[MinerWorking] [%s] Block broken (%d/%d this run). %s",
                npcId, counter.count, workStation.blocksPerRun,
                goCollect ? "Collecting drops." : "Seeking next block at " + nextBlock + ".");

        world.execute(() -> {
            // Guard against duplicate callbacks queued in the same cycle.
            JobComponent liveJob = entityStore.getStore().getComponent(colonistRef, JobComponent.getComponentType());
            if (liveJob == null || liveJob.getCurrentTask() != JobState.Working) return;

            if (currentTargetPos != null) {
                ClaimBlockUtil.unclaimBlock(world, currentTargetPos);
            }

            if (goCollect) {
                JobTargetComponent jt = entityStore.getStore().getComponent(colonistRef, JobTargetComponent.getComponentType());
                if (jt != null) jt.setTargetPosition(null);
                // Set leash to the last mined block so WanderInCircle constrains drop-pickup to that area.
                if (currentTargetPos != null) {
                    ColonistLeashUtil.setLeashToBlockCenter(colonistRef, entityStore.getStore(), currentTargetPos);
                }
                liveJob.collectingDropsSince = System.currentTimeMillis();
                ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.CollectingDrops);
                DebugLog.info(DebugCategory.MINER_JOB,
                        "[MinerWorking] [%s] %s -- transitioning to CollectingDrops.",
                        npcId, quotaReached ? "Quota reached" : "Shaft exhausted mid-run");
            } else {
                UUIDComponent uuidComp = entityStore.getStore().getComponent(colonistRef, UUIDComponent.getComponentType());
                if (uuidComp == null) {
                    ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.Idle);
                    return;
                }
                if (!ClaimBlockUtil.claimBlock(world, nextBlock, uuidComp.getUuid(), "Mine")) {
                    // Race loss -- retry via Idle.
                    DebugLog.fine(DebugCategory.MINER_JOB,
                            "[MinerWorking] [%s] Could not claim next block %s -- going Idle.", npcId, nextBlock);
                    ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.Idle);
                    return;
                }
                JobTargetComponent jt = entityStore.getStore().getComponent(colonistRef, JobTargetComponent.getComponentType());
                if (jt != null) {
                    jt.setTargetPosition(nextBlock);
                } else {
                    entityStore.getStore().addComponent(colonistRef, JobTargetComponent.getComponentType(),
                            new JobTargetComponent(nextBlock));
                }
                entityStore.getStore().tryRemoveComponent(colonistRef, MoveToTargetComponent.getComponentType());
                entityStore.getStore().addComponent(colonistRef, MoveToTargetComponent.getComponentType(),
                        new MoveToTargetComponent(MinerUtil.blockCenter(nextBlock)));
                ColonistStateUtil.setJobState(colonistRef, entityStore.getStore(), liveJob, JobState.TravelingToWorkSite);
                DebugLog.info(DebugCategory.MINER_JOB,
                        "[MinerWorking] [%s] Claimed next block at %s -- transitioning to TravelingToWorkSite.", npcId, nextBlock);
            }
        });
    }
}
