package com.hytalecolonies.npc.actions.miner;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.npc.actions.common.ActionSeekNextBlockBase;

/**
 * Pops the next block from the miner's ore-vein queue ({@link MinerJobComponent#oreVeinQueue}) and claims it for harvesting. When the queue is empty this
 * action sets {@code workAvailable =
 * false} so the JSON pipeline falls through to {@code SeekNextMineSegmentBlock}.
 */
public class ActionSeekNextOreVeinBlock extends ActionSeekNextBlockBase
{
    public ActionSeekNextOreVeinBlock(@Nonnull BuilderActionSeekNextOreVeinBlock builder, @Nonnull BuilderSupport support)
    {
        super(builder, support);
    }

    @Override
    @Nullable
    protected Vector3i findNextBlock(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull World world, @Nonnull String npcId)
    {
        MinerJobComponent minerJob = store.getComponent(ref, MinerJobComponent.getComponentType());
        if (minerJob == null || minerJob.oreVeinQueue.isEmpty())
        {
            DebugLog.fine(DebugCategory.MINER_JOB, "[SeekNextOreVeinBlock] [%s] Ore vein queue empty.", npcId);
            return null;
        }

        // Peek without popping -- pop happens in world.execute() after a successful claim.
        // If the claim race fails (very rare for mine walls), the miner retries next tick
        // with the same head entry.
        Vector3i head = minerJob.oreVeinQueue.peek();
        DebugLog.fine(DebugCategory.MINER_JOB,
                      "[SeekNextOreVeinBlock] [%s] Next ore vein block: %s (%d remaining).",
                      npcId,
                      head,
                      minerJob.oreVeinQueue.size());

        // Pop eagerly -- ore-vein blocks are in mine walls, essentially uncontested.
        // Losing one block on a rare race is acceptable; the vein will still be mostly mined.
        return minerJob.oreVeinQueue.poll();
    }

    @Override
    protected String getClaimLabel()
    {
        return "OreVein";
    }
}
