package com.hytalecolonies.components.jobs;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Generic worker component carried by every colonist that performs a task-based
 * job (miner, woodsman, farmer, etc.).
 *
 * <p>Contains only transient state; nothing is serialized. This means if the
 * server restarts while the colonist was unable to find work, they will simply
 * try again on the next cycle, which is the correct behaviour.
 */
public class WorkerComponent implements Component<EntityStore> {

    // ===== Codec (no persisted fields) =====
    public static final BuilderCodec<WorkerComponent> CODEC = BuilderCodec
            .builder(WorkerComponent.class, WorkerComponent::new)
            .build();

    // ===== Transient runtime state =====
    /**
     * Set to {@code true} by any seek action when it scans for work and finds
     * none available (e.g. shaft exhausted, no trees left, no crops ready).
     * Cleared back to {@code false} when work is found.
     *
     * <p>Checked by {@link com.hytalecolonies.npc.sensors.SensorNoWorkAvailable}.
     */
    public boolean noWorkAvailable = false;

    // ===== Constructors =====
    public WorkerComponent() {}

    // ===== Component Type =====
    public static ComponentType<EntityStore, WorkerComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getWorkerComponentType();
    }

    // ===== Cloneable =====
    @Override
    public @Nullable Component<EntityStore> clone() {
        WorkerComponent copy = new WorkerComponent();
        copy.noWorkAvailable = this.noWorkAvailable;
        return copy;
    }
}
