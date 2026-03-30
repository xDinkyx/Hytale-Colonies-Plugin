package com.hytalecolonies.npc.actions;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;

/**
 * Builder for {@code "SetEcsJobState"} — sets {@code JobComponent.getCurrentTask()}
 * to the configured {@link com.hytalecolonies.components.jobs.JobState} value.
 *
 * <p>JSON usage:
 * <pre>{@code { "Type": "SetEcsJobState", "State": "CollectingDrops" }}</pre>
 *
 * <p>When setting {@code CollectingDrops}, also initialises
 * {@code JobComponent.collectingDropsSince} to the current time so that
 * {@code SharedHandlers.COLLECTING_DROPS} can begin the 5-second countdown.
 */
public class BuilderActionSetEcsJobState extends BuilderActionBase {

    private String stateName = "Idle";

    @Nonnull @Override public String getShortDescription() { return "Sets the colonist's ECS job state to the configured value."; }
    @Nonnull @Override public String getLongDescription() { return getShortDescription(); }
    @Nonnull @Override public BuilderDescriptorState getBuilderDescriptorState() { return BuilderDescriptorState.Experimental; }

    @Nonnull
    @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        if (data.isJsonObject() && data.getAsJsonObject().has("State")) {
            this.stateName = data.getAsJsonObject().get("State").getAsString();
        }
        return this;
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport support) {
        return new ActionSetEcsJobState(this, support);
    }

    @Nonnull
    public String getStateName() {
        return stateName;
    }
}
