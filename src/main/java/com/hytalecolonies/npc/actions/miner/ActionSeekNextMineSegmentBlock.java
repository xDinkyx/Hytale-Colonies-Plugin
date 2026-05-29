package com.hytalecolonies.npc.actions.miner;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hytalecolonies.MineSegmentStore;
import com.hytalecolonies.components.jobs.MinerWorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.npc.actions.common.ActionSeekNextBlockBase;
import com.hytalecolonies.utils.ConstructorUtil;
import com.hytalecolonies.utils.WorkStationUtil;

/**
 * Finds the next block to clear in the active mine segment's prefab footprint (blocks that exist in the world where the prefab expects air), then claims it and
 * navigates the miner toward it.
 *
 * <p>
 * Returns {@code null} (sets {@code workAvailable = false}) when:
 * <ul>
 * <li>No active mine segment exists for this workstation.</li>
 * <li>The active segment's prefab is empty (placeholder, not yet built in-game).</li>
 * <li>The segment footprint is already fully cleared.</li>
 * </ul>
 */
public class ActionSeekNextMineSegmentBlock extends ActionSeekNextBlockBase
{
    public ActionSeekNextMineSegmentBlock(@Nonnull BuilderActionSeekNextMineSegmentBlock builder, @Nonnull BuilderSupport support)
    {
        super(builder, support);
    }

    @Override
    @Nullable
    protected Vector3i findNextBlock(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull World world, @Nonnull String npcId)
    {
        MinerWorkStationComponent ws = WorkStationUtil.getMinerWorkStation(store, ref);
        if (ws == null || ws.activeSegmentId == null)
        {
            DebugLog.fine(DebugCategory.MINER_JOB, "[SeekNextMineSegmentBlock] [%s] No active segment.", npcId);
            return null;
        }

        MineSegmentStore.Entry segment = MineSegmentStore.get().get(ws.activeSegmentId);
        if (segment == null || segment.origin == null)
        {
            DebugLog.fine(DebugCategory.MINER_JOB, "[SeekNextMineSegmentBlock] [%s] Segment %s not found or has no origin.", npcId, ws.activeSegmentId);
            return null;
        }

        BlockSelection prefab = loadPrefab(segment);
        if (prefab == null)
        {
            DebugLog.fine(DebugCategory.MINER_JOB, "[SeekNextMineSegmentBlock] [%s] Prefab %s not loaded (placeholder?).", npcId, segment.prefabId);
            return null;
        }

        // Build an adapter so ConstructorUtil's scan works for mine entries too.
        // We must sync cachedSortedBlocks back to the segment to avoid re-sorting every tick.
        com.hytalecolonies.ConstructionOrderStore.Entry adapter =
                new com.hytalecolonies.ConstructionOrderStore.Entry(segment.id, segment.prefabId, segment.origin);
        adapter.cachedSelection = segment.cachedSelection;
        adapter.cachedSortedBlocks = segment.cachedSortedBlocks;

        Vector3i next = ConstructorUtil.findNextClearingTarget(adapter, world, prefab);

        // Sync the sorted-block cache back so subsequent ticks skip re-sorting.
        segment.cachedSortedBlocks = adapter.cachedSortedBlocks;

        if (next == null)
        {
            DebugLog.fine(DebugCategory.MINER_JOB, "[SeekNextMineSegmentBlock] [%s] Segment %s fully cleared.", npcId, segment.id);
        }
        return next;
    }

    @Override
    protected String getClaimLabel()
    {
        return "MineSegment";
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Loads (or returns cached) {@link BlockSelection} for the segment. Returns {@code null} when the prefab is missing or has no blocks defined yet.
     */
    @Nullable
    private static BlockSelection loadPrefab(@Nonnull MineSegmentStore.Entry segment)
    {
        if (segment.cachedSelection != null)
            return segment.cachedSelection;

        com.hytalecolonies.ConstructionOrderStore.Entry adapter =
                new com.hytalecolonies.ConstructionOrderStore.Entry(segment.id, segment.prefabId, segment.origin);
        BlockSelection loaded = ConstructorUtil.loadPrefab(adapter);
        // Sync both cache fields back to the segment.
        segment.cachedSelection = adapter.cachedSelection;
        segment.cachedSortedBlocks = adapter.cachedSortedBlocks;
        return loaded;
    }
}
