package com.colonies.coloniesplugin.components.jobs;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/// <summary>
/// Component for entities that provide jobs to colonists.
/// </summary>
public class JobProviderComponent implements Component<ChunkStore> {

    public static final BuilderCodec<JobProviderComponent> CODEC = BuilderCodec.builder(JobProviderComponent.class, JobProviderComponent::new)
            .append(new KeyedCodec<>("JobType", Codec.STRING), (jobProviderComponent, s) -> jobProviderComponent.JobType = s, jobProviderComponent -> jobProviderComponent.JobType)
            .add()
            .build();

    public String JobType;
    public int MaxWorkers = 1; // ToDo: Add to codec
    public Set<UUID> AssignedColonists = new HashSet<>(); // ToDo: Add to codec (but first figure out how)

    public JobProviderComponent() {
    }

    @Override
    public @Nullable Component<ChunkStore> clone() {
        return new JobProviderComponent();
    }

    public int getAvailableJobSlots() {
        return Math.max(0, MaxWorkers - AssignedColonists.size());
    }

    public void assignColonist(UUID colonistId) {
        if (AssignedColonists.size() >= MaxWorkers) {
            throw new IllegalStateException("No available job slots to assign colonist.");
        }
        AssignedColonists.add(colonistId);
    }
}
