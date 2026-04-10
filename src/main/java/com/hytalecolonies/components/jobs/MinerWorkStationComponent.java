package com.hytalecolonies.components.jobs;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nullable;

public class MinerWorkStationComponent implements Component<ChunkStore> {

    // ===== Codec =====
    public static final BuilderCodec<MinerWorkStationComponent> CODEC = BuilderCodec
            .builder(MinerWorkStationComponent.class, MinerWorkStationComponent::new)
            .append(new KeyedCodec<>("MineOrigin", Vector3i.CODEC),
                    (o, v) -> o.mineOrigin = v,
                    o -> o.mineOrigin)
            .add()
            .append(new KeyedCodec<>("MineSize", Codec.INTEGER),
                    (o, v) -> o.mineSize = v,
                    o -> o.mineSize)
            .add()
            .append(new KeyedCodec<>("MineOffsetZ", Codec.INTEGER),
                    (o, v) -> o.mineOffsetZ = v,
                    o -> o.mineOffsetZ)
            .add()
            .build();

    /**
     * Top-north-west corner of the mine shaft. Null until first miner starts; set once.
     */
    public @Nullable Vector3i mineOrigin = null;
    /** Side length of the mine shaft in blocks. */
    public int mineSize = 4;
    /** Distance in +Z blocks from the workstation where the mine shaft begins. */
    public int mineOffsetZ = 5;

    // ===== Constructors =====
    public MinerWorkStationComponent() {}

    // ===== Component Type =====
    public static ComponentType<ChunkStore, MinerWorkStationComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getMinerWorkStationComponentType();
    }

    // ===== Clone =====
    @Override
    public @Nullable Component<ChunkStore> clone() {
        MinerWorkStationComponent copy = new MinerWorkStationComponent();
        copy.mineOrigin = this.mineOrigin;
        copy.mineSize = this.mineSize;
        copy.mineOffsetZ = this.mineOffsetZ;
        return copy;
    }
}
