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
 * Component for blocks that provide jobs for colonists.
 */
public class WorkStationComponent implements Component<ChunkStore> {

    // ===== Codec =====
    public static final BuilderCodec<WorkStationComponent> CODEC = BuilderCodec.builder(WorkStationComponent.class, WorkStationComponent::new)
            .append(new KeyedCodec<>("JobType", JobType.CODEC),
                    (o, v) -> o.jobType = v,
                    o -> o.jobType)
            .add()
            .append(new KeyedCodec<>("MaxWorkers", Codec.INTEGER),
                    (o, v) -> o.maxWorkers = v,
                    o -> o.maxWorkers)
            .add()
            // ToDo: This isn't working properly. Need to test if codec works in general and then figure out why this array codec is broken. Maybe convert to single string?
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
    public WorkStationComponent() {
    }

    public WorkStationComponent(JobType jobType, int maxWorkers) {
        this.jobType = jobType;
        this.maxWorkers = maxWorkers;
    }

    // ===== Component Type =====
    public static ComponentType<ChunkStore, WorkStationComponent> getComponentType() {
        return ColoniesPlugin.getInstance().getWorkStationComponentType();
    }

    // ===== Component Clone =====
    @Override
    public @Nullable Component<ChunkStore> clone() {
        var copy = new WorkStationComponent(this.jobType, this.maxWorkers);
        copy.assignedColonists = new HashSet<>(this.assignedColonists);
        return copy;
    }

    // ===== Public Methods =====
    public void assignColonist(UUID colonistUuid) {
        if (assignedColonists.size() >= maxWorkers) {
            throw new IllegalStateException("No available job slots to assign colonist.");
        }
        assignedColonists.add(colonistUuid);
    }

    public int getAvailableJobSlots() {
        return Math.max(0, maxWorkers - assignedColonists.size());
    }

    public void clearAssignedColonists() {
        assignedColonists.clear();
    }

    public void removeAssignedColonist(UUID colonistId) {
        assignedColonists.remove(colonistId);
    }

    // ===== Getters and Setters =====
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
