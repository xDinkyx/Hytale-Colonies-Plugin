package com.hytalecolonies.npc.actions;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.world.ClaimedBlockComponent;
import com.hytalecolonies.components.world.HarvestableTreeComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.JobNavigationUtil;
import com.hytalecolonies.utils.WorkStationUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Scans for the nearest unclaimed harvestable tree within the workstation's search
 * radius, claims it atomically via {@code world.execute()}, sets it as the active
 * job target, and dispatches navigation toward it.
 *
 * <p>This action is designed to fire once per idle cycle. If no tree is available
 * the action completes without setting a target; {@code SensorJobTargetExists} will
 * remain false and the instruction evaluator will retry on the next cycle.
 *
 * <p>Race safety: the claim is performed inside {@code world.execute()} so that two
 * woodsmen finding the same tree in the same tick serialize here — the first succeeds,
 * the second backs off.
 */
public class ActionSeekNearestTree extends ActionBase {

    public ActionSeekNearestTree(@Nonnull BuilderActionSeekNearestTree builder,
                                  @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           @Nullable InfoProvider sensorInfo, double dt,
                           @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        DebugLog.fine(DebugCategory.WOODSMAN_JOB, "[SeekNearestTree] Action fired.");

        WorkStationComponent workStation = WorkStationUtil.resolve(store, ref);
        if (workStation == null) {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB, "[SeekNearestTree] Workstation not found — skipping.");
            return true;
        }

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB, "[SeekNearestTree] No JobComponent — cannot resolve workstation position.");
            return true;
        }
        Vector3i workStationPosition = job.getWorkStationBlockPosition();

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB, "[SeekNearestTree] No UUIDComponent — cannot claim block.");
            return true;
        }
        UUID colonistUuid = uuidComponent.getUuid();

        World world = store.getExternalData().getWorld();
        Vector3i nearestTree = findNearestAvailableTree(workStation, workStationPosition, world);

        if (nearestTree == null) {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                    "[SeekNearestTree] No available trees within radius %.1f of workstation %s.",
                    workStation.treeSearchRadius, workStationPosition);
            return true;
        }

        DebugLog.info(DebugCategory.WOODSMAN_JOB,
                "[SeekNearestTree] Attempting to claim tree at %s.", nearestTree);

        final Vector3i treePosition = nearestTree;
        world.execute(() -> {
            JobTargetComponent existingTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
            if (existingTarget != null && existingTarget.targetPosition != null) {
                DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                        "[SeekNearestTree] Already has a job target — skipping claim.");
                return;
            }

            boolean claimed = JobNavigationUtil.claimAndNavigateTo(world, store, ref, colonistUuid, treePosition, "Harvest");
            if (claimed) {
                DebugLog.info(DebugCategory.WOODSMAN_JOB,
                        "[SeekNearestTree] Claimed tree at %s — navigating.", treePosition);
            } else {
                DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                        "[SeekNearestTree] Tree at %s was already taken — will retry next cycle.", treePosition);
            }
        });

        return true;
    }

    @Nullable
    private static Vector3i findNearestAvailableTree(@Nonnull WorkStationComponent workStation,
                                                      @Nullable Vector3i workStationPosition,
                                                      @Nonnull World world) {
        List<Vector3i> candidates = new ArrayList<>();
        Query<ChunkStore> treeQuery = Query.and(HarvestableTreeComponent.getComponentType());

        world.getChunkStore().getStore().forEachChunk(treeQuery, (chunk, _unused) -> {
            for (int index = 0; index < chunk.size(); index++) {
                HarvestableTreeComponent tree = chunk.getComponent(index, HarvestableTreeComponent.getComponentType());
                if (tree == null) {
                    continue;
                }
                if (chunk.getComponent(index, ClaimedBlockComponent.getComponentType()) != null) {
                    continue;
                }
                if (!workStation.getAllowedTreeTypes().contains(tree.getTreeTypeKey())) {
                    continue;
                }
                candidates.add(tree.getBasePosition());
            }
        });

        DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                "[SeekNearestTree] Scan found %d candidate trees (radius=%.1f, workstation=%s).",
                candidates.size(), workStation.treeSearchRadius, workStationPosition);

        if (workStationPosition == null) {
            return candidates.isEmpty() ? null : candidates.get(0);
        }

        Vector3i nearest = null;
        double nearestDistanceSq = workStation.treeSearchRadius * workStation.treeSearchRadius;

        for (Vector3i candidate : candidates) {
            double dx = candidate.x - workStationPosition.x;
            double dy = candidate.y - workStationPosition.y;
            double dz = candidate.z - workStationPosition.z;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq < nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
                nearest = candidate;
            }
        }

        return nearest;
    }
}
