package com.hytalecolonies.npc.actions.miner;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;

/** Builder for {@code "SeekNextMineSegmentBlock"}. */
public class BuilderActionSeekNextMineSegmentBlock extends BuilderActionBase {

    @Nonnull
    @Override
    public String getShortDescription()
    {
        return "Finds and claims the next block to clear in the active mine segment prefab.";
    }

    @Nonnull
    @Override
    public String getLongDescription()
    {
        return "Loads the active mine segment's prefab from MineSegmentStore, finds the first non-air "
                + "block where the prefab defines empty space, claims it, and navigates the miner toward it. "
                + "Sets workAvailable=false when the segment is fully cleared or no segment is active.";
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
        return new ActionSeekNextMineSegmentBlock(this, support);
    }
}
