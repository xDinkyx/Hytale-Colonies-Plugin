package com.hytalecolonies.npc.sensors.common;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import javax.annotation.Nonnull;

/**
 * Builder for the {@code "CollectionTimerElapsed"} custom NPC sensor.
 *
 * <p>Fires when 5 seconds have elapsed since the colonist entered CollectingDrops state.
 * No configuration parameters are required.
 *
 * <p>JSON usage:
 * <pre>{@code { "Type": "CollectionTimerElapsed" }}</pre>
 *
 * <p>Registered via {@code NPCPlugin.get().registerCoreComponentType("CollectionTimerElapsed", ...)}
 * in {@link com.hytalecolonies.HytaleColoniesPlugin#setup()}.
 */
public class BuilderSensorCollectionTimerElapsed extends BuilderSensorBase {

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Fires when 5 seconds have elapsed since the colonist entered the CollectingDrops state.";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return getShortDescription();
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull
    @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) {
        return this;
    }

    @Nonnull
    @Override
    public Sensor build(@Nonnull BuilderSupport support) {
        return new SensorCollectionTimerElapsed(this, support);
    }
}
