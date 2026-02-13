package com.colonies.coloniesplugin.components.jobs;

// Imports
import com.colonies.coloniesplugin.ColoniesPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Component for blocks that provide jobs to colonists.
 */
public class JobProviderComponent implements Component<ChunkStore> {

    // ===== Codec =====
    public static final BuilderCodec<JobProviderComponent> CODEC = BuilderCodec.builder(JobProviderComponent.class, JobProviderComponent::new)
            .append(new KeyedCodec<>("JobType", JobType.CODEC),
                    (o, v) -> o.jobType = v,
                    o -> o.jobType)
            .add()
            .append(new KeyedCodec<>("MaxWorkers", Codec.INTEGER),
                    (o, v) -> o.maxWorkers = v,
                    o -> o.maxWorkers)
            .add()
            .append(new KeyedCodec<>("AssignedColonists", new ArrayCodec<>(Codec.UUID_STRING, UUID[]::new)),
                    (o, v) -> {
                        o.assignedColonists = new HashSet<>();
                        Collections.addAll(o.assignedColonists, v);
                    }, o -> o.assignedColonists.toArray(UUID[]::new))
            .add().build();

    // ===== Fields =====
    protected JobType jobType;
    protected int maxWorkers = 1;
    protected Set<UUID> assignedColonists = new HashSet<>();

    // ===== Constructors =====
    public JobProviderComponent() {
    }

    public JobProviderComponent(JobType jobType, int maxWorkers) {
        this.jobType = jobType;
        this.maxWorkers = maxWorkers;
    }

    // ===== Component Type =====
    public static ComponentType<ChunkStore, JobProviderComponent> getComponentType() {
        return ColoniesPlugin.getInstance().getJobProviderComponentType();
    }

    // ===== Component Clone =====
    @Override
    public @Nullable Component<ChunkStore> clone() {
        var copy = new JobProviderComponent(this.jobType, this.maxWorkers);
        copy.assignedColonists = new HashSet<>(this.assignedColonists);
        return copy;
    }

    // ===== Public Methods =====
    public void assignColonist(UUID colonistId) {
        if (assignedColonists.size() >= maxWorkers) {
            throw new IllegalStateException("No available job slots to assign colonist.");
        }
        assignedColonists.add(colonistId);
    }

    // ===== Getters and Setters =====
    public int getAvailableJobSlots() {
        return Math.max(0, maxWorkers - assignedColonists.size());
    }

    public JobType getJobType() {
        return jobType;
    }

    public void setJobType(JobType jobType) {
        this.jobType = jobType;
    }

    public int getMaxWorkers() {
        return maxWorkers;
    }

    public void setMaxWorkers(int maxWorkers) {
        this.maxWorkers = maxWorkers;
    }

    public Set<UUID> getAssignedColonists() {
        return assignedColonists;
    }

    public void setAssignedColonists(Set<UUID> assignedColonists) {
        this.assignedColonists = assignedColonists;
    }
}
