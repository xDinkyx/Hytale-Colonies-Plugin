package com.hytalecolonies.npc.actions.miner;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;

/** Builder for {@code "SeekNextMineBlock"}. */
public class BuilderActionSeekNextMineBlock extends BuilderActionBase {

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Finds and claims the next unclaimed block in the mine shaft, then navigates the colonist toward it.";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return getShortDescription();
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull
    @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        return this;
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport support) {
        return new ActionSeekNextMineBlock(this, support);
    }
}
