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
public class ColonistJobComponent implements Component<EntityStore> {
    // ===== Codec =====
    public static final BuilderCodec<ColonistJobComponent> CODEC = BuilderCodec.builder(ColonistJobComponent.class, ColonistJobComponent::new)
            .append(new KeyedCodec<>("JobProviderBlockPosition", Vector3i.CODEC),
                    (o, v) -> o.jobProviderBlockPosition = v,
                    o -> o.jobProviderBlockPosition)
            .add()
            .append(new KeyedCodec<>("JobState", JobState.CODEC),
                    (o, v) -> o.jobState = v,
                    o -> o.jobState)
            .add().build();

    // ===== Fields =====
    protected @Nullable Vector3i jobProviderBlockPosition = null;
    protected @Nullable JobState jobState = null;

    // ===== Constructors =====
    public ColonistJobComponent() {}

    public ColonistJobComponent(@Nullable Vector3i jobProviderBlockPosition) {
        this.jobProviderBlockPosition = jobProviderBlockPosition;
    }

    // ===== Component Type =====
    public static ComponentType<EntityStore, ColonistJobComponent> getComponentType() {
        return ColoniesPlugin.getInstance().getColonistJobComponentType();
    }

    // ===== Component Clone =====
    @Override
    public @Nullable Component<EntityStore> clone() {
        ColonistJobComponent copy = new ColonistJobComponent(this.jobProviderBlockPosition);
        copy.jobState = this.jobState;
        return copy;
    }

    // ===== Public Methods =====
    public boolean isEmployed() {
        return jobProviderBlockPosition != null;
    }

    // ===== Getters and Setters =====
    public @Nullable Vector3i getJobProviderBlockPosition() {
        return jobProviderBlockPosition;
    }
    public void setJobProviderBlockPosition(@Nullable Vector3i jobProviderBlockPosition) {
        this.jobProviderBlockPosition = jobProviderBlockPosition;
    }
    public @Nullable JobState getCurrentTask() {
        return jobState;
    }
    public void setCurrentTask(@Nullable JobState currentTask) {
        this.jobState = currentTask;
    }
}
