package com.hytalecolonies.components.jobs;

import java.util.UUID;

import javax.annotation.Nullable;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/** Marker component on a Constructor workstation block. Holds the active order UUID. */
public class ConstructorWorkStationComponent implements Component<ChunkStore> {

    // ===== Codec =====
    public static final BuilderCodec<ConstructorWorkStationComponent> CODEC = BuilderCodec
            .builder(ConstructorWorkStationComponent.class, ConstructorWorkStationComponent::new)
            .append(new KeyedCodec<>("ActiveOrderId", Codec.UUID_STRING),
                    (o, v) -> o.activeOrderId = v,
                    o -> o.activeOrderId)
            .add()
            .build();

    /** UUID of the active {@link com.hytalecolonies.ConstructionOrderStore.Entry}, or null if idle. */
    public @Nullable UUID activeOrderId = null;

    // ===== Constructors =====
    public ConstructorWorkStationComponent() {}

    // ===== Component Type =====
    public static ComponentType<ChunkStore, ConstructorWorkStationComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getConstructorWorkStationComponentType();
    }

    // ===== Clone =====
    @Override
    public @Nullable Component<ChunkStore> clone() {
        ConstructorWorkStationComponent copy = new ConstructorWorkStationComponent();
        copy.activeOrderId = activeOrderId;
        return copy;
    }
}
