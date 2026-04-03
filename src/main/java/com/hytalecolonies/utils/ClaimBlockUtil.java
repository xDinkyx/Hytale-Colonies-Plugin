package com.hytalecolonies.utils;

import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.world.ClaimedBlockComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Utility for managing block claims across all colonist job types.
 *
 * <h3>Thread safety</h3>
 * <ul>
 *   <li>{@link #unclaimBlock} and {@link #unclaimByColonist} must be called
 *       from the world thread (e.g. inside a {@code world.execute()} callback)
 *       since they mutate ChunkStore data directly.</li>
 *   <li>Checking whether a block is already claimed from inside an EntityStore
 *       tick is safe because {@code store.getComponent()} is a read-only
 *       access.</li>
 * </ul>
 *
 * <h3>Multi-block support</h3>
 * {@link #resolveCanonicalPosition} applies the filler-block logic from the
 * engine (same as used by {@code BlockEntityInfoCommand}) to map any position
 * within a large multi-block structure to the canonical position that hosts
 * the block entity.
 */
public final class ClaimBlockUtil {

    private ClaimBlockUtil() {}

    // ===== Claim =====

    /**
     * Attempts to claim the block at {@code position} for the given colonist.
     *
     * <p>Works for <em>any</em> block, whether or not it has a pre-existing block entity:
     * <ul>
     *   <li>If a block entity already exists (e.g. tree with
     *       {@link com.hytalecolonies.components.world.HarvestableTreeComponent}), the
     *       {@link ClaimedBlockComponent} is attached to that entity and removed on
     *       unclaim without affecting the entity itself.</li>
     *   <li>If no block entity exists (e.g. a plain stone block in a mine), a minimal
     *       entity ({@code BlockStateInfo} + {@code ClaimedBlockComponent}) is created at
     *       the position. That entity is destroyed completely on unclaim.</li>
     * </ul>
     *
     * <p>Internally resolves filler-block indirection so multi-block structures are
     * always claimed at their canonical position.
     *
     * @param world         the world the block lives in
     * @param position      any position within the target block (filler-resolved automatically)
     * @param claimedByUuid UUID of the colonist taking the claim
     * @param claimType     free-form label for the claim purpose (e.g. {@code "Harvest"}, {@code "Mine"})
     * @return {@code true} if the claim was placed; {@code false} if the chunk is not
     *         loaded or the block is already claimed
     */
    public static boolean claimBlock(World world, Vector3i position, UUID claimedByUuid, String claimType) {
        Vector3i canonical = resolveCanonicalPosition(world, position);
        if (canonical == null) {
            DebugLog.fine(DebugCategory.CLAIM_SYSTEM,
                    "[Claim] claimBlock(%s) -- chunk not loaded, cannot claim.", position);
            return false;
        }

        long chunkIdx = ChunkUtil.indexChunkFromBlock(canonical.x, canonical.z);
        Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(chunkIdx);
        if (chunkRef == null || !chunkRef.isValid()) {
            DebugLog.fine(DebugCategory.CLAIM_SYSTEM,
                    "[Claim] claimBlock(%s) -- chunk ref not available.", canonical);
            return false;
        }

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        BlockComponentChunk bcc = chunkStore.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (bcc == null) {
            DebugLog.fine(DebugCategory.CLAIM_SYSTEM,
                    "[Claim] claimBlock(%s) -- no BlockComponentChunk.", canonical);
            return false;
        }

        int blockIndex = ChunkUtil.indexBlockInColumn(canonical.x, canonical.y, canonical.z);
        Ref<ChunkStore> blockRef = bcc.getEntityReference(blockIndex);

        if (blockRef != null && blockRef.isValid()) {
            // Pre-existing block entity (e.g. HarvestableTreeComponent) -- attach claim to it.
            ClaimedBlockComponent existing = chunkStore.getComponent(blockRef, ClaimedBlockComponent.getComponentType());
            if (existing != null) {
                DebugLog.fine(DebugCategory.CLAIM_SYSTEM,
                        "[Claim] claimBlock(%s) -- already claimed by %s (type=%s), rejecting.",
                        canonical, existing.getClaimedByUuid(), existing.getClaimType());
                return false;
            }
            chunkStore.putComponent(blockRef, ClaimedBlockComponent.getComponentType(),
                    new ClaimedBlockComponent(claimedByUuid, claimType));
            DebugLog.info(DebugCategory.CLAIM_SYSTEM,
                    "[Claim] Claimed block at %s by %s (type=%s).", canonical, claimedByUuid, claimType);
        } else {
            // No block entity -- create a minimal one for the claim.
            // ClaimedBlockCleanupSystem automatically destroys this entity when the
            // claim is released if no other components are present.
            Holder<ChunkStore> holder = ChunkStore.REGISTRY.newHolder();
            holder.putComponent(BlockModule.BlockStateInfo.getComponentType(),
                    new BlockModule.BlockStateInfo(blockIndex, chunkRef));
            holder.putComponent(ClaimedBlockComponent.getComponentType(),
                    new ClaimedBlockComponent(claimedByUuid, claimType));
            chunkStore.addEntity(holder, AddReason.SPAWN);
            DebugLog.info(DebugCategory.CLAIM_SYSTEM,
                    "[Claim] Created claim entity at %s for %s (type=%s).", canonical, claimedByUuid, claimType);
        }
        return true;
    }

    // ===== Claim resolution =====

    /**
     * Maps a block position to the canonical (main-block) position, following
     * filler-block indirection for multi-space blocks (e.g. large doors,
     * multi-block workstations).
     *
     * @return the canonical world position, or {@code null} if the chunk is
     *         not loaded
     */
    @Nullable
    public static Vector3i resolveCanonicalPosition(World world, Vector3i position) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(position.x, position.z);
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(chunkIndex);

        if (chunkRef == null || !chunkRef.isValid()) return null;

        BlockChunk blockChunk = chunkStore.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) return position;

        int blockId = blockChunk.getBlock(position.x, position.y, position.z);
        if (blockId == 0) return position;

        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) return position;

        BlockSection blockSection = blockChunk.getSectionAtBlockY(position.y);
        BlockBoundingBoxes hitbox = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());

        if (blockSection != null && hitbox != null && hitbox.protrudesUnitBox()) {
            int idx = ChunkUtil.indexBlock(position.x, position.y, position.z);
            int filler = blockSection.getFiller(idx);
            int fillerX = FillerBlockUtil.unpackX(filler);
            int fillerY = FillerBlockUtil.unpackY(filler);
            int fillerZ = FillerBlockUtil.unpackZ(filler);
            return Vector3i.add(position, new Vector3i(-fillerX, -fillerY, -fillerZ));
        }

        return position;
    }

    // ===== Unclaim =====

    /**
     * Removes the {@link ClaimedBlockComponent} from the block entity at the given position.
     *
     * <p>Cleanup of claim-only entities (those that have no other components) is handled
     * automatically by {@link com.hytalecolonies.systems.world.ClaimedBlockCleanupSystem},
     * which watches for {@code ClaimedBlockComponent} removal and destroys the entity if
     * nothing else uses it.
     *
     * <p>Safe to call when no claim exists (no-op).
     * Must be called from the world thread (e.g. inside {@code world.execute()}).
     */
    public static void unclaimBlock(World world, Vector3i position) {
        Vector3i canonical = resolveCanonicalPosition(world, position);
        if (canonical == null) {
            DebugLog.fine(DebugCategory.CLAIM_SYSTEM,
                    "[Claim] unclaimBlock(%s) -- chunk not loaded, nothing to remove.", position);
            return;
        }

        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, canonical.x, canonical.y, canonical.z);
        if (blockRef == null || !blockRef.isValid()) {
            DebugLog.fine(DebugCategory.CLAIM_SYSTEM,
                    "[Claim] unclaimBlock(%s) -- no block entity, nothing to remove.", canonical);
            return;
        }

        Store<ChunkStore> chunkStore = blockRef.getStore();
        ClaimedBlockComponent existing = chunkStore.getComponent(blockRef, ClaimedBlockComponent.getComponentType());
        if (existing == null) {
            DebugLog.fine(DebugCategory.CLAIM_SYSTEM,
                    "[Claim] unclaimBlock(%s) -- no ClaimedBlockComponent present, nothing to remove.", canonical);
            return;
        }

        chunkStore.tryRemoveComponent(blockRef, ClaimedBlockComponent.getComponentType());
        DebugLog.info(DebugCategory.CLAIM_SYSTEM,
                "[Claim] Unclaimed block at %s (was held by %s, type=%s).",
                canonical, existing.getClaimedByUuid(), existing.getClaimType());
    }

    /**
     * Reads the colonist's {@link JobTargetComponent} and releases the claim
     * on the targeted block, if any.
     *
     * <p>Must be called from the world thread (e.g. inside a
     * {@code world.execute()} callback).
     */
    public static void unclaimByColonist(Ref<EntityStore> colonistRef, Store<EntityStore> entityStore) {
        JobTargetComponent jobTarget = entityStore.getComponent(colonistRef, JobTargetComponent.getComponentType());
        if (jobTarget == null || jobTarget.targetPosition == null) {
            DebugLog.fine(DebugCategory.CLAIM_SYSTEM,
                    "[Claim] unclaimByColonist -- no JobTargetComponent or null targetPosition, nothing to release.");
            return;
        }

        World world = entityStore.getExternalData().getWorld();
        DebugLog.fine(DebugCategory.CLAIM_SYSTEM,
                "[Claim] unclaimByColonist -- releasing claim at %s.", jobTarget.targetPosition);
        unclaimBlock(world, jobTarget.targetPosition);
    }
}
