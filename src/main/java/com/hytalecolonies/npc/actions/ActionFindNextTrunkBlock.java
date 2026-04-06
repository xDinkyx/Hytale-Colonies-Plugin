package com.hytalecolonies.npc.actions;

import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.systems.treescan.TreeDetector;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hytalecolonies.utils.JobNavigationUtil;
import com.hytalecolonies.utils.WorkStationUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Called after the current trunk block is broken. Flood-fills horizontally at the
 * same Y level looking for an adjacent standing wood block (wide multi-block tree base).
 *
 * <p>If a next trunk is found: unclaims the broken block, claims the next one, and
 * dispatches navigation so the colonist can immediately continue harvesting.
 *
 * <p>If no adjacent trunk remains: unclaims the broken block and sets
 * {@link JobTargetComponent#targetPosition} to {@code null}. {@code SensorJobTargetExists}
 * will then return false, letting the instruction evaluator transition to Collecting.
 */
public class ActionFindNextTrunkBlock extends ActionBase {

    public ActionFindNextTrunkBlock(@Nonnull BuilderActionFindNextTrunkBlock builder,
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

        DebugLog.fine(DebugCategory.WOODSMAN_JOB, "[FindNextTrunkBlock] [%s] Action started.", npcId);

        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jobTarget == null || jobTarget.targetPosition == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB,
                    "[FindNextTrunkBlock] [%s] No job target -- nothing to do.", npcId);
            return true;
        }
        Vector3i brokenPosition = new Vector3i(
                jobTarget.targetPosition.x,
                jobTarget.targetPosition.y,
                jobTarget.targetPosition.z);

        DebugLog.info(DebugCategory.WOODSMAN_JOB,
                "[FindNextTrunkBlock] [%s] Block at %s broken -- flood-filling for adjacent trunk.", npcId, brokenPosition);

        WorkStationComponent workStation = WorkStationUtil.resolve(store, ref);
        Set<String> allowedWoodTypes = workStation != null ? workStation.getAllowedTreeTypes() : null;
        if (allowedWoodTypes == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB,
                    "[FindNextTrunkBlock] [%s] Workstation not found -- cannot filter by wood type. Target will be cleared.", npcId);
        }

        if (uuidComponent == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB,
                    "[FindNextTrunkBlock] [%s] No UUIDComponent -- cannot claim next trunk.", npcId);
            return true;
        }
        UUID colonistUuid = uuidComponent.getUuid();

        World world = store.getExternalData().getWorld();
        Vector3i nextTrunk = allowedWoodTypes != null
                ? findAdjacentStandingTrunk(brokenPosition, allowedWoodTypes, world, npcId)
                : null;

        world.execute(() -> {
            ClaimBlockUtil.unclaimBlock(world, brokenPosition);
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                    "[FindNextTrunkBlock] [%s] Unclaimed broken block at %s.", npcId, brokenPosition);

            JobTargetComponent liveTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
            if (liveTarget == null) {
                DebugLog.warning(DebugCategory.WOODSMAN_JOB,
                        "[FindNextTrunkBlock] [%s] JobTargetComponent disappeared during world.execute -- skipping.", npcId);
                return;
            }

            if (nextTrunk != null) {
                claimAndMoveToNextTrunk(world, store, ref, colonistUuid, liveTarget, nextTrunk, npcId);
            } else {
                clearTarget(liveTarget, npcId);
            }
        });

        return true;
    }

    private static void claimAndMoveToNextTrunk(@Nonnull World world, @Nonnull Store<EntityStore> store,
                                                 @Nonnull Ref<EntityStore> ref, @Nonnull UUID colonistUuid,
                                                 @Nonnull JobTargetComponent liveTarget, @Nonnull Vector3i nextTrunk,
                                                 @Nonnull String npcId) {
        boolean claimed = JobNavigationUtil.claimAndNavigateTo(world, store, ref, colonistUuid, nextTrunk, "Harvest");
        if (claimed) {
            DebugLog.info(DebugCategory.WOODSMAN_JOB,
                    "[FindNextTrunkBlock] [%s] Claimed adjacent trunk at %s -- navigating.", npcId, nextTrunk);
        } else {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                    "[FindNextTrunkBlock] [%s] Adjacent trunk at %s already taken -- clearing target.", npcId, nextTrunk);
            liveTarget.setTargetPosition(null);
        }
    }

    private static void clearTarget(@Nonnull JobTargetComponent liveTarget, @Nonnull String npcId) {
        liveTarget.setTargetPosition(null);
        DebugLog.info(DebugCategory.WOODSMAN_JOB,
                "[FindNextTrunkBlock] [%s] No adjacent trunks remain -- tree fully harvested.", npcId);
    }

    /**
     * Horizontal flood-fill from {@code brokenPosition} at the same Y level, returning
     * the first adjacent block whose key is in {@code allowedWoodTypes}, or {@code null}.
     */
    @Nullable
    private static Vector3i findAdjacentStandingTrunk(@Nonnull Vector3i brokenPosition,
                                                       @Nonnull Set<String> allowedWoodTypes,
                                                       @Nonnull World world,
                                                       @Nonnull String npcId) {
        int baseY = brokenPosition.y;
        Set<Long> visited = new HashSet<>();
        Deque<Vector3i> queue = new ArrayDeque<>();
        visited.add(pack(brokenPosition));

        int[][] cardinalDirections = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] direction : cardinalDirections) {
            Vector3i neighbour = new Vector3i(brokenPosition.x + direction[0], baseY, brokenPosition.z + direction[1]);
            if (visited.add(pack(neighbour))) {
                queue.add(neighbour);
            }
        }

        while (!queue.isEmpty()) {
            Vector3i current = queue.poll();
            String blockKey = TreeDetector.getBlockKey(world, current.x, current.y, current.z);
            if (blockKey != null && allowedWoodTypes.contains(blockKey)) {
                DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                        "[FindNextTrunkBlock] [%s] Found adjacent trunk at %s (key=%s).", npcId, current, blockKey);
                return current;
            }
        }

        DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                "[FindNextTrunkBlock] [%s] No adjacent standing trunks found from %s.", npcId, brokenPosition);
        return null;
    }

    private static long pack(@Nonnull Vector3i position) {
        return ((long) (position.x & 0x1FFFFFL) << 42)
                | ((long) (position.z & 0x1FFFFFL) << 21)
                | (position.y & 0x1FFFFFL);
    }
}
