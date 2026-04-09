package com.hytalecolonies.npc.actions.common;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;

/** Builder for the {@code "FindDeliveryContainer"} custom NPC action. */
public class BuilderActionFindDeliveryContainer extends BuilderActionBase {

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Finds the nearest item-container near the workstation and sets it as the delivery target.";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Searches within 3 blocks of the workstation for an item-container block. "
             + "Sets JobTargetComponent and the NavTarget slot so the colonist can navigate there. "
             + "Transitions to TravelingToHome if no container is found.";
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
        return new ActionFindDeliveryContainer(this, support);
    }
}
