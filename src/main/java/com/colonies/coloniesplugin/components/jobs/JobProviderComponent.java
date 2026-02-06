package com.colonies.coloniesplugin.components.jobs;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.Nullable;

/// <summary>
/// Component for entities that provide jobs to colonists.
/// </summary>
public class JobProviderComponent implements Component<ChunkStore> {
    public JobProviderComponent() {
    }

    @Override
    public @Nullable Component<ChunkStore> clone() {
        return new JobProviderComponent();
    }
}
