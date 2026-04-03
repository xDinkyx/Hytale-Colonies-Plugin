package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.components.world.ClaimedBlockComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

/**
 * Watches for {@link ClaimedBlockComponent} removal and automatically destroys
 * block entities that were created solely to host a claim.
 *
 * <p>When a block entity has only {@code BlockStateInfo} remaining after
 * {@code ClaimedBlockComponent} is stripped (archetype length == 1), the entity
 * serves no purpose and is queued for removal via the {@link CommandBuffer}.
 *
 * <p>When a block is broken by a player the engine already calls
 * {@code removeEntity} on the block entity, which cascades
 * {@code onComponentRemoved} for every component including
 * {@code ClaimedBlockComponent}. In that path the ref is no longer valid when
 * this callback fires, so the guard at the top of {@link #onComponentRemoved}
 * prevents a double-destroy.
 */
public class ClaimedBlockCleanupSystem extends RefChangeSystem<ChunkStore, ClaimedBlockComponent> {

    @Override
    public ComponentType<ChunkStore, ClaimedBlockComponent> componentType() {
        return ClaimedBlockComponent.getComponentType();
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return ClaimedBlockComponent.getComponentType();
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull ClaimedBlockComponent component,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        // No action required when a claim is added.
    }

    @Override
    public void onComponentSet(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull ClaimedBlockComponent oldComponent,
            @Nonnull ClaimedBlockComponent newComponent,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        // No action required when a claim is replaced.
    }

    @Override
    public void onComponentRemoved(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull ClaimedBlockComponent removed,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        // Guard: if the entity is already being destroyed (e.g. the block was broken)
        // the ref will be invalid here -- avoid a double-remove.
        if (!ref.isValid()) {
            return;
        }

        // After ClaimedBlockComponent is stripped, check how many components remain.
        // A claim-only entity (created for a plain block with no other data) will have
        // exactly 1 component: BlockStateInfo. Entities with other meaningful data
        // (e.g. HarvestableTreeComponent, WorkStationComponent) will have >= 2.
        //
        // TODO: Evaluate whether it is safe to destroy entities with only BlockStateInfo
        //       remaining. Removing them could interfere with vanilla engine behaviour or
        //       other mods that expect block entities to persist. For now we log the
        //       scenario so we can observe it in the wild and decide later.
        Archetype<ChunkStore> archetype = store.getArchetype(ref);
        if (archetype.length() <= 1) {
            DebugLog.info(DebugCategory.CLAIM_SYSTEM,
                    "[Claim] Released claim on entity with only BlockStateInfo remaining " +
                    "(was held by %s, type=%s).",
                    removed.getClaimedByUuid(), removed.getClaimType());
            // commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
        }
    }
}
