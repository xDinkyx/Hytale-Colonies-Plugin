package com.hytalecolonies.npc.actions.miner;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.ConstructionOrderStore;
import com.hytalecolonies.MineSegmentStore;
import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.components.jobs.MinerWorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ConstructorUtil;
import com.hytalecolonies.utils.MineOreDetector;
import com.hytalecolonies.utils.WorkStationUtil;

/**
 * Scans the 1-block perimeter of the active mine segment for ore veins and populates {@link MinerJobComponent#oreVeinQueue} when one is found. No-op if the
 * queue is already non-empty or there is no active segment.
 */
public class ActionScanForOreVein extends ActionBase
{
    public ActionScanForOreVein(@Nonnull BuilderActionScanForOreVein builder, @Nonnull BuilderSupport support)
    {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, @Nullable InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store)
    {
        super.execute(ref, role, sensorInfo, dt, store);
        String npcId = DebugLog.npcId(ref, store);

        MinerJobComponent minerJob = store.getComponent(ref, MinerJobComponent.getComponentType());
        if (minerJob == null)
        {
            DebugLog.fine(DebugCategory.MINER_JOB, "[ScanForOreVein] [%s] No MinerJobComponent.", npcId);
            return true;
        }

        if (!minerJob.oreVeinQueue.isEmpty())
        {
            DebugLog.fine(DebugCategory.MINER_JOB, "[ScanForOreVein] [%s] Queue already non-empty -- skipping scan.", npcId);
            return true;
        }

        MinerWorkStationComponent ws = WorkStationUtil.getMinerWorkStation(store, ref);
        if (ws == null || ws.activeSegmentId == null)
        {
            DebugLog.fine(DebugCategory.MINER_JOB, "[ScanForOreVein] [%s] No active segment -- skipping ore scan.", npcId);
            return true;
        }

        MineSegmentStore.Entry segment = MineSegmentStore.get().get(ws.activeSegmentId);
        if (segment == null || segment.origin == null)
        {
            DebugLog.fine(DebugCategory.MINER_JOB, "[ScanForOreVein] [%s] Segment %s not found or missing origin.", npcId, ws.activeSegmentId);
            return true;
        }

        Vector3i[] bounds = segmentBounds(segment);
        if (bounds == null)
        {
            DebugLog.fine(DebugCategory.MINER_JOB, "[ScanForOreVein] [%s] Could not determine segment bounds -- skipping.", npcId);
            return true;
        }

        World world = store.getExternalData().getWorld();
        List<Vector3i> vein = MineOreDetector.findFirstVeinAroundBox(world, bounds[0], bounds[1]);
        if (!vein.isEmpty())
        {
            minerJob.oreVeinQueue.addAll(vein);
            DebugLog.info(DebugCategory.MINER_JOB, "[ScanForOreVein] [%s] Ore vein of %d blocks found.", npcId, vein.size());
        }
        else
        {
            DebugLog.fine(DebugCategory.MINER_JOB, "[ScanForOreVein] [%s] No ore vein found.", npcId);
        }

        return true;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Computes the absolute min/max AABB of the segment's prefab footprint. Uses the cached sorted-block list when available; otherwise loads the prefab.
     * Returns {@code null} when no block data can be resolved.
     */
    @Nullable
    private static Vector3i[] segmentBounds(@Nonnull MineSegmentStore.Entry segment)
    {
        Vector3i origin = segment.origin;
        if (origin == null)
            return null;

        List<int[]> blocks = segment.cachedSortedBlocks;
        if (blocks == null || blocks.isEmpty())
        {
            ConstructionOrderStore.Entry adapter = new ConstructionOrderStore.Entry(segment.id, segment.prefabId, segment.origin);
            ConstructorUtil.loadPrefab(adapter);
            segment.cachedSelection = adapter.cachedSelection;
            segment.cachedSortedBlocks = adapter.cachedSortedBlocks;
            blocks = segment.cachedSortedBlocks;
        }

        if (blocks == null || blocks.isEmpty())
            return null;

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] b : blocks)
        {
            int ax = origin.x + b[0];
            int ay = origin.y + b[1];
            int az = origin.z + b[2];
            if (ax < minX)
                minX = ax;
            if (ay < minY)
                minY = ay;
            if (az < minZ)
                minZ = az;
            if (ax > maxX)
                maxX = ax;
            if (ay > maxY)
                maxY = ay;
            if (az > maxZ)
                maxZ = az;
        }
        return new Vector3i[] {new Vector3i(minX, minY, minZ), new Vector3i(maxX, maxY, maxZ)};
    }
}
