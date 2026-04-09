package com.hytalecolonies.components.jobs;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.HytaleColoniesPlugin;

/** Marker component identifying a colonist as a miner. */
public class MinerJobComponent implements Component<EntityStore> {

    // ===== Codec =====
    public static final BuilderCodec<MinerJobComponent> CODEC = BuilderCodec
            .builder(MinerJobComponent.class, MinerJobComponent::new)
            .build();

    // ===== Constructors =====
    public MinerJobComponent() {}

    // ===== Component Type =====
    public static ComponentType<EntityStore, MinerJobComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getMinerJobComponentType();
    }

    // ===== Cloneable =====
    @Override
    public @Nullable Component<EntityStore> clone() {
        return new MinerJobComponent();
    }
}
