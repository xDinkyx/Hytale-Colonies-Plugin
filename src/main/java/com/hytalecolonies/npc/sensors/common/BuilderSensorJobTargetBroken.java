package com.hytalecolonies.npc.sensors.common;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import javax.annotation.Nonnull;

/** Builder for {@link SensorJobTargetBroken}. JSON: {@code { "Type": "JobTargetBroken" }} */
public class BuilderSensorJobTargetBroken extends BuilderSensorBase {

    @Nonnull @Override public String getShortDescription() {
        return "Fires when the block at the NPC's current job target position is air (id 0).";
    }
    @Nonnull @Override public String getLongDescription() { return getShortDescription(); }
    @Nonnull @Override public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) { return this; }

    @Nonnull @Override
    public Sensor build(@Nonnull BuilderSupport support) {
        return new SensorJobTargetBroken(this, support);
    }
}
