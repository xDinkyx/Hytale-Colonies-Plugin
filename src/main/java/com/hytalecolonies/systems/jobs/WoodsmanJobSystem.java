package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.WoodsmanJobComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.components.world.HarvestableTreeComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

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
 *   <li>{@link JobState#Working} — performs the harvest (TODO), unmarks the
 *       claimed tree so other colonists may take it, dispatches the return
 *       navigation, and transitions to {@link JobState#TravelingHome}.</li>
 * </ul>
 *
 * The {@link JobState#TravelingToJob} and {@link JobState#TravelingHome} legs
 * are handled entirely by {@link ColonistMovementSystem}.
 */
public class WoodsmanJobSystem extends DelayedEntitySystem<EntityStore> {

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
        WoodsmanJobComponent woodsman = archetypeChunk.getComponent(index, WoodsmanJobComponent.getComponentType());
        assert job != null && woodsman != null;

        Ref<EntityStore> colonistRef = archetypeChunk.getReferenceTo(index);
        JobState state = job.getCurrentTask();

        DebugLog.log(DebugCategory.WOODSMAN_JOB, "[WoodsmanJob] state=%s workStation=%s",
                state, job.getWorkStationBlockPosition());

        if (state == null || state == JobState.Idle) {
            handleIdle(colonistRef, job, woodsman, commandBuffer, store);
        } else if (state == JobState.Working) {
            handleWorking(colonistRef, job, commandBuffer, store);
        }
    }

    // ===== State handlers =====

    private void handleIdle(Ref<EntityStore> ref, JobComponent job, WoodsmanJobComponent woodsman,
                             CommandBuffer<EntityStore> commandBuffer, Store<EntityStore> store) {
        Vector3i workStationPos = job.getWorkStationBlockPosition();
        if (workStationPos == null) return;

        World world = store.getExternalData().getWorld();
        Vector3i nearestTree = findNearestAvailableTree(woodsman, workStationPos, world);
        if (nearestTree == null) {
            DebugLog.log(DebugCategory.WOODSMAN_JOB,
                    "[WoodsmanJob] Idle — no available trees within radius %.1f of workstation %s (allowedTypes=%s).",
                    woodsman.treeSearchRadius, workStationPos, woodsman.allowedTreeTypes);
            return;
        }

        // Claim the tree to prevent other colonists from taking it.
        Ref<ChunkStore> treeBlockRef = BlockModule.getBlockEntity(world, nearestTree.x, nearestTree.y, nearestTree.z);
        if (treeBlockRef == null) {
            DebugLog.log(DebugCategory.WOODSMAN_JOB, Level.WARNING, "[WoodsmanJob] Found tree candidate at %s but BlockEntity is null.", nearestTree);
            return;
        }
        HarvestableTreeComponent tree = treeBlockRef.getStore().getComponent(treeBlockRef, HarvestableTreeComponent.getComponentType());
        if (tree == null || tree.isMarkedForHarvest()) {
            DebugLog.log(DebugCategory.WOODSMAN_JOB, "[WoodsmanJob] Tree at %s already claimed or missing — skipping.", nearestTree);
            return; // Race: another colonist got there first.
        }
        tree.markForHarvest();

        // Add a JobTargetComponent so ColonistMovementSystem can drive travel to the tree.
        commandBuffer.addComponent(ref, JobTargetComponent.getComponentType(), new JobTargetComponent(nearestTree));

        Vector3d treeTarget = new Vector3d(nearestTree.x + 0.5, nearestTree.y, nearestTree.z + 0.5);
        commandBuffer.addComponent(ref, MoveToTargetComponent.getComponentType(), new MoveToTargetComponent(treeTarget));

        job.setCurrentTask(JobState.TravelingToJob);
        DebugLog.log(DebugCategory.WOODSMAN_JOB, Level.INFO, "[WoodsmanJob] Heading to tree at %s.", nearestTree);
    }

    private void handleWorking(Ref<EntityStore> ref, JobComponent job,
                               CommandBuffer<EntityStore> commandBuffer, Store<EntityStore> store) {
        Vector3i workStationPos = job.getWorkStationBlockPosition();
        if (workStationPos == null) return;

        // Unmark the claimed tree so other colonists may claim it while this one heads home.
        unmarkClaimedTree(ref, store);

        // Clear targetPosition so StaleMarkCleanupSystem does not count this as an active claim.
        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jobTarget != null) {
            jobTarget.setTargetPosition(null);
        }

        // TODO: Implement actual tree harvesting (break blocks, drop items) before returning.

        Vector3d wsTarget = new Vector3d(workStationPos.x + 0.5, workStationPos.y, workStationPos.z + 0.5);
        commandBuffer.addComponent(ref, MoveToTargetComponent.getComponentType(), new MoveToTargetComponent(wsTarget));

        job.setCurrentTask(JobState.TravelingHome);
        DebugLog.log(DebugCategory.WOODSMAN_JOB, Level.INFO, "[WoodsmanJob] Harvesting done, returning home to %s.", workStationPos);
    }

    // ===== Helpers =====

    /**
     * Unmarks the tree claimed by the given colonist, if any.
     * Reads the target position from the colonist's {@link JobTargetComponent}.
     */
    static void unmarkClaimedTree(Ref<EntityStore> ref, Store<EntityStore> store) {
        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jobTarget == null || jobTarget.targetPosition == null) return;
        Vector3i treePos = jobTarget.targetPosition;
        World world = store.getExternalData().getWorld();
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
    private static Vector3i findNearestAvailableTree(WoodsmanJobComponent woodsman,
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
                if (!woodsman.allowedTreeTypes.contains(tree.getTreeTypeKey())) { wrongTypeTrees[0]++; continue; }
                candidates.add(tree.getBasePosition());
            }
        });

        DebugLog.log(DebugCategory.WOODSMAN_JOB,
                "[WoodsmanJob] Tree scan: total=%d, marked=%d, wrongType=%d, candidates=%d",
                totalTrees[0], markedTrees[0], wrongTypeTrees[0], candidates.size());

        Vector3i nearest = null;
        double nearestDistSq = woodsman.treeSearchRadius * woodsman.treeSearchRadius;
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
            DebugLog.log(DebugCategory.WOODSMAN_JOB,
                    "[WoodsmanJob] Closest tree outside radius: %s at dist=%.1f (radius=%.1f).",
                    closestOutsideRadius, Math.sqrt(closestOutsideRadiusDist), woodsman.treeSearchRadius);
        }
        return nearest;
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return query;
    }
}
