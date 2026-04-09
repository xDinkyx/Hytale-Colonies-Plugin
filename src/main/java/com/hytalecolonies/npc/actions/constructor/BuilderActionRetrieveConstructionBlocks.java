package com.hytalecolonies.npc.actions.constructor;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;

/** Builder for the {@code "RetrieveConstructionBlocks"} NPC action. No configuration. */
public class BuilderActionRetrieveConstructionBlocks extends BuilderActionBase {

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Stub: marks construction blocks as retrieved from the workstation.";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Sets JobComponent.blocksRetrievedNotification = true. No inventory transfer yet.";
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
        return new ActionRetrieveConstructionBlocks(this, support);
    }
}
