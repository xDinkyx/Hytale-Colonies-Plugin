package com.hytalecolonies.components.jobs;

import java.util.LinkedList;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.HytaleColoniesPlugin;

/** Identifies a colonist as a miner. Holds transient per-session mining state. */
public class MinerJobComponent implements Component<EntityStore> {

    // ===== Codec =====
    public static final BuilderCodec<MinerJobComponent> CODEC = BuilderCodec
            .builder(MinerJobComponent.class, MinerJobComponent::new)
            .build();

    /**
     * Transient: ore blocks queued for mining (vein detected adjacent to cleared mine region).
     * Not persisted -- populated at runtime by MinerWorkingSystem and consumed by
     * ActionSeekNextOreVeinBlock.
     */
    public final LinkedList<Vector3i> oreVeinQueue = new LinkedList<>();

    // ===== Constructors =====
    public MinerJobComponent() {}

    // ===== Component Type =====
    public static ComponentType<EntityStore, MinerJobComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getMinerJobComponentType();
    }

    // ===== Cloneable =====
    @Override
    public @Nullable Component<EntityStore> clone() {
        // oreVeinQueue is transient -- clone starts with an empty queue.
        return new MinerJobComponent();
    }
}
