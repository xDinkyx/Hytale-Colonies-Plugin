package com.hytalecolonies.components.jobs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.HytaleColoniesPlugin;
import com.hytalecolonies.utils.ColonistStateUtil;

/**
 * Added to colonist to assign them a job.
 */
public class JobComponent implements Component<EntityStore> {
    // ===== Codec =====
    public static final BuilderCodec<JobComponent> CODEC = BuilderCodec.builder(JobComponent.class, JobComponent::new)
            .append(new KeyedCodec<>("WorkStationBlockPosition", Vector3i.CODEC),
                    (o, v) -> o.workStationBlockPosition = v,
                    o -> o.workStationBlockPosition)
            .add()
            .append(new KeyedCodec<>("JobState", JobState.CODEC),
                    (o, v) -> o.jobState = v,
                    o -> o.jobState)
            .add().build();

    // ===== Fields =====
    protected @Nullable Vector3i workStationBlockPosition = null;
    protected @Nullable JobState jobState = null; // ToDo: Probably move state logic to separate component.

    // ===== Constructors =====
    public JobComponent() {}

    public JobComponent(@Nullable Vector3i workStationBlockPos) {
        this.workStationBlockPosition = workStationBlockPos;
        this.jobState = JobState.Idle; // Start immediately in Idle so the work loop begins on the next system tick.
    }

    // ===== Component Type =====
    public static ComponentType<EntityStore, JobComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getJobComponentType();
    }

    // ===== Component Clone =====
    @Override
    public @Nullable Component<EntityStore> clone() {
        JobComponent copy = new JobComponent(this.workStationBlockPosition);
        copy.jobState = this.jobState;
        return copy;
    }

    // ===== Public Methods =====
    public boolean isEmployed() {
        return workStationBlockPosition != null;
    }

    // ===== Getters and Setters =====
    public @Nullable Vector3i getWorkStationBlockPosition() {
        return workStationBlockPosition;
    }
    public void setWorkStationBlockPosition(@Nullable Vector3i workStationBlockPosition) {
        this.workStationBlockPosition = workStationBlockPosition;
    }
    public @Nullable JobState getCurrentTask() {
        return jobState;
    }

    /// Only updates the field if the caller has the key, which is only obtainable by code in {@link ColonistStateUtil}.
    /// This enforces that all state changes go through the utility method which mirrors to NPC role state.
    public void setCurrentTask(@Nonnull ColonistStateUtil.Key key, @Nullable JobState currentTask) {
        this.jobState = currentTask;
    }
}
