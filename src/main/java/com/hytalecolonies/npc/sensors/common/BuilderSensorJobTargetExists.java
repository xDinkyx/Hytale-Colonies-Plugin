package com.hytalecolonies.npc.sensors.common;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import javax.annotation.Nonnull;

/** Builder for {@link SensorJobTargetExists}. JSON: {@code { "Type": "JobTargetExists" }} */
public class BuilderSensorJobTargetExists extends BuilderSensorBase {

    @Nonnull @Override public String getShortDescription() {
        return "Fires when the NPC has a non-null job target position.";
    }
    @Nonnull @Override public String getLongDescription() { return getShortDescription(); }
    @Nonnull @Override public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) { return this; }

    @Nonnull @Override
    public Sensor build(@Nonnull BuilderSupport support) {
        return new SensorJobTargetExists(this, support);
    }
}
