package com.hytalecolonies.components.jobs;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Per-colonist component for woodsman colonists. Carries runtime state that
 * is meaningful across restarts; all job configuration (allowed tree types,
 * search radius) lives in {@link WorkStationComponent}.
 */
public class WoodsmanJobComponent implements Component<EntityStore> {

    // ===== Codec =====
    public static final BuilderCodec<WoodsmanJobComponent> CODEC = BuilderCodec
            .builder(WoodsmanJobComponent.class, WoodsmanJobComponent::new)
            // Persisted: woodsman resumes travel to the same tree after restart.
            .append(new KeyedCodec<>("LastKnownPosition", Vector3i.CODEC),
                    (o, v) -> o.lastKnownPosition = v,
                    o -> o.lastKnownPosition)
            .add()
            .build();

    // ===== Persisted runtime state =====
    public @Nullable Vector3i lastKnownPosition = null;

    // ===== Transient runtime state -- always reset on server restart =====
    /** Stuck-detection counter; meaningless after a restart. */
    public int stuckTicks = 0;

    // ===== Constructors =====
    public WoodsmanJobComponent() {}

    // ===== Component Type =====
    public static ComponentType<EntityStore, WoodsmanJobComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getWoodsmanJobComponentType();
    }

    // ===== Cloneable =====
    @Override
    public @Nullable Component<EntityStore> clone() {
        WoodsmanJobComponent copy = new WoodsmanJobComponent();
        copy.lastKnownPosition = this.lastKnownPosition;
        // stuckTicks intentionally not copied -- transient.
        return copy;
    }
}

