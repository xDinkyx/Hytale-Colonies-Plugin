package com.hytalecolonies.npc.sensors.common;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.Feature;
import com.hypixel.hytale.server.npc.asset.builder.holder.DoubleHolder;
import com.hypixel.hytale.server.npc.asset.builder.validators.DoubleRangeValidator;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import com.hypixel.hytale.server.npc.util.expression.ExecutionContext;
import javax.annotation.Nonnull;

/**
 * Builder for the {@code "JobTarget"} custom NPC sensor.
 *
 * <p>Fires when the entity has an active job target position and is within
 * the configured range of it. Provides the target position so that actions
 * (e.g. {@code HarvestBlock}) can receive it via {@code sensorInfo}.
 *
 * <p>JSON usage in an NPC role template:
 * <pre>{@code
 * { "Type": "JobTarget", "Range": 2.5 }
 * }</pre>
 *
 * <p>Registered via {@code NPCPlugin.get().registerCoreComponentType("JobTarget", ...)}
 * in {@link com.hytalecolonies.HytaleColoniesPlugin#setup()}.
 */
public class BuilderSensorJobTarget extends BuilderSensorBase {

    private final DoubleHolder range = new DoubleHolder();

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Fires when the entity is within range of its active job target position.";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Reads the JobTargetComponent from the entity. Returns true when a target position "
             + "exists and the entity is within Range metres (horizontal) of it. Provides the target "
             + "position to any paired action via sensorInfo.";
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull
    @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) {
        this.requireDouble(
            data, "Range", this.range,
            DoubleRangeValidator.fromExclToIncl(0.0, Double.MAX_VALUE),
            BuilderDescriptorState.Experimental,
            "Maximum horizontal distance from the target position for the sensor to fire", null
        );
        this.provideFeature(Feature.Position);
        return this;
    }

    @Nonnull
    @Override
    public Sensor build(@Nonnull BuilderSupport support) {
        return new SensorJobTarget(this, support);
    }

    public double getRange(@Nonnull BuilderSupport support) {
        ExecutionContext ctx = support.getExecutionContext();
        return this.range.get(ctx);
    }
}
