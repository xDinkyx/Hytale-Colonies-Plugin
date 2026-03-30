package com.hytalecolonies.npc.sensors;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import javax.annotation.Nonnull;

/**
 * Builder for the {@code "EcsJobState"} sensor.
 *
 * <p>JSON usage:
 * <pre>{@code { "Type": "EcsJobState", "State": "Idle" }}</pre>
 *
 * <p>Fires when {@code JobComponent.getCurrentTask()} equals the configured state name.
 * This is the bridge sensor between the NPC role state machine and the ECS job pipeline.
 */
public class BuilderSensorEcsJobState extends BuilderSensorBase {

    private String stateName = "Idle";

    @Nonnull @Override public String getShortDescription() { return "Fires when the colonist's ECS job state matches the configured value."; }
    @Nonnull @Override public String getLongDescription() { return getShortDescription(); }
    @Nonnull @Override public BuilderDescriptorState getBuilderDescriptorState() { return BuilderDescriptorState.Experimental; }

    @Nonnull
    @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) {
        if (data.isJsonObject() && data.getAsJsonObject().has("State")) {
            this.stateName = data.getAsJsonObject().get("State").getAsString();
        }
        return this;
    }

    @Nonnull
    @Override
    public Sensor build(@Nonnull BuilderSupport support) {
        return new SensorEcsJobState(this, support);
    }

    @Nonnull
    public String getStateName() {
        return stateName;
    }
}
