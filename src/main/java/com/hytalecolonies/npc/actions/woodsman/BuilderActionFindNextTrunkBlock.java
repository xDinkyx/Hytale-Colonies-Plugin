package com.hytalecolonies.npc.actions.woodsman;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;

/** Builder for {@code "FindNextTrunkBlock"}. */
public class BuilderActionFindNextTrunkBlock extends BuilderActionBase {

    @Nonnull @Override public String getShortDescription() { return "Finds the next adjacent trunk block after the current one is broken; clears target if none remain."; }
    @Nonnull @Override public String getLongDescription() { return getShortDescription(); }
    @Nonnull @Override public BuilderDescriptorState getBuilderDescriptorState() { return BuilderDescriptorState.Experimental; }

    @Nonnull
    @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        return this;
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport support) {
        return new ActionFindNextTrunkBlock(this, support);
    }
}
