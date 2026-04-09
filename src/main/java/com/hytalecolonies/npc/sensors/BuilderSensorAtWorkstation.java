package com.hytalecolonies.npc.sensors;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import javax.annotation.Nonnull;

/**
 * Builder for the {@code "AtWorkstation"} custom NPC sensor.
 *
 * <p>Fires when the colonist is within 3.0 blocks (XZ) of their assigned workstation.
 * No configuration parameters are required.
 *
 * <p>JSON usage:
 * <pre>{@code { "Type": "AtWorkstation" }}</pre>
 *
 * <p>Registered via {@code NPCPlugin.get().registerCoreComponentType("AtWorkstation", ...)}
 * in {@link com.hytalecolonies.HytaleColoniesPlugin#setup()}.
 */
public class BuilderSensorAtWorkstation extends BuilderSensorBase {

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Fires when the colonist is within 3.0 blocks (XZ) of their assigned workstation.";
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
        return new SensorAtWorkstation(this, support);
    }
}
