package com.hytalecolonies.components.jobs;

import javax.annotation.Nullable;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Per-colonist component for constructor colonists. Carries runtime state across
 * restarts; all build configuration and order data lives in
 * {@link WorkStationComponent} and {@link ConstructionOrderComponent}.
 */
public class ConstructorJobComponent implements Component<EntityStore> {

    // ===== Codec =====
    public static final BuilderCodec<ConstructorJobComponent> CODEC = BuilderCodec
            .builder(ConstructorJobComponent.class, ConstructorJobComponent::new)
            .append(new KeyedCodec<>("BlocksProcessedThisRun", Codec.INTEGER),
                    (o, v) -> o.blocksProcessedThisRun = v,
                    o -> o.blocksProcessedThisRun)
            .add()
            .build();

    // ===== Persisted runtime state =====
    /** Blocks cleared or placed during the current work run. Resets each Idle tick. */
    public int blocksProcessedThisRun = 0;

    // ===== Constructors =====
    public ConstructorJobComponent() {}

    // ===== Component Type =====
    public static ComponentType<EntityStore, ConstructorJobComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getConstructorJobComponentType();
    }

    // ===== Cloneable =====
    @Override
    public @Nullable Component<EntityStore> clone() {
        ConstructorJobComponent copy = new ConstructorJobComponent();
        copy.blocksProcessedThisRun = this.blocksProcessedThisRun;
        return copy;
    }
}
