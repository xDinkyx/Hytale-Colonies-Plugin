package com.hytalecolonies.npc.sensors;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import javax.annotation.Nonnull;

/**
 * Builder for the {@code "NoWorkAvailable"} sensor.
 *
 * <p>Fires when the colonist's {@code JobComponent.noWorkAvailable} flag is
 * {@code true}. Any seek action (SeekNextMineBlock, SeekNextTree, etc.) sets
 * this flag when it scans for task targets and finds none. No JSON configuration
 * is required.
 */
public class BuilderSensorNoWorkAvailable extends BuilderSensorBase {

    @Nonnull @Override public String getShortDescription() { return "Fires when the colonist's seek action found no work available."; }
    @Nonnull @Override public String getLongDescription() { return getShortDescription(); }
    @Nonnull @Override public BuilderDescriptorState getBuilderDescriptorState() { return BuilderDescriptorState.Experimental; }

    @Nonnull
    @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) {
        return this;
    }

    @Nonnull
    @Override
    public Sensor build(@Nonnull BuilderSupport support) {
        return new SensorNoWorkAvailable(this, support);
    }
}
