package com.hytalecolonies.systems.jobs.handlers;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.world.ClaimedBlockComponent;
import com.hytalecolonies.components.world.HarvestableTreeComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.debug.DebugTiming;
import com.hytalecolonies.systems.jobs.JobStateHandler;
import com.hytalecolonies.systems.treescan.TreeDetector;


/** Default {@link JobStateHandler} implementations for woodsman colonists. */
public final class WoodsmanHandlers
{

    private static final String REQUIRED_GATHER_TYPE = "Woods";

    private WoodsmanHandlers()
    {
    }

    // ===== Handlers =====

    /**
     * Waits for a suitable tool, finds the nearest unclaimed tree, claims it,
     * dispatches
     * navigation, and transitions to {@link JobState#TravelingToJob}.
     */
    public static final JobStateHandler IDLE = SharedHandlers.idle(new String[] {REQUIRED_GATHER_TYPE}, (ctx, workStation, workStationPos) -> {
        Vector3i nearestTree;
        try (var t = DebugTiming.measure("WoodsmanJob.findNearestAvailableTree@" + workStationPos, 50))
        {
            nearestTree = findNearestAvailableTree(workStation, workStationPos, ctx.world);
        }
        if (nearestTree == null)
        {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                          "[WoodsmanJob] [%s] Idle -- no available trees within radius %.1f of workstation %s.",
                          DebugLog.npcId(ctx.colonistRef, ctx.store),
                          workStation.treeSearchRadius,
                          workStationPos);
        }
        return nearestTree;
    }, "Harvest");

    // ===== Private helpers =====

    @Nullable private static Vector3i findNearestAvailableTree(WorkStationComponent workStation, Vector3i workStationPos, World world)
    {
        List<Vector3i> candidates = new ArrayList<>();
        int[] totalTrees = {0}, markedTrees = {0}, wrongTypeTrees = {0};
        Query<ChunkStore> treeQuery = Query.and(HarvestableTreeComponent.getComponentType());

        world.getChunkStore().getStore().forEachChunk(treeQuery, (treeChunk, _unused) -> {
            for (int i = 0; i < treeChunk.size(); i++)
            {
                HarvestableTreeComponent tree = treeChunk.getComponent(i, HarvestableTreeComponent.getComponentType());
                if (tree == null)
                    continue;
                totalTrees[0]++;
                if (treeChunk.getComponent(i, ClaimedBlockComponent.getComponentType()) != null)
                {
                    markedTrees[0]++;
                    continue;
                }
                if (!workStation.getAllowedTreeTypes().contains(tree.getTreeTypeKey()))
                {
                    wrongTypeTrees[0]++;
                    continue;
                }
                candidates.add(tree.getBasePosition());
            }
        });

        DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                      "[WoodsmanJob] Tree scan: total=%d, marked=%d, wrongType=%d, candidates=%d",
                      totalTrees[0],
                      markedTrees[0],
                      wrongTypeTrees[0],
                      candidates.size());

        Vector3i nearest = null;
        double nearestDistSq = workStation.treeSearchRadius * workStation.treeSearchRadius;
        double closestOutsideRadiusDist = Double.MAX_VALUE;
        Vector3i closestOutsideRadius = null;

        for (Vector3i pos : candidates)
        {
            double dx = pos.x - workStationPos.x;
            double dy = pos.y - workStationPos.y;
            double dz = pos.z - workStationPos.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < nearestDistSq)
            {
                nearestDistSq = distSq;
                nearest = pos;
            }
            else if (distSq < closestOutsideRadiusDist)
            {
                closestOutsideRadiusDist = distSq;
                closestOutsideRadius = pos;
            }
        }

        if (nearest == null && closestOutsideRadius != null)
        {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                          "[WoodsmanJob] Closest tree outside radius: %s at dist=%.1f (radius=%.1f).",
                          closestOutsideRadius,
                          Math.sqrt(closestOutsideRadiusDist),
                          workStation.treeSearchRadius);
        }
        return nearest;
    }

    /**
     * Horizontal flood-fill from broken trunk block looking for adjacent standing
     * wood blocks
     * at the same Y (wide multi-block tree base detection).
     */
    @Nullable public static Vector3i findNextBaseBlock(@Nonnull Vector3i brokenPos, @Nonnull Set<String> woodKeys, @Nonnull World world)
    {
        int baseY = brokenPos.y;
        Set<Long> visited = new HashSet<>();
        Deque<Vector3i> queue = new ArrayDeque<>();
        visited.add(pack3i(brokenPos));
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs)
        {
            Vector3i neighbour = new Vector3i(brokenPos.x + d[0], baseY, brokenPos.z + d[1]);
            if (visited.add(pack3i(neighbour)))
                queue.add(neighbour);
        }
        DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                      "[WoodsmanJob] findNextBaseBlock from %s (Y=%d), queued %d neighbours, woodKeys=%d.",
                      brokenPos,
                      baseY,
                      queue.size(),
                      woodKeys.size());

        while (!queue.isEmpty())
        {
            Vector3i cur = queue.poll();
            String key = TreeDetector.getBlockKey(world, cur.x, cur.y, cur.z);
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                          "[WoodsmanJob] findNextBaseBlock checking %s -- blockKey=%s isWood=%b.",
                          cur,
                          key,
                          key != null && woodKeys.contains(key));
            if (key == null || !woodKeys.contains(key))
                continue;
            DebugLog.info(DebugCategory.WOODSMAN_JOB, "[WoodsmanJob] findNextBaseBlock found standing base block at %s (key=%s).", cur, key);
            return cur;
        }
        DebugLog.info(DebugCategory.WOODSMAN_JOB, "[WoodsmanJob] findNextBaseBlock -- no more standing base blocks adjacent to %s.", brokenPos);
        return null;
    }

    private static long pack3i(Vector3i v)
    {
        return ((long)(v.x & 0x1FFFFFL) << 42) | ((long)(v.z & 0x1FFFFFL) << 21) | (v.y & 0x1FFFFFL);
    }
}
