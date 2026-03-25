package com.hytalecolonies.components.jobs;

// Imports
import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nullable;

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
            .append(new KeyedCodec<>("AssignedColonists", new ArrayCodec<>(Codec.UUID_BINARY, UUID[]::new)),
                    (o, v) -> {
                        o.assignedColonists.clear();
                        Collections.addAll(o.assignedColonists, v);
                    }, o -> o.assignedColonists.toArray(UUID[]::new))
            .add()
            // ===== Woodsman config =====
            .append(new KeyedCodec<>("TreeSearchRadius", Codec.FLOAT),
                    (o, v) -> o.treeSearchRadius = v,
                    o -> o.treeSearchRadius)
            .add()
            .append(new KeyedCodec<>("AllowedTreeTypes", new SetCodec<>(Codec.STRING, HashSet::new, false)),
                    (o, v) -> o.allowedTreeTypes = v,
                    o -> o.allowedTreeTypes)
            .add()
            .build();

    // ===== Fields =====
    protected JobType jobType;
    protected int maxWorkers = 1;
    protected Set<UUID> assignedColonists = new HashSet<>();
    /** Search radius for harvestable trees (Woodsman). */
    public float treeSearchRadius = 64.0f;
    /**
     * Block type keys the woodsman is allowed to harvest. Null until first accessed;
     * defaults to the {@value DEFAULT_TREE_TYPE_LIST} BlockTypeList asset.
     */
    public @Nullable Set<String> allowedTreeTypes = null;


    // ===== Constructors =====
    public WorkStationComponent() {
    }

    public WorkStationComponent(JobType jobType, int maxWorkers) {
        this.jobType = jobType;
        this.maxWorkers = maxWorkers;
    }

    // ===== Component Type =====
    public static ComponentType<ChunkStore, WorkStationComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getWorkStationComponentType();
    }

    // ===== Component Clone =====
    @Override
    public @Nullable Component<ChunkStore> clone() {
        WorkStationComponent copy = new WorkStationComponent(this.jobType, this.maxWorkers);
        copy.assignedColonists = new HashSet<>(this.assignedColonists);
        copy.treeSearchRadius = this.treeSearchRadius;
        copy.allowedTreeTypes = this.allowedTreeTypes != null ? new HashSet<>(this.allowedTreeTypes) : null;
        return copy;
    }

    // ===== Woodsman helpers =====

    /**
     * Returns the set of allowed tree type keys for this woodsman workstation.
     * Lazily loads the default tree wood block type list if not explicitly set.
     */
    public Set<String> getAllowedTreeTypes() {
        if (allowedTreeTypes == null) {
            BlockTypeListAsset asset = BlockTypeListAsset.getAssetMap().getAsset(DEFAULT_TREE_TYPE_LIST);
            allowedTreeTypes = asset != null ? asset.getBlockTypeKeys() : Collections.emptySet();
        }
        return allowedTreeTypes;
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
