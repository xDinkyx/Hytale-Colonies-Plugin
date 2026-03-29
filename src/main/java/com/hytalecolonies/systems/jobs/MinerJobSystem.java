package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.components.world.ClaimedBlockComponent;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hytalecolonies.utils.ColonistToolUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Miner-specific job system. Handles the {@link JobState#Idle},
 * {@link JobState#Working}, and {@link JobState#CollectingDrops} transitions
 * for colonists with a {@link MinerJobComponent}.
 *
 * <ul>
 *   <li>{@link JobState#Idle} — checks the miner has a pickaxe and shovel,
 *       computes the mine origin on first use, finds the next solid block in
 *       the shaft top-down, and transitions to {@link JobState#TravelingToJob}.</li>
 *   <li>{@link JobState#Working} — detects when the NPC role's
 *       {@code HarvestBlock} action breaks the current target block, then either
 *       moves on to the next block in the shaft or yields to
 *       {@link JobState#CollectingDrops} once the per-run quota is reached.</li>
 *   <li>{@link JobState#CollectingDrops} — waits a short time for dropped items
 *       to be picked up by {@link ColonistItemPickupSystem}, then transitions to
 *       {@link JobState#DeliveringItems}.</li>
 * </ul>
 *
 * <p>Travel legs ({@link JobState#TravelingToJob} / {@link JobState#TravelingHome})
 * are handled by {@link ColonistMovementSystem}.
 * Item delivery is handled by {@link ColonistDeliverySystem}.
 *
 * <p>TODO (phase 2): mine navigation (entering/leaving the excavated hole),
 * per-block tool compatibility pre-check, multi-miner block claiming to avoid
 * two miners targeting the same block simultaneously, mine layout templates.
 */
public class MinerJobSystem extends DelayedEntitySystem<EntityStore> {

    /**
     * GatherType that identifies a pickaxe-class tool.
     * The miner must have at least one pickaxe in inventory before leaving the workstation.
     */
    private static final String GATHER_TYPE_PICKAXE = "Rocks";

    /**
     * GatherType that identifies a shovel-class tool.
     * The miner must have at least one shovel in inventory before leaving the workstation.
     */
    private static final String GATHER_TYPE_SHOVEL = "Soils";

    /** Time (ms) the colonist lingers after finishing a mining run, giving items time to settle. */
    private static final long COLLECTING_DROPS_DURATION_MS = 5_000L;

    private final Query<EntityStore> query = Query.and(
            JobComponent.getComponentType(),
            MinerJobComponent.getComponentType()
    );

    public MinerJobSystem() {
        super(2.0f); // Same cadence as the other job/movement systems.
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        JobComponent job = archetypeChunk.getComponent(index, JobComponent.getComponentType());
        MinerJobComponent miner = archetypeChunk.getComponent(index, MinerJobComponent.getComponentType());
        assert job != null && miner != null;

        Ref<EntityStore> colonistRef = archetypeChunk.getReferenceTo(index);
        JobState state = job.getCurrentTask();

        DebugLog.fine(DebugCategory.MINER_JOB,
                "[MinerJob] state=%s workStation=%s blocksMinedThisRun=%d",
                state, job.getWorkStationBlockPosition(), miner.blocksMinedThisRun);

        if (state == null || state == JobState.Idle) {
            handleIdle(colonistRef, job, miner, commandBuffer, store);
        } else if (state == JobState.Working) {
            handleWorking(colonistRef, job, miner, commandBuffer, store);
        } else if (state == JobState.CollectingDrops) {
            handleCollectingDrops(colonistRef, job, commandBuffer);
        }
    }

    // ===== State handlers =====

    private void handleIdle(Ref<EntityStore> ref, JobComponent job, MinerJobComponent miner,
                             CommandBuffer<EntityStore> commandBuffer, Store<EntityStore> store) {
        Vector3i workStationPos = job.getWorkStationBlockPosition();
        if (workStationPos == null) return;

        World world = store.getExternalData().getWorld();

        // Look up the workstation to read mine configuration.
        Ref<ChunkStore> wsRef = BlockModule.getBlockEntity(world, workStationPos.x, workStationPos.y, workStationPos.z);
        WorkStationComponent workStation = wsRef != null
                ? wsRef.getStore().getComponent(wsRef, WorkStationComponent.getComponentType())
                : null;
        if (workStation == null) {
            DebugLog.warning(DebugCategory.MINER_JOB,
                    "[MinerJob] Idle — workstation block entity not found at %s.", workStationPos);
            return;
        }

        // Require both a pickaxe and a shovel before leaving the workstation.
        LivingEntity colonist = (LivingEntity) EntityUtils.getEntity(ref, store);
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

        // Reset the per-run counter at the start of each new run.
        miner.blocksMinedThisRun = 0;

        Vector3i nextBlock = findNextMineBlock(workStation, world);
        if (nextBlock == null) {
            DebugLog.fine(DebugCategory.MINER_JOB,
                    "[MinerJob] Idle — no solid/unclaimed blocks remain in mine shaft at %s. Waiting.",
                    workStation.mineOrigin);
            return;
        }

        // Atomically claim the block and assign targets on the world thread.
        // world.execute() runs sequentially between ticks so two miners finding the
        // same block in the same tick will serialize: the first claims it, the second
        // sees claimBlock() return false and stays Idle.
        EntityStore entityStore = world.getEntityStore();
        world.execute(() -> {
            // Re-validate colonist is still Idle.
            JobComponent liveJob = entityStore.getStore().getComponent(ref, JobComponent.getComponentType());
            if (liveJob == null || liveJob.getCurrentTask() != JobState.Idle) return;

            UUIDComponent uuidComp = entityStore.getStore().getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComp == null) return;

            if (!ClaimBlockUtil.claimBlock(world, nextBlock, uuidComp.getUuid(), "Mine")) {
                DebugLog.fine(DebugCategory.MINER_JOB,
                        "[MinerJob] Could not claim mine block %s (already taken) — staying Idle.", nextBlock);
                return;
            }

            entityStore.getStore().addComponent(ref, JobTargetComponent.getComponentType(), new JobTargetComponent(nextBlock));
            MoveToTargetComponent existingMove = entityStore.getStore().getComponent(ref, MoveToTargetComponent.getComponentType());
            if (existingMove != null) {
                existingMove.target = blockCenter(nextBlock);
            } else {
                entityStore.getStore().addComponent(ref, MoveToTargetComponent.getComponentType(),
                        new MoveToTargetComponent(blockCenter(nextBlock)));
            }
            liveJob.setCurrentTask(JobState.TravelingToJob);
            DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob] Claimed mine block at %s — heading there.", nextBlock);
        });
    }

    private void handleWorking(Ref<EntityStore> ref, JobComponent job, MinerJobComponent miner,
                               CommandBuffer<EntityStore> commandBuffer, Store<EntityStore> store) {
        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jobTarget == null || jobTarget.targetPosition == null) {
            job.setCurrentTask(JobState.Idle);
            return;
        }

        Vector3i targetPos = jobTarget.targetPosition;
        World world = store.getExternalData().getWorld();

        // Block not broken yet — NPC role's HarvestBlock action handles per-tick damage.
        if (world.getBlock(targetPos.x, targetPos.y, targetPos.z) != 0) return;

        // Look up the workstation for quota and mine config.
        Vector3i workStationPos = job.getWorkStationBlockPosition();
        if (workStationPos == null) { job.setCurrentTask(JobState.Idle); return; }
        Ref<ChunkStore> wsRef = BlockModule.getBlockEntity(world, workStationPos.x, workStationPos.y, workStationPos.z);
        WorkStationComponent workStation = wsRef != null
                ? wsRef.getStore().getComponent(wsRef, WorkStationComponent.getComponentType())
                : null;
        if (workStation == null) { job.setCurrentTask(JobState.Idle); return; }

        miner.blocksMinedThisRun++;
        DebugLog.info(DebugCategory.MINER_JOB,
                "[MinerJob] Block at %s mined (%d/%d this run).", targetPos, miner.blocksMinedThisRun, workStation.blocksPerRun);

        boolean quotaReached = miner.blocksMinedThisRun >= workStation.blocksPerRun;
        // Find the next block optimistically in the tick thread (claimed-block filter prevents
        // double-assignments, and world.execute() atomicity handles same-tick races).
        final Vector3i nextBlock = quotaReached ? null : findNextMineBlock(workStation, world);
        final boolean goCollect = quotaReached || nextBlock == null;
        final Vector3i finalTargetPos = targetPos;
        EntityStore entityStore = world.getEntityStore();

        world.execute(() -> {
            // Release the claim on the block we just broke.
            ClaimBlockUtil.unclaimBlock(world, finalTargetPos);

            JobComponent liveJob = entityStore.getStore().getComponent(ref, JobComponent.getComponentType());
            if (liveJob == null) return;

            if (goCollect) {
                JobTargetComponent jt = entityStore.getStore().getComponent(ref, JobTargetComponent.getComponentType());
                if (jt != null) jt.setTargetPosition(null);
                liveJob.collectingDropsSince = System.currentTimeMillis();
                liveJob.setCurrentTask(JobState.CollectingDrops);
                DebugLog.info(DebugCategory.MINER_JOB, quotaReached
                        ? "[MinerJob] Run quota reached — collecting drops."
                        : "[MinerJob] Mine exhausted mid-run — collecting drops.");
            } else {
                UUIDComponent uuidComp = entityStore.getStore().getComponent(ref, UUIDComponent.getComponentType());
                if (uuidComp == null || !ClaimBlockUtil.claimBlock(world, nextBlock, uuidComp.getUuid(), "Mine")) {
                    DebugLog.fine(DebugCategory.MINER_JOB,
                            "[MinerJob] Could not claim next mine block %s — going Idle.", nextBlock);
                    liveJob.setCurrentTask(JobState.Idle);
                    return;
                }
                JobTargetComponent jt = entityStore.getStore().getComponent(ref, JobTargetComponent.getComponentType());
                if (jt != null) {
                    jt.setTargetPosition(nextBlock);
                } else {
                    entityStore.getStore().addComponent(ref, JobTargetComponent.getComponentType(),
                            new JobTargetComponent(nextBlock));
                }
                MoveToTargetComponent mt = entityStore.getStore().getComponent(ref, MoveToTargetComponent.getComponentType());
                if (mt != null) {
                    mt.target = blockCenter(nextBlock);
                } else {
                    entityStore.getStore().addComponent(ref, MoveToTargetComponent.getComponentType(),
                            new MoveToTargetComponent(blockCenter(nextBlock)));
                }
                liveJob.setCurrentTask(JobState.TravelingToJob);
                DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob] Claimed next mine block at %s — heading there.", nextBlock);
            }
        });
    }

    private void handleCollectingDrops(Ref<EntityStore> ref, JobComponent job,
                                       CommandBuffer<EntityStore> commandBuffer) {
        long elapsedMs = System.currentTimeMillis() - job.collectingDropsSince;
        if (elapsedMs < COLLECTING_DROPS_DURATION_MS) {
            DebugLog.fine(DebugCategory.MINER_JOB, "[MinerJob] Collecting drops — %.1f s remaining.",
                    (COLLECTING_DROPS_DURATION_MS - elapsedMs) / 1000.0);
            return;
        }

        DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob] Done collecting drops — heading to deliver items.");
        job.deliveryContainerPosition = null; // Clear stale cache so ColonistDeliverySystem scans fresh.
        job.setCurrentTask(JobState.DeliveringItems);
    }

    // ===== Helpers =====

    /**
     * Scans the mine shaft top-down and returns the first solid, unclaimed block found,
     * or {@code null} if the entire shaft is already excavated or all remaining blocks
     * are claimed by other miners.
     *
     * <p>Scan order: layer by layer from top (workstation Y) downward, completing each
     * layer fully before descending to the next.
     *
     * <p>TODO (phase 2): smarter scan order; skip blocks the colonist's tool cannot break.
     */
    @Nullable
    private static Vector3i findNextMineBlock(WorkStationComponent workStation, World world) {
        Vector3i origin = workStation.mineOrigin;
        if (origin == null) return null;
        int size = workStation.mineSize;
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        for (int dy = 0; dy < size; dy++) {       // dy=0 = top layer (workstation Y)
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    int x = origin.x + dx;
                    int y = origin.y - dy;         // descend into the shaft
                    int z = origin.z + dz;
                    if (world.getBlock(x, y, z) == 0) continue;
                    // Skip blocks already claimed by another miner.
                    Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, x, y, z);
                    if (blockRef != null && chunkStore.getComponent(blockRef, ClaimedBlockComponent.getComponentType()) != null) continue;
                    return new Vector3i(x, y, z);
                }
            }
        }
        return null;
    }

    /** Returns the XZ-centre, ground-level point of a block position for navigation. */
    private static Vector3d blockCenter(Vector3i block) {
        return new Vector3d(block.x + 0.5, block.y, block.z + 0.5);
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return query;
    }
}
