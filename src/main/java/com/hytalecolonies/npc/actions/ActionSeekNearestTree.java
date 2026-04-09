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
 * Scans for the nearest unclaimed harvestable tree within the workstation's search radius,
 * claims it atomically via {@code world.execute()}, sets it as the job target, and dispatches navigation.
 * If none is available the target is left unset and the instruction block retries next cycle.
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

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        String npcId = DebugLog.npcId(ref, store);

        DebugLog.fine(DebugCategory.WOODSMAN_JOB, "[SeekNearestTree] [%s] Action started.", npcId);

        WorkStationComponent workStation = WorkStationUtil.resolve(store, ref);
        if (workStation == null) {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB, "[SeekNearestTree] [%s] Workstation not found -- skipping.", npcId);
            return true;
        }

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB, "[SeekNearestTree] [%s] No JobComponent -- cannot resolve workstation position.", npcId);
            return true;
        }
        Vector3i workStationPosition = job.getWorkStationBlockPosition();

        if (uuidComponent == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB, "[SeekNearestTree] [%s] No UUIDComponent -- cannot claim block.", npcId);
            return true;
        }
        UUID colonistUuid = uuidComponent.getUuid();

        World world = store.getExternalData().getWorld();
        Vector3i nearestTree = findNearestAvailableTree(workStation, workStationPosition, world, npcId);

        if (nearestTree == null) {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                    "[SeekNearestTree] [%s] No available trees within radius %.1f of workstation %s.",
                    npcId, workStation.treeSearchRadius, workStationPosition);
            return true;
        }

        DebugLog.info(DebugCategory.WOODSMAN_JOB,
                "[SeekNearestTree] [%s] Attempting to claim tree at %s.", npcId, nearestTree);

        final Vector3i treePosition = nearestTree;
        world.execute(() -> {
            JobTargetComponent existingTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
            if (existingTarget != null && existingTarget.targetPosition != null) {
                DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                        "[SeekNearestTree] [%s] Already has a job target -- skipping claim.", npcId);
                return;
            }

            boolean claimed = JobNavigationUtil.claimAndNavigateTo(world, store, ref, colonistUuid, treePosition, "Harvest");
            if (claimed) {
                DebugLog.info(DebugCategory.WOODSMAN_JOB,
                        "[SeekNearestTree] [%s] Claimed tree at %s -- navigating.", npcId, treePosition);
            } else {
                DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                        "[SeekNearestTree] [%s] Tree at %s was already taken -- will retry next cycle.", npcId, treePosition);
            }
        });

        return true;
    }

    @Nullable
    private static Vector3i findNearestAvailableTree(@Nonnull WorkStationComponent workStation,
                                                      @Nullable Vector3i workStationPosition,
                                                      @Nonnull World world,
                                                      @Nonnull String npcId) {
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
                "[SeekNearestTree] [%s] Scan found %d candidate trees (radius=%.1f, workstation=%s).",
                npcId, candidates.size(), workStation.treeSearchRadius, workStationPosition);

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
