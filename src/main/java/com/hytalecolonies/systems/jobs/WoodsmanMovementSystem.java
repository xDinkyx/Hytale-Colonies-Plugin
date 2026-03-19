package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.WoodsmanJobComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.components.world.HarvestableTreeComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives the movement state machine for woodsman colonists.
 *
 * State transitions:
 * - null / Idle     → find nearest free tree → dispatch nav → TravelingToJob
 * - TravelingToJob  → (arrived near tree)    → Working
 * - Working         → dispatch return nav    → TravelingHome
 * - TravelingHome   → (arrived at workstation) → Idle, unmark tree
 */
public class WoodsmanMovementSystem extends DelayedEntitySystem<EntityStore> {

    /** XZ distance to consider a colonist "at" a tree (tree bases can be underground). */
    private static final float TREE_ARRIVAL_XZ = 4.0f;
    /** 3D distance to consider a colonist "at" the workstation. */
    private static final float WORKSTATION_ARRIVAL_3D = 3.5f;
    /** Stuck ticks before forcing state advance while TravelingToJob. */
    private static final int STUCK_TICKS_LIMIT = 5;

    private final Query<EntityStore> query = Query.and(
            JobComponent.getComponentType(),
            WoodsmanJobComponent.getComponentType()
    );

    public WoodsmanMovementSystem() {
        super(2.0f); // Check state every 2 seconds.
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
        TransformComponent transform = store.getComponent(colonistRef, TransformComponent.getComponentType());
        if (transform == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB, "[Woodsman] Colonist has no TransformComponent — skipping.");
            return;
        }

        Vector3d colonistPos = transform.getTransform().getPosition();
        JobState state = job.getCurrentTask();

        DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                "[Woodsman] state=%s pos=(%.1f, %.1f, %.1f) target=%s workStation=%s",
                state, colonistPos.x, colonistPos.y, colonistPos.z,
                woodsman.targetTreePosition, job.getWorkStationBlockPosition());

        if (state == null || state == JobState.Idle) {
            handleIdle(colonistRef, job, woodsman, commandBuffer, store);
        } else if (state == JobState.TravelingToJob) {
            handleTravelingToJob(colonistRef, job, woodsman, colonistPos, commandBuffer);
        } else if (state == JobState.Working) {
            handleWorking(colonistRef, job, woodsman, commandBuffer);
        } else if (state == JobState.TravelingHome) {
            handleTravelingHome(colonistRef, job, woodsman, colonistPos, commandBuffer, store);
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
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                    "[Woodsman] Idle — no available trees found within radius %.1f of workstation %s (allowedTypes=%s).",
                    woodsman.treeSearchRadius, workStationPos, woodsman.allowedTreeTypes);
            return;
        }

        // Claim the tree to prevent other colonists from taking it.
        Ref<ChunkStore> treeBlockRef = BlockModule.getBlockEntity(world, nearestTree.x, nearestTree.y, nearestTree.z);
        if (treeBlockRef == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB, "[Woodsman] Found tree candidate at %s but BlockEntity is null.", nearestTree);
            return;
        }
        HarvestableTreeComponent tree = treeBlockRef.getStore().getComponent(treeBlockRef, HarvestableTreeComponent.getComponentType());
        if (tree == null || tree.isMarkedForHarvest()) {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                    "[Woodsman] Tree at %s already claimed or component missing — skipping.", nearestTree);
            return; // Race: another colonist got there first.
        }
        tree.markForHarvest();

        woodsman.targetTreePosition = nearestTree;

        Vector3d treeTarget = new Vector3d(nearestTree.x + 0.5, nearestTree.y, nearestTree.z + 0.5);
        commandBuffer.addComponent(ref, MoveToTargetComponent.getComponentType(), new MoveToTargetComponent(treeTarget));

        job.setCurrentTask(JobState.TravelingToJob);
        DebugLog.info(DebugCategory.WOODSMAN_JOB, "Woodsman heading to tree at %s", nearestTree);
    }

    private void handleTravelingToJob(Ref<EntityStore> ref, JobComponent job, WoodsmanJobComponent woodsman,
                                      Vector3d colonistPos, CommandBuffer<EntityStore> commandBuffer) {
        Vector3i treePos = woodsman.targetTreePosition;
        if (treePos == null) {
            // Lost target after server restart — reset to Idle.
            job.setCurrentTask(JobState.Idle);
            return;
        }

        double dx = colonistPos.x - (treePos.x + 0.5);
        double dz = colonistPos.z - (treePos.z + 0.5);
        double xzDistSq = dx * dx + dz * dz;
        double xzDist = Math.sqrt(xzDistSq);

        // Stuck detection: track whether position changed since last tick.
        Vector3i currentCell = new Vector3i((int) colonistPos.x, (int) colonistPos.y, (int) colonistPos.z);
        if (currentCell.equals(woodsman.lastKnownPosition)) {
            woodsman.stuckTicks++;
        } else {
            woodsman.stuckTicks = 0;
            woodsman.lastKnownPosition = currentCell;
        }

        boolean arrivedXZ = xzDistSq <= TREE_ARRIVAL_XZ * TREE_ARRIVAL_XZ;
        boolean stuck = woodsman.stuckTicks >= STUCK_TICKS_LIMIT && xzDist <= woodsman.treeSearchRadius;

        DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                "[Woodsman] TravelingToJob — xzDist=%.2f to tree %s (threshold %.1f) stuckTicks=%d.",
                xzDist, treePos, TREE_ARRIVAL_XZ, woodsman.stuckTicks);

        if (arrivedXZ || stuck) {
            if (stuck && !arrivedXZ) {
                DebugLog.info(DebugCategory.WOODSMAN_JOB,
                        "[Woodsman] Stuck near tree at %s (xzDist=%.2f) — advancing to Working.", treePos, xzDist);
            }
            woodsman.stuckTicks = 0;
            woodsman.lastKnownPosition = null;
            job.setCurrentTask(JobState.Working);
            DebugLog.info(DebugCategory.WOODSMAN_JOB, "[Woodsman] Arrived at tree at %s.", treePos);
        }
    }

    private void handleWorking(Ref<EntityStore> ref, JobComponent job, WoodsmanJobComponent woodsman,
                               CommandBuffer<EntityStore> commandBuffer) {
        Vector3i workStationPos = job.getWorkStationBlockPosition();
        if (workStationPos == null) return;

        // TODO: Implement actual tree harvesting here before returning.
        Vector3d wsTarget = new Vector3d(workStationPos.x + 0.5, workStationPos.y, workStationPos.z + 0.5);
        commandBuffer.addComponent(ref, MoveToTargetComponent.getComponentType(), new MoveToTargetComponent(wsTarget));

        job.setCurrentTask(JobState.TravelingHome);
        DebugLog.info(DebugCategory.WOODSMAN_JOB, "Woodsman returning home from tree at %s", woodsman.targetTreePosition);
    }

    private void handleTravelingHome(Ref<EntityStore> ref, JobComponent job, WoodsmanJobComponent woodsman,
                                     Vector3d colonistPos, CommandBuffer<EntityStore> commandBuffer,
                                     Store<EntityStore> store) {
        Vector3i workStationPos = job.getWorkStationBlockPosition();
        if (workStationPos == null) return;

        double dx = colonistPos.x - (workStationPos.x + 0.5);
        double dy = colonistPos.y - workStationPos.y;
        double dz = colonistPos.z - (workStationPos.z + 0.5);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                "[Woodsman] TravelingHome — dist=%.2f to workstation %s (threshold %.1f).",
                dist, workStationPos, WORKSTATION_ARRIVAL_3D);

        if (dist <= WORKSTATION_ARRIVAL_3D) {
            unmarkClaimedTree(woodsman, store);
            woodsman.targetTreePosition = null;
            woodsman.stuckTicks = 0;
            woodsman.lastKnownPosition = null;
            job.setCurrentTask(JobState.Idle);
            DebugLog.info(DebugCategory.WOODSMAN_JOB, "[Woodsman] Arrived home at workstation.");
            return;
        }

        // Stuck detection — re-dispatch nav if the colonist hasn't moved (e.g. after server restart).
        Vector3i currentCell = new Vector3i((int) colonistPos.x, (int) colonistPos.y, (int) colonistPos.z);
        if (currentCell.equals(woodsman.lastKnownPosition)) {
            woodsman.stuckTicks++;
        } else {
            woodsman.stuckTicks = 0;
            woodsman.lastKnownPosition = currentCell;
        }

        if (woodsman.stuckTicks >= STUCK_TICKS_LIMIT) {
            woodsman.stuckTicks = 0;
            woodsman.lastKnownPosition = null;
            Vector3d wsTarget = new Vector3d(workStationPos.x + 0.5, workStationPos.y, workStationPos.z + 0.5);
            commandBuffer.addComponent(ref, MoveToTargetComponent.getComponentType(), new MoveToTargetComponent(wsTarget));
            DebugLog.info(DebugCategory.WOODSMAN_JOB, "[Woodsman] TravelingHome — stuck, re-dispatching nav to workstation.");
        }
    }

    // ===== Helpers =====

    static void unmarkClaimedTree(WoodsmanJobComponent woodsman, Store<EntityStore> store) {
        if (woodsman.targetTreePosition == null) return;
        Vector3i treePos = woodsman.targetTreePosition;
        World world = store.getExternalData().getWorld();
        Ref<ChunkStore> treeBlockRef = BlockModule.getBlockEntity(world, treePos.x, treePos.y, treePos.z);
        if (treeBlockRef == null) return;
        HarvestableTreeComponent tree = treeBlockRef.getStore().getComponent(treeBlockRef, HarvestableTreeComponent.getComponentType());
        if (tree != null) {
            tree.setMarkedForHarvest(false);
        }
    }

    /**
     * Finds the nearest available (unmarked, allowed-type) tree within the woodsman's
     * search radius of the workstation.
     */
    @Nullable
    private Vector3i findNearestAvailableTree(WoodsmanJobComponent woodsman, Vector3i workStationPos, World world) {
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

        DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                "[Woodsman] Tree scan: total=%d, marked=%d, wrongType=%d, candidates=%d",
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
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                    "[Woodsman] Closest tree outside radius: %s at dist=%.1f (radius=%.1f).",
                    closestOutsideRadius, Math.sqrt(closestOutsideRadiusDist), woodsman.treeSearchRadius);
        }
        return nearest;
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return query;
    }
}
