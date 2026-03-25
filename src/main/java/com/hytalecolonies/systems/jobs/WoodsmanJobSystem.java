package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.debug.DebugTiming;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.WoodsmanJobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.components.world.HarvestableTreeComponent;
import com.hytalecolonies.utils.ColonistToolUtil;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hytalecolonies.systems.treescan.TreeDetector;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Woodsman-specific job system. Handles the {@link JobState#Idle} and
 * {@link JobState#Working} transitions for colonists with a
 * {@link WoodsmanJobComponent}.
 *
 * <ul>
 *   <li>{@link JobState#Idle}    — scans for the nearest free tree, claims it,
 *       adds a {@link JobTargetComponent} so {@link ColonistMovementSystem}
 *       can drive travel, dispatches initial navigation, and transitions to
 *       {@link JobState#TravelingToJob}.</li>
 *   <li>{@link JobState#Working} — calls {@code BlockHarvestUtils.performBlockDamage}
 *       on the base trunk block each tick, accumulating block health damage just
 *       as a player would. When the block breaks (returns {@code true}) Hytale's
 *       physics cascade the rest of the tree. The claim is cleared and the system
 *       transitions to {@link JobState#CollectingDrops}.</li>
 *   <li>{@link JobState#CollectingDrops} — a temporary 5-second wait that gives
 *       the colonist time to pick up fallen items before heading home and
 *       transitioning to {@link JobState#TravelingHome}.</li>
 * </ul>
 *
 * The {@link JobState#TravelingToJob} and {@link JobState#TravelingHome} legs
 * are handled entirely by {@link ColonistMovementSystem}.
 */
public class WoodsmanJobSystem extends DelayedEntitySystem<EntityStore> {

    /**
     * GatherType the woodsman must have a tool for before leaving the workstation.
     * Any quality tier (≥0) is accepted.
     */
    private static final String REQUIRED_GATHER_TYPE = "Woods";

    private final Query<EntityStore> query = Query.and(
            JobComponent.getComponentType(),
            WoodsmanJobComponent.getComponentType()
    );

    public WoodsmanJobSystem() {
        super(2.0f); // Same cadence as ColonistMovementSystem.
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        JobComponent job = archetypeChunk.getComponent(index, JobComponent.getComponentType());
        assert job != null;

        Ref<EntityStore> colonistRef = archetypeChunk.getReferenceTo(index);
        JobState state = job.getCurrentTask();

        DebugLog.fine(DebugCategory.WOODSMAN_JOB, "[WoodsmanJob] state=%s workStation=%s",
                state, job.getWorkStationBlockPosition());

        if (state == null || state == JobState.Idle) {
            handleIdle(colonistRef, job, commandBuffer, store);
        } else if (state == JobState.Working) {
            handleWorking(colonistRef, job, commandBuffer, store);
        } else if (state == JobState.CollectingDrops) {
            handleCollectingDrops(colonistRef, job, commandBuffer);
        }
    }

    // ===== State handlers =====

    /**
     * How long (ms) the colonist lingers at the chop site before heading home.
     * Actual item pickup is handled continuously by {@link ColonistItemPickupSystem};
     * this is just time for physics-dropped items to settle and become eligible.
     */
    private static final long COLLECTING_DROPS_DURATION_MS = 5_000L;

    /**
     * Waits {@link #COLLECTING_DROPS_DURATION_MS} at the drop site then dispatches
     * the colonist home. Pickup itself is handled by {@link ColonistItemPickupSystem}.
     */
    private void handleCollectingDrops(@Nonnull Ref<EntityStore> ref, @Nonnull JobComponent job,
                                       @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        long elapsedMs = System.currentTimeMillis() - job.collectingDropsSince;
        if (elapsedMs < COLLECTING_DROPS_DURATION_MS) {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB, "[WoodsmanJob] Collecting drops — %.1f s remaining.",
                    (COLLECTING_DROPS_DURATION_MS - elapsedMs) / 1000.0);
            return;
        }

        DebugLog.info(DebugCategory.WOODSMAN_JOB, "[WoodsmanJob] Done collecting drops — heading to deliver items.");
        job.deliveryContainerPosition = null; // Clear any stale cache so ColonistDeliverySystem scans fresh.
        job.setCurrentTask(JobState.DeliveringItems);
    }

    private void handleIdle(Ref<EntityStore> ref, JobComponent job,
                             CommandBuffer<EntityStore> commandBuffer, Store<EntityStore> store) {
        Vector3i workStationPos = job.getWorkStationBlockPosition();
        if (workStationPos == null) return;

        World world = store.getExternalData().getWorld();

        // Look up the workstation block entity to read job configuration.
        Ref<ChunkStore> wsRef = BlockModule.getBlockEntity(world, workStationPos.x, workStationPos.y, workStationPos.z);
        WorkStationComponent workStation = wsRef != null
                ? wsRef.getStore().getComponent(wsRef, WorkStationComponent.getComponentType())
                : null;
        if (workStation == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB,
                    "[WoodsmanJob] Idle — workstation block entity not found at %s.", workStationPos);
            return;
        }

        // Wait at the workstation until the colonist has a suitable tool for the job.
        LivingEntity colonist = (LivingEntity) EntityUtils.getEntity(ref, store);
        if (colonist == null) return;
        if (!ColonistToolUtil.hasToolForGatherType(colonist.getInventory(), REQUIRED_GATHER_TYPE, 0)) {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                    "[WoodsmanJob] Idle — no '%s' tool in inventory. Waiting at workstation.", REQUIRED_GATHER_TYPE);
            return;
        }

        Vector3i nearestTree;
        try (var t = DebugTiming.measure("WoodsmanJob.findNearestAvailableTree@" + workStationPos, 50)) {
            nearestTree = findNearestAvailableTree(workStation, workStationPos, world);
        }
        if (nearestTree == null) {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                    "[WoodsmanJob] Idle — no available trees within radius %.1f of workstation %s.",
                    workStation.treeSearchRadius, workStationPos);
            return;
        }

        // Claim the tree to prevent other colonists from taking it.
        Ref<ChunkStore> treeBlockRef = BlockModule.getBlockEntity(world, nearestTree.x, nearestTree.y, nearestTree.z);
        if (treeBlockRef == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB, "[WoodsmanJob] Found tree candidate at %s but BlockEntity is null.", nearestTree);
            return;
        }
        HarvestableTreeComponent tree = treeBlockRef.getStore().getComponent(treeBlockRef, HarvestableTreeComponent.getComponentType());
        if (tree == null || tree.isMarkedForHarvest()) {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB, "[WoodsmanJob] Tree at %s already claimed or missing — skipping.", nearestTree);
            return; // Race: another colonist got there first.
        }
        tree.markForHarvest();

        // Add a JobTargetComponent so ColonistMovementSystem can drive travel to the tree.
        commandBuffer.addComponent(ref, JobTargetComponent.getComponentType(), new JobTargetComponent(nearestTree));

        Vector3d treeTarget = new Vector3d(nearestTree.x + 0.5, nearestTree.y, nearestTree.z + 0.5);
        commandBuffer.addComponent(ref, MoveToTargetComponent.getComponentType(), new MoveToTargetComponent(treeTarget));

        job.setCurrentTask(JobState.TravelingToJob);
        DebugLog.info(DebugCategory.WOODSMAN_JOB, "[WoodsmanJob] Heading to tree at %s.", nearestTree);
    }

    /**
     * Monitors the {@link JobState#Working} state.
     *
     * <p>Actual block damage and tool equipping are handled per-tick by the NPC role
     * instructions ({@code SensorJobTarget + EquipBestTool + HarvestBlock}). This method
     * runs on the 2-second cadence only to detect when the target block has been broken
     * and transition to the next base block (wide multi-block bases) or
     * {@link JobState#CollectingDrops} when all base-level trunk blocks are gone.
     */
    private void handleWorking(Ref<EntityStore> ref, JobComponent job,
                               CommandBuffer<EntityStore> commandBuffer, Store<EntityStore> store) {
        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jobTarget == null || jobTarget.targetPosition == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB,
                    "[WoodsmanJob] Working — no JobTargetComponent or target is null, resetting to Idle.");
            unmarkClaimedTree(ref, store);
            job.setCurrentTask(JobState.Idle);
            return;
        }

        Vector3i treeBase = jobTarget.targetPosition;
        World world = store.getExternalData().getWorld();
        int blockId = world.getBlock(treeBase);

        DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                "[WoodsmanJob] Working — target=%s blockId=%d (0=broken).", treeBase, blockId);

        // Block still standing — the NPC role's SensorJobTarget + HarvestBlock actions handle damage per-tick.
        if (blockId != 0) return;

        // blockId == 0: block was broken by the NPC role's HarvestBlock action.
        WoodsmanJobComponent woodsman = store.getComponent(ref, WoodsmanJobComponent.getComponentType());
        DebugLog.info(DebugCategory.WOODSMAN_JOB,
                "[WoodsmanJob] Block at %s is broken — scanning for adjacent base blocks.", treeBase);
        Vector3i nextBase = (woodsman != null)
                ? findNextBaseBlock(treeBase, woodsman.allowedTreeTypes, world)
                : null;

        if (nextBase != null) {
                // Wide multi-block base — travel to the next connected base block.
                DebugLog.info(DebugCategory.WOODSMAN_JOB,
                        "[WoodsmanJob] Found adjacent base block at %s — traveling there (TravelingToJob).",
                        nextBase);
                jobTarget.setTargetPosition(nextBase);
                // Keep WoodsmanMovementSystem's travel detection in sync.
                if (woodsman != null) woodsman.targetTreePosition = nextBase;
                boolean hadMove = store.getComponent(ref, MoveToTargetComponent.getComponentType()) != null;
                MoveToTargetComponent newMove = new MoveToTargetComponent(
                        new Vector3d(nextBase.x + 0.5, nextBase.y, nextBase.z + 0.5));
                if (hadMove) {
                    commandBuffer.replaceComponent(ref, MoveToTargetComponent.getComponentType(), newMove);
                } else {
                    commandBuffer.addComponent(ref, MoveToTargetComponent.getComponentType(), newMove);
                }
                DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                        "[WoodsmanJob] MoveToTarget %s (hadComponent=%b).", nextBase, hadMove);
                // Transition to TravelingToJob so both movement systems drive the colonist
                // to the next block; on arrival they will transition back to Working.
                job.setCurrentTask(JobState.TravelingToJob);
        } else {
                // All base-level trunk blocks gone — collect drops.
                DebugLog.info(DebugCategory.WOODSMAN_JOB,
                        "[WoodsmanJob] No further base blocks found — transitioning to CollectingDrops.");
                unmarkClaimedTree(ref, store);
                jobTarget.setTargetPosition(null);
                job.collectingDropsSince = System.currentTimeMillis();
                job.setCurrentTask(JobState.CollectingDrops);
        }
    }

    /**
     * Performs a horizontal (XZ-plane) flood-fill starting adjacent to {@code brokenPos}
     * at the same Y level, looking for any still-standing tree-wood blocks that were part
     * of a wide multi-block tree base.
     *
     * <p>Only 4-connected horizontal neighbours are visited (±X, ±Z). This keeps the
     * search focused on the base layer and avoids wandering up the trunk.
     *
     * @return the nearest still-standing base block, or {@code null} if none remain
     */
    @Nullable
    private static Vector3i findNextBaseBlock(
            @Nonnull Vector3i brokenPos,
            @Nonnull Set<String> woodKeys,
            @Nonnull World world) {
        int baseY = brokenPos.y;
        Set<Long> visited = new HashSet<>();
        Deque<Vector3i> queue = new ArrayDeque<>();
        visited.add(pack3i(brokenPos));
        // Seed with horizontal neighbours of the broken position.
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            Vector3i neighbour = new Vector3i(brokenPos.x + d[0], baseY, brokenPos.z + d[1]);
            if (visited.add(pack3i(neighbour))) queue.add(neighbour);
        }
        DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                "[WoodsmanJob] findNextBaseBlock starting from %s (Y=%d), queued %d neighbours, woodKeys=%d.",
                brokenPos, baseY, queue.size(), woodKeys.size());

        while (!queue.isEmpty()) {
            Vector3i cur = queue.poll();
            String key = TreeDetector.getBlockKey(world, cur.x, cur.y, cur.z);
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                    "[WoodsmanJob] findNextBaseBlock checking %s — blockKey=%s isWood=%b.",
                    cur, key, key != null && woodKeys.contains(key));
            if (key == null || !woodKeys.contains(key)) continue; // air or non-wood
            // Found a standing base block.
            DebugLog.info(DebugCategory.WOODSMAN_JOB,
                    "[WoodsmanJob] findNextBaseBlock found standing base block at %s (key=%s).", cur, key);
            return cur;
        }
        DebugLog.info(DebugCategory.WOODSMAN_JOB,
                "[WoodsmanJob] findNextBaseBlock — no more standing base blocks adjacent to %s.", brokenPos);
        return null;
    }

    private static long pack3i(Vector3i v) {
        return ((long)(v.x & 0x1FFFFFL) << 42) | ((long)(v.z & 0x1FFFFFL) << 21) | (v.y & 0x1FFFFFL);
    }

    // ===== Helpers =====

    /**
     * Unmarks the tree claimed by the given colonist, if any.
     * Reads the target position from the colonist's {@link JobTargetComponent}.
     */
    static void unmarkClaimedTree(Ref<EntityStore> ref, Store<EntityStore> store) {
        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        unmarkClaimedTree(ref, jobTarget);
    }

    private static void unmarkClaimedTree(Ref<EntityStore> ref, @Nullable JobTargetComponent jobTarget) {
        if (jobTarget == null || jobTarget.targetPosition == null) return;
        Vector3i treePos = jobTarget.targetPosition;
        World world = ref.getStore().getExternalData().getWorld();
        Ref<ChunkStore> treeBlockRef = BlockModule.getBlockEntity(world, treePos.x, treePos.y, treePos.z);
        if (treeBlockRef == null) return;
        HarvestableTreeComponent tree = treeBlockRef.getStore().getComponent(treeBlockRef, HarvestableTreeComponent.getComponentType());
        if (tree != null) {
            tree.setMarkedForHarvest(false);
        }
    }

    /**
     * Returns the nearest available (unmarked, allowed-type) tree within the
     * woodsman's search radius of the workstation, or {@code null} if none.
     */
    @Nullable
    private static Vector3i findNearestAvailableTree(WorkStationComponent workStation,
                                                      Vector3i workStationPos, World world) {
        List<Vector3i> candidates = new ArrayList<>();
        int[] totalTrees = {0}, markedTrees = {0}, wrongTypeTrees = {0};
        Query<ChunkStore> treeQuery = Query.and(HarvestableTreeComponent.getComponentType());

        world.getChunkStore().getStore().forEachChunk(treeQuery, (treeChunk, _unused) -> {
            for (int i = 0; i < treeChunk.size(); i++) {
                HarvestableTreeComponent tree = treeChunk.getComponent(i, HarvestableTreeComponent.getComponentType());
                if (tree == null) continue;
                totalTrees[0]++;
                if (tree.isMarkedForHarvest()) { markedTrees[0]++; continue; }
                if (!workStation.getAllowedTreeTypes().contains(tree.getTreeTypeKey())) { wrongTypeTrees[0]++; continue; }
                candidates.add(tree.getBasePosition());
            }
        });

        DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                "[WoodsmanJob] Tree scan: total=%d, marked=%d, wrongType=%d, candidates=%d",
                totalTrees[0], markedTrees[0], wrongTypeTrees[0], candidates.size());

        Vector3i nearest = null;
        double nearestDistSq = workStation.treeSearchRadius * workStation.treeSearchRadius;
        double closestOutsideRadiusDist = Double.MAX_VALUE;
        Vector3i closestOutsideRadius = null;

        for (Vector3i pos : candidates) {
            double dx = pos.x - workStationPos.x;
            double dy = pos.y - workStationPos.y;
            double dz = pos.z - workStationPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = pos;
            } else if (distSq < closestOutsideRadiusDist) {
                closestOutsideRadiusDist = distSq;
                closestOutsideRadius = pos;
            }
        }

        if (nearest == null && closestOutsideRadius != null) {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                    "[WoodsmanJob] Closest tree outside radius: %s at dist=%.1f (radius=%.1f).",
                    closestOutsideRadius, Math.sqrt(closestOutsideRadiusDist), workStation.treeSearchRadius);
        }
        return nearest;
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return query;
    }
}
