package com.hytalecolonies.utils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.systems.world.TreeDetector;


/** Utility methods for woodsman colonist job logic. */
public final class WoodsmanUtil
{

    private WoodsmanUtil()
    {
    }

    /** Flood-fill from {@code brokenPos} at the same Y; returns the next adjacent wood block, or {@code null}. */
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
            DebugLog.fine(DebugCategory.WOODSMAN_JOB, "[WoodsmanJob] findNextBaseBlock found standing base block at %s (key=%s).", cur, key);
            return cur;
        }
        DebugLog.fine(DebugCategory.WOODSMAN_JOB, "[WoodsmanJob] findNextBaseBlock -- no more standing base blocks adjacent to %s.", brokenPos);
        return null;
    }

    private static long pack3i(Vector3i v)
    {
        return ((long)(v.x & 0x1FFFFFL) << 42) | ((long)(v.z & 0x1FFFFFL) << 21) | (v.y & 0x1FFFFFL);
    }
}
