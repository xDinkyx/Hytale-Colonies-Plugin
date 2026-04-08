package com.hytalecolonies.components.jobs;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.HytaleColoniesPlugin;

/**
 * Per-colonist component for miner colonists. Carries runtime state that is
 * meaningful across restarts; all job and mine configuration lives in
 * {@link WorkStationComponent}.
 */
public class MinerJobComponent implements Component<EntityStore> {

    // ===== Codec =====
    public static final BuilderCodec<MinerJobComponent> CODEC = BuilderCodec
            .builder(MinerJobComponent.class, MinerJobComponent::new)
            // Persisted: miner resumes the current run quota after restart instead of
            // resetting to 0, which would cause them to over-mine before delivering.
            .append(new KeyedCodec<>("BlocksMinedThisRun", Codec.INTEGER),
                    (o, v) -> o.blocksMinedThisRun = v,
                    o -> o.blocksMinedThisRun)
            .add()
            .build();

    // ===== Persisted runtime state =====
    /** How many blocks have been mined during the current work run. Resets each Idle tick. */
    public int blocksMinedThisRun = 0;

    // ===== Constructors =====
    public MinerJobComponent() {}

    // ===== Component Type =====
    public static ComponentType<EntityStore, MinerJobComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getMinerJobComponentType();
    }

    // ===== Cloneable =====
    @Override
    public @Nullable Component<EntityStore> clone() {
        MinerJobComponent copy = new MinerJobComponent();
        copy.blocksMinedThisRun = this.blocksMinedThisRun;
        return copy;
    }
}
