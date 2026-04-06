package com.hytalecolonies.components.jobs;

// Imports

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
    /** Epoch-ms timestamp set when the colonist enters {@link JobState#CollectingDrops}. Transient -- not persisted. */
    public long collectingDropsSince = 0L;
    /** World position of the linked delivery container. Transient -- re-discovered each delivery run. */
    public @Nullable Vector3i deliveryContainerPosition = null;
    /**
     * {@code false} when no work targets were found. Cleared when work is found or target released.
     * Read by {@link com.hytalecolonies.npc.sensors.SensorNoWorkAvailable}. Transient.
     */
    public boolean workAvailable = true;
    /** Set by {@code ActionNotifyBlockBroken}; read and cleared by {@link com.hytalecolonies.systems.jobs.MinerWorkingSystem}. Transient. */
    public boolean blockBrokenNotification = false;

    // ===== Constructors =====
    public JobComponent() {}

    public JobComponent(@Nullable Vector3i workStationBlockPos) {
        this.workStationBlockPosition = workStationBlockPos;
        this.jobState = JobState.Idling; // Start immediately in Idling so the work loop begins on the next system tick.
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
