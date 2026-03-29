package com.hytalecolonies.components.world;

import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of {@link ChunkStore} component types that implement
 * {@link IClaimableBlock}.
 *
 * <p>Register each claimable block component type on plugin startup (before any
 * colonist is assigned). The general unclaim utility
 * ({@link #unclaimTargetBlock}) then works for any job type without needing to
 * know which specific component marks the block.
 *
 * <p>Example registration in the plugin:
 * <pre>{@code
 * ClaimedBlockRegistry.register(HarvestableTreeComponent.getComponentType());
 * }</pre>
 */
public final class ClaimedBlockRegistry {

    private static final List<ComponentType<ChunkStore, ? extends IClaimableBlock>> registeredTypes =
            new ArrayList<>();

    private ClaimedBlockRegistry() {}

    /**
     * Registers a claimable block component type.
     * Must be called during plugin setup before any colonist is assigned a job.
     */
    public static void register(ComponentType<ChunkStore, ? extends IClaimableBlock> type) {
        registeredTypes.add(type);
    }

    /**
     * Returns an unmodifiable view of all registered claimable component types.
     * Used by the cleanup system to sweep orphaned marks.
     */
    public static List<ComponentType<ChunkStore, ? extends IClaimableBlock>> getRegisteredTypes() {
        return Collections.unmodifiableList(registeredTypes);
    }

    /**
     * Releases the block claimed by the given colonist, if any.
     *
     * <p>Reads the target position from the colonist's {@link JobTargetComponent},
     * looks up the block entity, then calls {@link IClaimableBlock#unclaim()} on
     * whichever registered component type is present. Safe to call when no block
     * is claimed (no-op).
     */
    public static void unclaimTargetBlock(Ref<EntityStore> colonistRef, Store<EntityStore> entityStore) {
        JobTargetComponent jobTarget = entityStore.getComponent(colonistRef, JobTargetComponent.getComponentType());
        if (jobTarget == null || jobTarget.targetPosition == null) return;

        Vector3i pos = jobTarget.targetPosition;
        World world = entityStore.getExternalData().getWorld();
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
        if (blockRef == null) return;

        Store<ChunkStore> chunkStore = blockRef.getStore();
        for (ComponentType<ChunkStore, ? extends IClaimableBlock> type : registeredTypes) {
            IClaimableBlock claimable = chunkStore.getComponent(blockRef, type);
            if (claimable != null) {
                claimable.unclaim();
                return; // A block is only ever claimed by one component type.
            }
        }
    }
}
