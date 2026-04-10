package com.hytalecolonies.npc.actions.miner;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.MinerWorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.npc.actions.common.ActionSeekNextBlockBase;
import com.hytalecolonies.utils.MinerUtil;
import com.hytalecolonies.utils.WorkStationUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;

/**
 * Seeks the next unclaimed solid block in the mine shaft and navigates toward it.
 * Extends {@link ActionSeekNextBlockBase} for the shared claim-and-navigate logic;
 * mine origin is lazily initialised here and the actual scan delegates to
 * {@link MinerUtil#findNextMineBlock}.
 */
public class ActionSeekNextMineBlock extends ActionSeekNextBlockBase {

    public ActionSeekNextMineBlock(@Nonnull BuilderActionSeekNextMineBlock builder,
            @Nonnull BuilderSupport support) {
        super(builder, support);
    }

    @Override
    protected void preProcess(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                              @Nonnull JobComponent job, @Nonnull String npcId) {
        MinerWorkStationComponent ws = WorkStationUtil.getMinerWorkStation(store, ref);
        if (ws == null) return;
        if (ws.mineOrigin == null && job.getWorkStationBlockPosition() != null) {
            Vector3i pos = job.getWorkStationBlockPosition();
            ws.mineOrigin = new Vector3i(pos.x, pos.y, pos.z + ws.mineOffsetZ);
            DebugLog.info(DebugCategory.MINER_JOB,
                    "[SeekNextMineBlock] [%s] Mine origin initialised at %s.", npcId, ws.mineOrigin);
        }
    }

    @Override
    protected @Nullable Vector3i findNextBlock(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                                               @Nonnull World world, @Nonnull String npcId) {
        MinerWorkStationComponent ws = WorkStationUtil.getMinerWorkStation(store, ref);
        if (ws == null) return null;
        return MinerUtil.findNextMineBlock(ws, world);
    }

    @Override
    protected String getClaimLabel() {
        return "Mine";
    }
}
