package com.hytalecolonies.npc.actions.constructor;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;

/** Builder for the {@code "PlaceConstructionBlock"} NPC action. No configuration. */
public class BuilderActionPlaceConstructionBlock extends BuilderActionBase {

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Places the correct prefab block at the job-target position (from thin air -- stub).";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Resolves the desired block key from the active prefab, places it in the world, and sets "
                + "JobComponent.blockPlacedNotification = true. No inventory check yet.";
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
        return new ActionPlaceConstructionBlock(this, support);
    }
}
