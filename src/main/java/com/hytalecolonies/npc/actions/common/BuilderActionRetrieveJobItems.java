package com.hytalecolonies.npc.actions.common;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;

/** Builder for the {@code "RetrieveJobItems"} custom NPC action. */
public class BuilderActionRetrieveJobItems extends BuilderActionBase {

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Retrieves items needed for a task from the workstation container.";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Tries to get all the items needed for a task from the workstation container. Both the required items and tools for the job as defined by the workstation and the items needed to perform the current task; like blocks to build or items to craft.";
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.WorkInProgress;
    }

    @Nonnull
    @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        return this;
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport support) {
        return new ActionRetrieveJobItems(this, support);
    }
}
