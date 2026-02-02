package com.colonies.coloniesplugin.components.jobs;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// <summary>
/// Component for entities that provide jobs to colonists.
/// </summary>
public class JobProviderComponent implements Component<EntityStore> {

    public JobType jobType; // e.g., "WOODCUTTER"
    public int maxWorkers = 1;

    public List<UUID> assignedWorkerIds = new ArrayList<>();

    public boolean isOperational = true;

    public JobProviderComponent() {}

    public JobProviderComponent(JobType jobType, int maxWorkers) {
        this.jobType = jobType;
        this.maxWorkers = maxWorkers;
    }

    @Override
    public @Nullable Component<EntityStore> clone() {
        return new JobProviderComponent(this.jobType, this.maxWorkers);
    }
}
