package com.colonies.coloniesplugin.components.jobs;

// Imports
import com.colonies.coloniesplugin.ColoniesPlugin;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.Nullable;

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
    protected @Nullable JobState jobState = null;

    // ===== Constructors =====
    public JobComponent() {}

    public JobComponent(@Nullable Vector3i workStationBlockPos) {
        this.workStationBlockPosition = workStationBlockPos;
    }

    // ===== Component Type =====
    public static ComponentType<EntityStore, JobComponent> getComponentType() {
        return ColoniesPlugin.getInstance().getColonistJobComponentType();
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
    public void setCurrentTask(@Nullable JobState currentTask) {
        this.jobState = currentTask;
    }
}
