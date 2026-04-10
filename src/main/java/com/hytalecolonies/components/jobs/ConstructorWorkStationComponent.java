package com.hytalecolonies.components.jobs;

import javax.annotation.Nullable;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/** Marker component on a Constructor workstation block. Also holds the active order pointer. */
public class ConstructorWorkStationComponent implements Component<ChunkStore> {

    // ===== Codec =====
    public static final BuilderCodec<ConstructorWorkStationComponent> CODEC = BuilderCodec
            .builder(ConstructorWorkStationComponent.class, ConstructorWorkStationComponent::new)
            .append(new KeyedCodec<>("ActiveOrderPosition", Vector3i.CODEC),
                    (o, v) -> o.activeOrderPosition = v,
                    o -> o.activeOrderPosition)
            .add()
            .build();

    /** Position of the block entity carrying the active {@link ConstructionOrderComponent}, or null if idle. */
    public @Nullable Vector3i activeOrderPosition = null;

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
        copy.activeOrderPosition = activeOrderPosition != null ? activeOrderPosition.clone() : null;
        return copy;
    }
}
