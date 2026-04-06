package com.hytalecolonies.components.world;

import com.hypixel.hytale.math.vector.Vector3i;

/**
 * Marker interface for {@code ChunkStore} components that represent a block
 * a colonist can reserve for exclusive use during a job.
 *
 * <p>Job systems call {@link #unclaim()} when releasing the block.
 * The {@link com.hytalecolonies.systems.jobs.ColonistCleanupSystem} uses
 * {@link #isClaimed()} and {@link #getClaimPosition()} during the periodic
 * orphan-mark sweep.
 *
 * <p>Register each implementing component type with
 * {@link ClaimedBlockRegistry#register} on plugin startup so the general
 * unclaim utility knows which component types to check.
 */
public interface IClaimableBlock {

    /** Returns {@code true} if this block is currently reserved by a colonist. */
    boolean isClaimed();

    /**
     * Returns the world position used as the claim key -- must match the value
     * stored in the colonist's
     * {@link com.hytalecolonies.components.jobs.JobTargetComponent#targetPosition}.
     */
    Vector3i getClaimPosition();

    /** Releases this block so another colonist can claim it. */
    void unclaim();
}
