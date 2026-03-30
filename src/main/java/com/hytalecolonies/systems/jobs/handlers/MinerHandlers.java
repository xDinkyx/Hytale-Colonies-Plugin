package com.hytalecolonies.systems.jobs.handlers;

import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.components.world.ClaimedBlockComponent;
import com.hytalecolonies.systems.jobs.JobContext;
import com.hytalecolonies.systems.jobs.JobStateHandler;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hytalecolonies.utils.ColonistToolUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/** Default {@link JobStateHandler} implementations for miner colonists. */
public final class MinerHandlers {

    private static final String GATHER_TYPE_PICKAXE = "Rocks";
    private static final String GATHER_TYPE_SHOVEL = "Soils";

    private MinerHandlers() {}

    // ===== Handlers =====

    /**
     * Checks tool requirements, computes the mine origin if needed, finds the next unclaimed
     * shaft block, claims it, and transitions to {@link JobState#TravelingToJob}.
     */
    public static final JobStateHandler IDLE = ctx -> {
        Vector3i workStationPos = ctx.job.getWorkStationBlockPosition();
        if (workStationPos == null) return;

        World world = ctx.world;
        Ref<ChunkStore> wsRef = BlockModule.getBlockEntity(world, workStationPos.x, workStationPos.y, workStationPos.z);
        WorkStationComponent workStation = wsRef != null
                ? wsRef.getStore().getComponent(wsRef, WorkStationComponent.getComponentType())
                : null;
        if (workStation == null) {
            DebugLog.warning(DebugCategory.MINER_JOB,
                    "[MinerJob] Idle — workstation block entity not found at %s.", workStationPos);
            return;
        }

        LivingEntity colonist = (LivingEntity) EntityUtils.getEntity(ctx.colonistRef, ctx.store);
        if (colonist == null) return;
        if (!ColonistToolUtil.hasToolForGatherType(colonist.getInventory(), GATHER_TYPE_PICKAXE, 0)) {
            DebugLog.fine(DebugCategory.MINER_JOB,
                    "[MinerJob] Idle — no pickaxe ('%s') in inventory. Waiting at workstation.", GATHER_TYPE_PICKAXE);
            return;
        }
        if (!ColonistToolUtil.hasToolForGatherType(colonist.getInventory(), GATHER_TYPE_SHOVEL, 0)) {
            DebugLog.fine(DebugCategory.MINER_JOB,
                    "[MinerJob] Idle — no shovel ('%s') in inventory. Waiting at workstation.", GATHER_TYPE_SHOVEL);
            return;
        }

        MinerJobComponent miner = ctx.store.getComponent(ctx.colonistRef, MinerJobComponent.getComponentType());
        if (miner == null) return;

        // Compute mine origin once per workstation lifetime and persist it there.
        if (workStation.mineOrigin == null) {
            workStation.mineOrigin = new Vector3i(
                    workStationPos.x,
                    workStationPos.y,
                    workStationPos.z + workStation.mineOffsetZ
            );
            DebugLog.info(DebugCategory.MINER_JOB,
                    "[MinerJob] Mine origin set to %s for workstation at %s.", workStation.mineOrigin, workStationPos);
        }

        miner.blocksMinedThisRun = 0;

        Vector3i nextBlock = findNextMineBlock(workStation, world);
        if (nextBlock == null) {
            DebugLog.fine(DebugCategory.MINER_JOB,
                    "[MinerJob] Idle — no solid/unclaimed blocks remain in mine shaft at %s. Waiting.",
                    workStation.mineOrigin);
            return;
        }

        // Atomically claim on the world thread — serializes same-tick races between miners.
        EntityStore entityStore = world.getEntityStore();
        world.execute(() -> {
            JobComponent liveJob = entityStore.getStore().getComponent(ctx.colonistRef, JobComponent.getComponentType());
            if (liveJob == null || liveJob.getCurrentTask() != JobState.Idle) return;

            UUIDComponent uuidComp = entityStore.getStore().getComponent(ctx.colonistRef, UUIDComponent.getComponentType());
            if (uuidComp == null) return;

            if (!ClaimBlockUtil.claimBlock(world, nextBlock, uuidComp.getUuid(), "Mine")) {
                DebugLog.fine(DebugCategory.MINER_JOB,
                        "[MinerJob] Could not claim mine block %s (already taken) — staying Idle.", nextBlock);
                return;
            }

            entityStore.getStore().addComponent(ctx.colonistRef, JobTargetComponent.getComponentType(), new JobTargetComponent(nextBlock));
            MoveToTargetComponent existingMove = entityStore.getStore().getComponent(ctx.colonistRef, MoveToTargetComponent.getComponentType());
            if (existingMove != null) {
                existingMove.target = blockCenter(nextBlock);
            } else {
                entityStore.getStore().addComponent(ctx.colonistRef, MoveToTargetComponent.getComponentType(),
                        new MoveToTargetComponent(blockCenter(nextBlock)));
            }
            liveJob.setCurrentTask(JobState.TravelingToJob);
            DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob] Claimed mine block at %s — heading there.", nextBlock);
        });
    };

    /**
     * Detects when the current shaft block is broken, then moves to the next one or transitions
     * to {@link JobState#CollectingDrops} when the per-run quota is reached.
     *
     * <p>Per-tick block damage is handled by the NPC role's sensor/action pipeline.
     */
    public static final JobStateHandler WORKING = ctx -> {
        JobTargetComponent jobTarget = ctx.store.getComponent(ctx.colonistRef, JobTargetComponent.getComponentType());
        if (jobTarget == null || jobTarget.targetPosition == null) {
            ctx.job.setCurrentTask(JobState.Idle);
            return;
        }

        Vector3i targetPos = jobTarget.targetPosition;
        World world = ctx.world;

        // Block still standing — NPC role handles per-tick damage.
        if (world.getBlock(targetPos.x, targetPos.y, targetPos.z) != 0) return;

        Vector3i workStationPos = ctx.job.getWorkStationBlockPosition();
        if (workStationPos == null) { ctx.job.setCurrentTask(JobState.Idle); return; }
        Ref<ChunkStore> wsRef = BlockModule.getBlockEntity(world, workStationPos.x, workStationPos.y, workStationPos.z);
        WorkStationComponent workStation = wsRef != null
                ? wsRef.getStore().getComponent(wsRef, WorkStationComponent.getComponentType())
                : null;
        if (workStation == null) { ctx.job.setCurrentTask(JobState.Idle); return; }

        MinerJobComponent miner = ctx.store.getComponent(ctx.colonistRef, MinerJobComponent.getComponentType());
        if (miner == null) { ctx.job.setCurrentTask(JobState.Idle); return; }

        miner.blocksMinedThisRun++;
        DebugLog.info(DebugCategory.MINER_JOB,
                "[MinerJob] Block at %s mined (%d/%d this run).", targetPos, miner.blocksMinedThisRun, workStation.blocksPerRun);

        boolean quotaReached = miner.blocksMinedThisRun >= workStation.blocksPerRun;
        // Find next block optimistically in tick thread; world.execute() atomicity handles races.
        final Vector3i nextBlock = quotaReached ? null : findNextMineBlock(workStation, world);
        final boolean goCollect = quotaReached || nextBlock == null;
        final Vector3i finalTargetPos = targetPos;
        EntityStore entityStore = world.getEntityStore();

        world.execute(() -> {
            ClaimBlockUtil.unclaimBlock(world, finalTargetPos);

            JobComponent liveJob = entityStore.getStore().getComponent(ctx.colonistRef, JobComponent.getComponentType());
            if (liveJob == null) return;

            if (goCollect) {
                JobTargetComponent jt = entityStore.getStore().getComponent(ctx.colonistRef, JobTargetComponent.getComponentType());
                if (jt != null) jt.setTargetPosition(null);
                liveJob.collectingDropsSince = System.currentTimeMillis();
                liveJob.setCurrentTask(JobState.CollectingDrops);
                DebugLog.info(DebugCategory.MINER_JOB, quotaReached
                        ? "[MinerJob] Run quota reached — collecting drops."
                        : "[MinerJob] Mine exhausted mid-run — collecting drops.");
            } else {
                UUIDComponent uuidComp = entityStore.getStore().getComponent(ctx.colonistRef, UUIDComponent.getComponentType());
                if (uuidComp == null || !ClaimBlockUtil.claimBlock(world, nextBlock, uuidComp.getUuid(), "Mine")) {
                    DebugLog.fine(DebugCategory.MINER_JOB,
                            "[MinerJob] Could not claim next mine block %s — going Idle.", nextBlock);
                    liveJob.setCurrentTask(JobState.Idle);
                    return;
                }
                JobTargetComponent jt = entityStore.getStore().getComponent(ctx.colonistRef, JobTargetComponent.getComponentType());
                if (jt != null) {
                    jt.setTargetPosition(nextBlock);
                } else {
                    entityStore.getStore().addComponent(ctx.colonistRef, JobTargetComponent.getComponentType(),
                            new JobTargetComponent(nextBlock));
                }
                MoveToTargetComponent mt = entityStore.getStore().getComponent(ctx.colonistRef, MoveToTargetComponent.getComponentType());
                if (mt != null) {
                    mt.target = blockCenter(nextBlock);
                } else {
                    entityStore.getStore().addComponent(ctx.colonistRef, MoveToTargetComponent.getComponentType(),
                            new MoveToTargetComponent(blockCenter(nextBlock)));
                }
                liveJob.setCurrentTask(JobState.TravelingToJob);
                DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob] Claimed next mine block at %s — heading there.", nextBlock);
            }
        });
    };

    // ===== Private helpers =====

    /** Scans the mine shaft top-down and returns the first solid unclaimed block, or {@code null}. */
    @Nullable
    private static Vector3i findNextMineBlock(WorkStationComponent workStation, World world) {
        Vector3i origin = workStation.mineOrigin;
        if (origin == null) return null;
        int size = workStation.mineSize;
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        for (int dy = 0; dy < size; dy++) {        // dy=0 = top layer (workstation Y)
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    int x = origin.x + dx;
                    int y = origin.y - dy;          // descend into the shaft
                    int z = origin.z + dz;
                    if (world.getBlock(x, y, z) == 0) continue;
                    Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, x, y, z);
                    if (blockRef != null && chunkStore.getComponent(blockRef, ClaimedBlockComponent.getComponentType()) != null) continue;
                    return new Vector3i(x, y, z);
                }
            }
        }
        return null;
    }

    private static Vector3d blockCenter(Vector3i block) {
        return new Vector3d(block.x + 0.5, block.y, block.z + 0.5);
    }
}
