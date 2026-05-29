package com.hytalecolonies.npc.actions.miner;

import javax.annotation.Nonnull;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

/** Builder for {@code "SeekNextOreVeinBlock"}. */
public class BuilderActionSeekNextOreVeinBlock extends BuilderActionBase
{
    @Nonnull
    @Override
    public String getShortDescription()
    {
        return "Pops the next block from the miner's ore-vein queue and claims it for harvesting.";
    }

    @Nonnull
    @Override
    public String getLongDescription()
    {
        return "When an ore vein has been detected adjacent to a cleared mine segment, this action "
                + "pops the head of the transient oreVeinQueue on MinerJobComponent, claims the block, "
                + "and sets the NPC nav target toward it. Sets workAvailable=false when the queue is empty.";
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState()
    {
        return BuilderDescriptorState.WorkInProgress;
    }

    @Nonnull
    @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data)
    {
        return this;
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport support)
    {
        return new ActionSeekNextOreVeinBlock(this, support);
    }
}
