package com.hytalecolonies.npc.actions;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;

/** Builder for the {@code "NotifyBlockBroken"} NPC action. No configuration. */
public class BuilderActionNotifyBlockBroken extends BuilderActionBase {

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Notifies ECS that the current job-target block has been broken.";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Sets JobComponent.blockBrokenNotification = true. No logic -- ECS decides the next state.";
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
        return new ActionNotifyBlockBroken(this, support);
    }
}
