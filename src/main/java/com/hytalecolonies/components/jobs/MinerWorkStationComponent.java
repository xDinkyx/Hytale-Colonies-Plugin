package com.hytalecolonies.components.jobs;

import java.util.UUID;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hytalecolonies.HytaleColoniesPlugin;

public class MinerWorkStationComponent implements Component<ChunkStore>
{
    // ===== Codec =====
    public static final BuilderCodec<MinerWorkStationComponent> CODEC =
            BuilderCodec.builder(MinerWorkStationComponent.class, MinerWorkStationComponent::new)
                    .append(new KeyedCodec<>("ActiveSegmentId", Codec.UUID_STRING), (o, v) -> o.activeSegmentId = v, o -> o.activeSegmentId)
                    .add()
                    .build();

    /** ID of the current {@link com.hytalecolonies.MineSegmentStore.Entry} being excavated, or null. */
    public @Nullable UUID activeSegmentId = null;

    // ===== Constructors =====
    public MinerWorkStationComponent() {}

    // ===== Component Type =====
    public static ComponentType<ChunkStore, MinerWorkStationComponent> getComponentType()
    {
        return HytaleColoniesPlugin.getInstance().getMinerWorkStationComponentType();
    }

    // ===== Clone =====
    @Override
    public @Nullable Component<ChunkStore> clone()
    {
        MinerWorkStationComponent copy = new MinerWorkStationComponent();
        copy.activeSegmentId = this.activeSegmentId;
        return copy;
    }
}
