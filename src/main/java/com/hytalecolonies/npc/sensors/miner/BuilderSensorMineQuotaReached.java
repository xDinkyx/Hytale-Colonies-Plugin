package com.hytalecolonies.npc.sensors.miner;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;
import javax.annotation.Nonnull;

/**
 * Builder for the {@code "MineQuotaReached"} sensor.
 *
 * <p>Fires when {@code MinerJobComponent.blocksMinedThisRun >= WorkStationComponent.blocksPerRun}.
 * No JSON configuration required.
 */
public class BuilderSensorMineQuotaReached extends BuilderSensorBase {

    @Nonnull @Override public String getShortDescription() { return "Fires when the miner's per-run block quota has been reached."; }
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
        return new SensorMineQuotaReached(this, support);
    }
}
