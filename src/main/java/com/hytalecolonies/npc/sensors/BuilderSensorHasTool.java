package com.hytalecolonies.npc.sensors;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringHolder;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import javax.annotation.Nonnull;

/**
 * Builder for {@link SensorHasTool}.
 * JSON: {@code { "Type": "HasTool", "GatherType": "Woods" }}
 */
public class BuilderSensorHasTool extends BuilderSensorBase {

    private final StringHolder gatherType = new StringHolder();

    @Nonnull @Override public String getShortDescription() {
        return "Fires if the NPC has at least one tool covering the given gather type.";
    }
    @Nonnull @Override public String getLongDescription() { return getShortDescription(); }
    @Nonnull @Override public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data) {
        this.requireString(data, "GatherType", this.gatherType, null,
                BuilderDescriptorState.Experimental,
                "Gather type key to look for in inventory (e.g. Woods, Rocks, Soils)", null);
        return this;
    }

    @Nonnull @Override
    public Sensor build(@Nonnull BuilderSupport support) {
        return new SensorHasTool(this, support);
    }

    @Nonnull public String getGatherType(@Nonnull BuilderSupport support) {
        return this.gatherType.get(support.getExecutionContext());
    }
}
