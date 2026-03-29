package com.hytalecolonies.components.world;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.util.UUID;

/**
 * Block entity component that records an exclusive block claim by a colonist.
 *
 * <p>Added to a block's ChunkStore entity when a colonist reserves it for work
 * (e.g. tree harvesting, mining, using a container). Removed when the colonist
 * finishes, is fired, or the block entity is destroyed.
 *
 * <p>Use {@link com.hytalecolonies.utils.ClaimBlockUtil} to add and remove
 * claims; never mutate this component directly.
 *
 * <p>The component is persisted across server restarts so that the
 * {@link com.hytalecolonies.systems.jobs.ColonistCleanupSystem} can detect
 * and release stale claims left by crashes or unexpected colonist removal.
 */
public class ClaimedBlockComponent implements Component<ChunkStore> {

    // ===== Codec =====
    public static final BuilderCodec<ClaimedBlockComponent> CODEC = BuilderCodec
            .builder(ClaimedBlockComponent.class, ClaimedBlockComponent::new)
            .append(new KeyedCodec<>("ClaimedBy", Codec.UUID_BINARY),
                    (o, v) -> o.claimedByUuid = v,
                    o -> o.claimedByUuid)
            .add()
            .append(new KeyedCodec<>("ClaimType", Codec.STRING),
                    (o, v) -> o.claimType = v,
                    o -> o.claimType)
            .add()
            .build();

    // ===== Fields =====
    UUID claimedByUuid;
    String claimType;

    // ===== Constructors =====
    public ClaimedBlockComponent() {}

    public ClaimedBlockComponent(UUID claimedByUuid, String claimType) {
        this.claimedByUuid = claimedByUuid;
        this.claimType = claimType;
    }

    // ===== Component Type =====
    public static ComponentType<ChunkStore, ClaimedBlockComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getClaimedBlockComponentType();
    }

    // ===== Component Clone =====
    @Override
    public ClaimedBlockComponent clone() {
        ClaimedBlockComponent copy = new ClaimedBlockComponent();
        copy.claimedByUuid = this.claimedByUuid;
        copy.claimType = this.claimType;
        return copy;
    }

    // ===== Getters =====
    public UUID getClaimedByUuid() {
        return claimedByUuid;
    }

    public String getClaimType() {
        return claimType;
    }
}
