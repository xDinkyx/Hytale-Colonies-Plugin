package com.hytalecolonies.npc.sensors;

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
 * Builder for the {@code "HarvestableTree"} custom NPC sensor.
 *
 * <p>The sensor scans the ChunkStore for the nearest unclaimed {@code HarvestableTreeComponent}
 * within the specified range, claims it, and provides its position to {@code Seek} body motion.
 *
 * <p>JSON usage in an NPC role template:
 * <pre>{@code
 * {
 *   "Sensor":     { "Type": "HarvestableTree", "Range": 64.0 },
 *   "BodyMotion": { "Type": "Seek", "StopDistance": 2.0, "SlowDownDistance": 4.0 },
 *   "Action":     { "Type": "HarvestBlock" }
 * }
 * }</pre>
 *
 * <p>Registered via {@code NPCPlugin.get().registerCoreComponentType("HarvestableTree", ...)}
 * in {@link com.hytalecolonies.HytaleColoniesPlugin#setup()}.
 */
public class BuilderSensorHarvestableTree extends BuilderSensorBase {

    private final DoubleHolder range = new DoubleHolder();

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Detects the nearest unclaimed harvestable tree within range.";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Scans the world for registered HarvestableTreeComponent block entities within the given "
             + "range. Claims the nearest unclaimed tree and provides its base position for Seek navigation. "
             + "The claim is released when the sensor stops firing (tree broken or no longer valid).";
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
            "Horizontal range to search for harvestable trees (metres)", null
        );
        // Declare that this sensor provides a position — Seek will pick it up.
        this.provideFeature(Feature.Position);
        return this;
    }

    @Nonnull
    @Override
    public Sensor build(@Nonnull BuilderSupport support) {
        return new SensorHarvestableTree(this, support);
    }

    public double getRange(@Nonnull BuilderSupport support) {
        ExecutionContext ctx = support.getExecutionContext();
        return this.range.get(ctx);
    }
}
