package com.hytalecolonies.components.jobs;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Generic job-target component. Present on a colonist during an active job
 * cycle (from claiming a target through returning home).
 *
 * <p>{@code targetPosition} is the world position the colonist should travel
 * to for their task. It is set to {@code null} once the task finishes and
 * the colonist begins heading home, so that
 * {@link com.hytalecolonies.systems.jobs.JobAssignmentSystems.StaleMarkCleanupSystem}
 * does not treat an already-released claim as still active.
 *
 * <p>{@code lastKnownPosition} and {@code stuckTicks} are transient
 * stuck-detection fields used by
 * {@link com.hytalecolonies.systems.jobs.ColonistMovementSystem}.
 * They are never persisted and reset to defaults on load.
 */
public class JobTargetComponent implements Component<EntityStore> {

    // ===== Codec =====
    public static final BuilderCodec<JobTargetComponent> CODEC = BuilderCodec
            .builder(JobTargetComponent.class, JobTargetComponent::new)
            .append(new KeyedCodec<>("TargetPosition", Vector3i.CODEC),
                    (o, v) -> o.targetPosition = v,
                    o -> o.targetPosition)
            .add()
            .build();

    // ===== Fields =====
    /** Where the colonist is headed for their job task. Null while the colonist is heading home. */
    public @Nullable Vector3i targetPosition = null;

    /** Last recorded cell position for stuck detection. Transient -- not persisted. */
    public @Nullable Vector3i lastKnownPosition = null;

    /** How many consecutive ticks the position has been unchanged while traveling. Transient -- not persisted. */
    public int stuckTicks = 0;

    // ===== Constructors =====
    public JobTargetComponent() {}

    public JobTargetComponent(@Nullable Vector3i targetPosition) {
        this.targetPosition = targetPosition;
    }

    // ===== Component Type =====
    public static ComponentType<EntityStore, JobTargetComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getJobTargetComponentType();
    }

    // ===== Component Clone =====
    @Override
    public JobTargetComponent clone() {
        JobTargetComponent copy = new JobTargetComponent(this.targetPosition);
        copy.lastKnownPosition = this.lastKnownPosition;
        copy.stuckTicks = this.stuckTicks;
        return copy;
    }

    // ===== Getters/Setters =====
    public @Nullable Vector3i getTargetPosition() {
        return targetPosition;
    }

    public void setTargetPosition(@Nullable Vector3i targetPosition) {
        this.targetPosition = targetPosition;
    }
}
