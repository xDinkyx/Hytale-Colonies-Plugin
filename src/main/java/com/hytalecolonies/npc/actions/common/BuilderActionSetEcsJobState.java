package com.hytalecolonies.npc.actions.common;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringHolder;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;

/**
 * Builder for {@code "SetEcsJobState"} -- sets {@code JobComponent.getCurrentTask()}.
 *
 * JSON: {@code { "Type": "SetEcsJobState", "JobState": "DeliveringItems" }}
 *
 */
public class BuilderActionSetEcsJobState extends BuilderActionBase {

    private final StringHolder stateName = new StringHolder();

    @Nonnull @Override public String getShortDescription() { return "Sets the colonist's ECS job state to the configured value."; }
    @Nonnull @Override public String getLongDescription() { return getShortDescription(); }
    @Nonnull @Override public BuilderDescriptorState getBuilderDescriptorState() { return BuilderDescriptorState.WorkInProgress; }

    @Nonnull
    @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        this.getString(data, "JobState", this.stateName, "Idle",
                null,
                BuilderDescriptorState.WorkInProgress,
                "The ECS job state to set (e.g. DeliveringItems, Idle)", null);
        return this;
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport support) {
        return new ActionSetEcsJobState(this, support);
    }

    @Nonnull
    public String getStateName(@Nonnull BuilderSupport support) {
        return this.stateName.get(support.getExecutionContext());
    }
}
