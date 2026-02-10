package com.colonies.coloniesplugin.components.jobs;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.Nullable;

/// <summary>
/// Component for entities that provide jobs to colonists.
/// </summary>
public class JobProviderComponent implements Component<ChunkStore> {

    public static final BuilderCodec<JobProviderComponent> CODEC = BuilderCodec.builder(JobProviderComponent.class, JobProviderComponent::new)
            .append(new KeyedCodec<>("JobType", Codec.STRING), (jobProviderComponent, s) -> jobProviderComponent.JobType = s, jobProviderComponent -> jobProviderComponent.JobType)
            .add()
            .build();

    public String JobType;

    public JobProviderComponent() {
    }

    @Override
    public @Nullable Component<ChunkStore> clone() {
        return new JobProviderComponent();
    }
}
