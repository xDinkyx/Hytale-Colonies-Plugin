package com.hytalecolonies.components.jobs;

import javax.annotation.Nullable;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Block-entity component placed on the Constructor workstation block when a build order
 * is created. Tracks which prefab to build, where to build it, and current status.
 *
 * <p>Status values: {@code "Pending"}, {@code "InProgress"}, {@code "Complete"}.
 */
public class ConstructionOrderComponent implements Component<ChunkStore> {

    // ===== Codec =====
    public static final BuilderCodec<ConstructionOrderComponent> CODEC = BuilderCodec
            .builder(ConstructionOrderComponent.class, ConstructionOrderComponent::new)
            .append(new KeyedCodec<>("PrefabId", Codec.STRING),
                    (o, v) -> o.prefabId = v,
                    o -> o.prefabId)
            .add()
            .append(new KeyedCodec<>("Status", Codec.STRING),
                    (o, v) -> o.status = v,
                    o -> o.status)
            .add()
            .append(new KeyedCodec<>("BuildOrigin", Vector3i.CODEC),
                    (o, v) -> o.buildOrigin = v,
                    o -> o.buildOrigin)
            .add()
            .build();

    /** ID of the prefab to build. */
    public String prefabId = "";

    /** Build status: "Pending", "InProgress", or "Complete". */
    public String status = "Pending";

    /** World position where the prefab anchor should be placed (the paste position). */
    public @Nullable Vector3i buildOrigin = null;

    // ===== Constructors =====
    public ConstructionOrderComponent() {}

    public ConstructionOrderComponent(String prefabId, Vector3i buildOrigin) {
        this.prefabId = prefabId;
        this.buildOrigin = buildOrigin;
    }

    // ===== Component Type =====
    public static ComponentType<ChunkStore, ConstructionOrderComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getConstructionOrderComponentType();
    }

    // ===== Cloneable =====
    @Override
    public @Nullable Component<ChunkStore> clone() {
        ConstructionOrderComponent copy = new ConstructionOrderComponent();
        copy.prefabId = this.prefabId;
        copy.status = this.status;
        copy.buildOrigin = this.buildOrigin;
        return copy;
    }
}
