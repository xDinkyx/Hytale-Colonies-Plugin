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
 * Block-entity component placed on the prefab origin block when a build order
 * is created. Tracks which prefab to build and the current build status.
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
            .append(new KeyedCodec<>("WorkstationPosition", Vector3i.CODEC),
                    (o, v) -> o.workstationPosition = v,
                    o -> o.workstationPosition)
            .add()
            .build();

    /** ID of the prefab to build at this origin. */
    public String prefabId = "";

    /** Build status: "Pending", "InProgress", or "Complete". */
    public String status = "Pending";

    /** World position of the workstation that owns this order. Null if unassigned. */
    public @Nullable Vector3i workstationPosition = null;

    // ===== Constructors =====
    public ConstructionOrderComponent() {}

    public ConstructionOrderComponent(String prefabId, Vector3i workstationPosition) {
        this.prefabId = prefabId;
        this.workstationPosition = workstationPosition;
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
        copy.workstationPosition = this.workstationPosition;
        return copy;
    }
}
