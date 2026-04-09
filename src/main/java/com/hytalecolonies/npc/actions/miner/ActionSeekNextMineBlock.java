package com.hytalecolonies.npc.actions.miner;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.npc.actions.common.ActionSeekNextBlockBase;
import com.hytalecolonies.utils.MinerUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
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
    protected void preProcess(@Nonnull WorkStationComponent ws,
                              @Nonnull JobComponent job,
                              @Nonnull String npcId) {
        if (ws.mineOrigin == null && job.getWorkStationBlockPosition() != null) {
            Vector3i pos = job.getWorkStationBlockPosition();
            ws.mineOrigin = new Vector3i(pos.x, pos.y, pos.z + ws.mineOffsetZ);
            DebugLog.info(DebugCategory.MINER_JOB,
                    "[SeekNextMineBlock] [%s] Mine origin initialised at %s.", npcId, ws.mineOrigin);
        }
    }

    @Override
    protected @Nullable Vector3i findNextBlock(@Nonnull WorkStationComponent ws,
                                               @Nonnull World world,
                                               @Nonnull String npcId) {
        return MinerUtil.findNextMineBlock(ws, world);
    }

    @Override
    protected String getClaimLabel() {
        return "Mine";
    }
}
