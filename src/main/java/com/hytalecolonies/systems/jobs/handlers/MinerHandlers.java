package com.hytalecolonies.systems.jobs.handlers;

import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.components.world.ClaimedBlockComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.systems.jobs.JobStateHandler;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hytalecolonies.utils.ColonistStateUtil;

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
    public static final JobStateHandler IDLE = SharedHandlers.idle(
            new String[]{GATHER_TYPE_PICKAXE, GATHER_TYPE_SHOVEL},
            (ctx, workStation, workStationPos) -> {
                // Compute mine origin once per workstation lifetime.
                if (workStation.mineOrigin == null) {
                    workStation.mineOrigin = new Vector3i(
                            workStationPos.x,
                            workStationPos.y,
                            workStationPos.z + workStation.mineOffsetZ
                    );
                    DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] [%s] Mine origin set to %s.",
                            DebugLog.npcId(ctx.colonistRef, ctx.store), workStation.mineOrigin);
                }
                MinerJobComponent miner = ctx.store.getComponent(ctx.colonistRef, MinerJobComponent.getComponentType());
                if (miner == null) {
                    DebugLog.warning(DebugCategory.MINER_JOB, "[MinerJob:Idling] [%s] No MinerJobComponent on colonist.",
                            DebugLog.npcId(ctx.colonistRef, ctx.store));
                    return null;
                }
                DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] [%s] WorkStation found. mineOffsetZ=%d mineSize=%d blocksPerRun=%d blocksMinedThisRun=%d.",
                        DebugLog.npcId(ctx.colonistRef, ctx.store), workStation.mineOffsetZ, workStation.mineSize, workStation.blocksPerRun, miner.blocksMinedThisRun);
                miner.blocksMinedThisRun = 0;
                Vector3i nextBlock = findNextMineBlock(workStation, ctx.world);
                if (nextBlock == null) {
                    DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] [%s] No solid/unclaimed blocks in shaft at %s - flagging no work.",
                            DebugLog.npcId(ctx.colonistRef, ctx.store), workStation.mineOrigin);
                }
                return nextBlock;
            },
            "Mine");

    /**
     * Detects when the current shaft block is broken, then moves to the next one or transitions
     * to {@link JobState#CollectingDrops} when the per-run quota is reached.
     *
     * <p>Per-tick block damage is handled by the NPC role's sensor/action pipeline.
     */
    public static final JobStateHandler WORKING = ctx -> {
        JobTargetComponent jobTarget = ctx.store.getComponent(ctx.colonistRef, JobTargetComponent.getComponentType());
        if (jobTarget == null || jobTarget.targetPosition == null) {
            ColonistStateUtil.setJobState(ctx.colonistRef, ctx.store, ctx.job, JobState.Idling);
            return;
        }

        Vector3i targetPos = jobTarget.targetPosition;
        World world = ctx.world;

        // Block still standing -- NPC role handles per-tick damage.
        if (world.getBlock(targetPos.x, targetPos.y, targetPos.z) != 0) return;

        Vector3i workStationPos = ctx.job.getWorkStationBlockPosition();
        if (workStationPos == null) { ColonistStateUtil.setJobState(ctx.colonistRef, ctx.store, ctx.job, JobState.Idling); return; }
        Ref<ChunkStore> wsRef = BlockModule.getBlockEntity(world, workStationPos.x, workStationPos.y, workStationPos.z);
        WorkStationComponent workStation = wsRef != null
                ? wsRef.getStore().getComponent(wsRef, WorkStationComponent.getComponentType())
                : null;
        if (workStation == null) { ColonistStateUtil.setJobState(ctx.colonistRef, ctx.store, ctx.job, JobState.Idling); return; }

        MinerJobComponent miner = ctx.store.getComponent(ctx.colonistRef, MinerJobComponent.getComponentType());
        if (miner == null) { ColonistStateUtil.setJobState(ctx.colonistRef, ctx.store, ctx.job, JobState.Idling); return; }

        miner.blocksMinedThisRun++;
        DebugLog.info(DebugCategory.MINER_JOB,
                "[MinerJob] [%s] Block at %s mined (%d/%d this run).",
                DebugLog.npcId(ctx.colonistRef, ctx.store), targetPos, miner.blocksMinedThisRun, workStation.blocksPerRun);

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
                ColonistStateUtil.setJobState(ctx.colonistRef, entityStore.getStore(), liveJob, JobState.CollectingDrops);
                DebugLog.info(DebugCategory.MINER_JOB, quotaReached
                        ? "[MinerJob] [%s] Run quota reached -- collecting drops."
                        : "[MinerJob] [%s] Mine exhausted mid-run -- collecting drops.",
                        DebugLog.npcId(ctx.colonistRef, entityStore.getStore()));
            } else {
                UUIDComponent uuidComp = entityStore.getStore().getComponent(ctx.colonistRef, UUIDComponent.getComponentType());
                if (uuidComp == null || !ClaimBlockUtil.claimBlock(world, nextBlock, uuidComp.getUuid(), "Mine")) {
                    DebugLog.fine(DebugCategory.MINER_JOB,
                            "[MinerJob] [%s] Could not claim next mine block %s -- going Idling.",
                            DebugLog.npcId(ctx.colonistRef, entityStore.getStore()), nextBlock);
                    ColonistStateUtil.setJobState(ctx.colonistRef, entityStore.getStore(), liveJob, JobState.Idling);
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
                ColonistStateUtil.setJobState(ctx.colonistRef, entityStore.getStore(), liveJob, JobState.TravelingToJob);
                DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob] [%s] Claimed next mine block at %s -- heading there.",
                        DebugLog.npcId(ctx.colonistRef, entityStore.getStore()), nextBlock);
            }
        });
    };

    // ===== Private helpers =====

    /** Scans the mine shaft top-down and returns the first solid unclaimed block, or {@code null}. */
    @Nullable
    public static Vector3i findNextMineBlock(WorkStationComponent workStation, World world) {
        Vector3i origin = workStation.mineOrigin;
        if (origin == null) return null;
        int size = workStation.mineSize;
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        int checkedTotal = 0;
        int nonAir = 0;
        int claimed = 0;
        for (int dy = 0; dy < size; dy++) {
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    int x = origin.x + dx;
                    int y = origin.y - dy;
                    int z = origin.z + dz;
                    checkedTotal++;
                    int blockId = world.getBlock(x, y, z);
                    if (blockId == 0) continue;
                    nonAir++;
                    DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Scan] Non-air block id=%d at (%d,%d,%d).", blockId, x, y, z);
                    Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, x, y, z);
                    if (blockRef != null && chunkStore.getComponent(blockRef, ClaimedBlockComponent.getComponentType()) != null) {
                        claimed++;
                        DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Scan] Block at (%d,%d,%d) is claimed -- skipping.", x, y, z);
                        continue;
                    }
                    DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Scan] Returning block id=%d at (%d,%d,%d).", blockId, x, y, z);
                    return new Vector3i(x, y, z);
                }
            }
        }
        DebugLog.info(DebugCategory.MINER_JOB,
                "[MinerJob:Scan] Done -- checked=%d nonAir=%d claimed=%d -> no eligible block. origin=%s size=%d.",
                checkedTotal, nonAir, claimed, origin, size);
        return null;
    }

    public static Vector3d blockCenter(Vector3i block) {
        return new Vector3d(block.x + 0.5, block.y, block.z + 0.5);
    }
}
