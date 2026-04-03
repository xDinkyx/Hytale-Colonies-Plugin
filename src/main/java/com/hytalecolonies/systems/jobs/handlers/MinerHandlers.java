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
import com.hytalecolonies.systems.jobs.JobStateHandler;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hytalecolonies.utils.ColonistToolUtil;
import com.hytalecolonies.utils.JobNavigationUtil;
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
        DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] Handler running.");

        Vector3i workStationPos = ctx.job.getWorkStationBlockPosition();
        if (workStationPos == null) {
            DebugLog.warning(DebugCategory.MINER_JOB, "[MinerJob:Idling] No workstation position on JobComponent.");
            return;
        }
        DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] Workstation pos = %s.", workStationPos);

        // Always keep NavTarget pointed at the workstation while idling.
        // This ensures the JSON ReadPosition sensor fires so the fidget animation plays,
        // and returns the miner if they drifted away from the workstation.
        ctx.world.execute(() ->
            JobNavigationUtil.dispatchNavigation(ctx.world.getEntityStore().getStore(), ctx.colonistRef, workStationPos)
        );

        World world = ctx.world;
        Ref<ChunkStore> wsRef = BlockModule.getBlockEntity(world, workStationPos.x, workStationPos.y, workStationPos.z);
        WorkStationComponent workStation = wsRef != null
                ? wsRef.getStore().getComponent(wsRef, WorkStationComponent.getComponentType())
                : null;
        if (workStation == null) {
            DebugLog.warning(DebugCategory.MINER_JOB, "[MinerJob:Idling] WorkStationComponent not found at %s (wsRef=%s).", workStationPos, wsRef);
            return;
        }
        DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] WorkStationComponent found. mineOffsetZ=%d mineSize=%d blocksPerRun=%d.",
                workStation.mineOffsetZ, workStation.mineSize, workStation.blocksPerRun);

        MinerJobComponent miner = ctx.store.getComponent(ctx.colonistRef, MinerJobComponent.getComponentType());
        if (miner == null) {
            DebugLog.warning(DebugCategory.MINER_JOB, "[MinerJob:Idling] No MinerJobComponent on colonist — is this actually a miner?");
            return;
        }
        DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] MinerJobComponent found. blocksMinedThisRun=%d.", miner.blocksMinedThisRun);

        LivingEntity colonist = (LivingEntity) EntityUtils.getEntity(ctx.colonistRef, ctx.store);
        if (colonist == null) {
            DebugLog.warning(DebugCategory.MINER_JOB, "[MinerJob:Idling] Could not resolve LivingEntity.");
            return;
        }
        boolean hasPickaxe = ColonistToolUtil.hasToolForGatherType(colonist.getInventory(), GATHER_TYPE_PICKAXE, 0);
        boolean hasShovel = ColonistToolUtil.hasToolForGatherType(colonist.getInventory(), GATHER_TYPE_SHOVEL, 0);
        DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] Tool check — pickaxe=%b shovel=%b.", hasPickaxe, hasShovel);
        if (!hasPickaxe || !hasShovel) {
            DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] Missing tools — skipping until equipped.");
            return;
        }

        // Compute mine origin once per workstation lifetime.
        if (workStation.mineOrigin == null) {
            workStation.mineOrigin = new Vector3i(
                    workStationPos.x,
                    workStationPos.y,
                    workStationPos.z + workStation.mineOffsetZ
            );
            DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] Mine origin set to %s.", workStation.mineOrigin);
        }

        miner.blocksMinedThisRun = 0;

        Vector3i nextBlock = findNextMineBlock(workStation, world);
        if (nextBlock == null) {
            DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] No solid/unclaimed blocks in shaft at %s - flagging no work.", workStation.mineOrigin);
            ctx.job.workAvailable = false;
            return;
        }
        ctx.job.workAvailable = true;
        DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] Found next block at %s — claiming.", nextBlock);

        EntityStore entityStore = world.getEntityStore();
        final Vector3i targetBlock = nextBlock;
        world.execute(() -> {
            JobComponent liveJob = entityStore.getStore().getComponent(ctx.colonistRef, JobComponent.getComponentType());
            if (liveJob == null || liveJob.getCurrentTask() != JobState.Idling) {
                DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] world.execute guard — state is now %s, skipping.",
                        liveJob != null ? liveJob.getCurrentTask() : "null");
                return;
            }

            UUIDComponent uuidComp = entityStore.getStore().getComponent(ctx.colonistRef, UUIDComponent.getComponentType());
            if (uuidComp == null) {
                DebugLog.warning(DebugCategory.MINER_JOB, "[MinerJob:Idling] No UUIDComponent in world.execute.");
                return;
            }

            if (!ClaimBlockUtil.claimBlock(world, targetBlock, uuidComp.getUuid(), "Mine")) {
                DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] Block %s already claimed — staying Idling.", targetBlock);
                return;
            }

            // Update or add JobTargetComponent — may already exist if a prior world.execute crashed.
            JobTargetComponent existingTarget = entityStore.getStore().getComponent(ctx.colonistRef, JobTargetComponent.getComponentType());
            if (existingTarget != null) {
                existingTarget.targetPosition = targetBlock;
            } else {
                entityStore.getStore().addComponent(ctx.colonistRef, JobTargetComponent.getComponentType(), new JobTargetComponent(targetBlock));
            }

            // Set state BEFORE adding MoveToTargetComponent so that if PathFindingSystem
            // throws, the state is already TravelingToJob and the Idling handler won't re-fire.
            liveJob.setCurrentTask(JobState.TravelingToJob);
            DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Idling] Claimed %s — state -> TravelingToJob.", targetBlock);

            MoveToTargetComponent existingMove = entityStore.getStore().getComponent(ctx.colonistRef, MoveToTargetComponent.getComponentType());
            if (existingMove != null) {
                existingMove.target = blockCenter(targetBlock);
            } else {
                entityStore.getStore().addComponent(ctx.colonistRef, MoveToTargetComponent.getComponentType(),
                        new MoveToTargetComponent(blockCenter(targetBlock)));
            }
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
            ctx.job.setCurrentTask(JobState.Idling);
            return;
        }

        Vector3i targetPos = jobTarget.targetPosition;
        World world = ctx.world;

        // Block still standing — NPC role handles per-tick damage.
        if (world.getBlock(targetPos.x, targetPos.y, targetPos.z) != 0) return;

        Vector3i workStationPos = ctx.job.getWorkStationBlockPosition();
        if (workStationPos == null) { ctx.job.setCurrentTask(JobState.Idling); return; }
        Ref<ChunkStore> wsRef = BlockModule.getBlockEntity(world, workStationPos.x, workStationPos.y, workStationPos.z);
        WorkStationComponent workStation = wsRef != null
                ? wsRef.getStore().getComponent(wsRef, WorkStationComponent.getComponentType())
                : null;
        if (workStation == null) { ctx.job.setCurrentTask(JobState.Idling); return; }

        MinerJobComponent miner = ctx.store.getComponent(ctx.colonistRef, MinerJobComponent.getComponentType());
        if (miner == null) { ctx.job.setCurrentTask(JobState.Idling); return; }

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
                            "[MinerJob] Could not claim next mine block %s — going Idling.", nextBlock);
                    liveJob.setCurrentTask(JobState.Idling);
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
                        DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Scan] Block at (%d,%d,%d) is claimed — skipping.", x, y, z);
                        continue;
                    }
                    DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Scan] Returning block id=%d at (%d,%d,%d).", blockId, x, y, z);
                    return new Vector3i(x, y, z);
                }
            }
        }
        DebugLog.info(DebugCategory.MINER_JOB,
                "[MinerJob:Scan] Done — checked=%d nonAir=%d claimed=%d → no eligible block. origin=%s size=%d.",
                checkedTotal, nonAir, claimed, origin, size);
        return null;
    }

    public static Vector3d blockCenter(Vector3i block) {
        return new Vector3d(block.x + 0.5, block.y, block.z + 0.5);
    }
}
