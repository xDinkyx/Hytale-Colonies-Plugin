package com.hytalecolonies.npc.sensors;

import com.hytalecolonies.components.world.ClaimedBlockComponent;
import com.hytalecolonies.components.world.HarvestableTreeComponent;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hypixel.hytale.server.npc.sensorinfo.PositionProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Runtime sensor that detects the nearest unclaimed harvestable tree within
 * a configured range and provides its base position for {@code Seek} body motion.
 *
 * <p>Claim semantics: when the sensor first fires it schedules adding a
 * {@link ClaimedBlockComponent} (with this NPC's UUID) on the tree's block
 * entity via {@code world.execute()}. On subsequent ticks the claim is
 * validated against the component — if another colonist won the race the sensor
 * releases local state and searches for a different tree.
 *
 * <p>Constructed by {@link BuilderSensorHarvestableTree}.
 */
public class SensorHarvestableTree extends SensorBase {

    private static final Query<ChunkStore> TREE_QUERY =
            Query.and(HarvestableTreeComponent.getComponentType());

    private final double range;
    private final PositionProvider positionProvider = new PositionProvider();

    /** Position of the tree this sensor has optimistically claimed, or {@code null} if none. */
    @Nullable
    private Vector3i claimedTreePos = null;

    /** Cached UUID of this NPC entity (lazy-initialised on first tick). */
    @Nullable
    private UUID myUuid = null;

    public SensorHarvestableTree(@Nonnull BuilderSensorHarvestableTree builder,
                                 @Nonnull BuilderSupport support) {
        super(builder);
        this.range = builder.getRange(support);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref,
                           @Nonnull Role role,
                           double dt,
                           @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, role, dt, store)) {
            releaseClaim(store);
            positionProvider.clear();
            return false;
        }

        // Lazy-initialise the NPC's UUID for claim ownership checks.
        if (myUuid == null) {
            UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uuidComp != null) myUuid = uuidComp.getUuid();
        }

        World world = store.getExternalData().getWorld();
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            positionProvider.clear();
            return false;
        }
        Vector3d entityPos = transform.getPosition();

        // ------------------------------------------------------------------
        // Validate existing optimistic claim
        // ------------------------------------------------------------------
        if (claimedTreePos != null) {
            Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, claimedTreePos.x, claimedTreePos.y, claimedTreePos.z);
            if (blockRef != null && blockRef.isValid()) {
                ClaimedBlockComponent claim = blockRef.getStore().getComponent(blockRef, ClaimedBlockComponent.getComponentType());
                if (claim != null && myUuid != null && myUuid.equals(claim.getClaimedByUuid())) {
                    // Our claim is confirmed — keep providing the same position.
                    positionProvider.setTarget(
                            claimedTreePos.x + 0.5, claimedTreePos.y, claimedTreePos.z + 0.5);
                    return true;
                }
            }
            // Block is gone, or another colonist won the race and holds the claim.
            claimedTreePos = null;
        }

        // ------------------------------------------------------------------
        // Find the nearest unclaimed tree within range
        // ------------------------------------------------------------------
        Vector3i nearest = findNearestUnclaimedTree(world, entityPos, range);
        if (nearest == null) {
            positionProvider.clear();
            return false;
        }

        // Optimistically record the claim locally; schedule the actual block entity
        // component addition on the world thread via ClaimBlockUtil.claimBlock(),
        // which handles the race-condition check atomically. The UUID validation
        // above handles the case where another NPC wins the race before our callback runs.
        claimedTreePos = nearest;
        if (myUuid != null) {
            final UUID claimUuid = myUuid;
            final Vector3i claimPos = nearest;
            world.execute(() -> ClaimBlockUtil.claimBlock(world, claimPos, claimUuid, "Harvest"));
        }

        positionProvider.setTarget(nearest.x + 0.5, nearest.y, nearest.z + 0.5);
        return true;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return positionProvider;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Scans the ChunkStore for the nearest unclaimed {@link HarvestableTreeComponent}
     * within {@code range} metres of {@code entityPos}.
     */
    @Nullable
    private static Vector3i findNearestUnclaimedTree(
            @Nonnull World world,
            @Nonnull Vector3d entityPos,
            double range) {
        double rangeSq = range * range;
        Vector3i[] nearestRef = {null};
        double[] nearestDistSq = {Double.MAX_VALUE};

        world.getChunkStore().getStore().forEachChunk(TREE_QUERY, (chunk, unused) -> {
            for (int i = 0; i < chunk.size(); i++) {
                HarvestableTreeComponent tree =
                        chunk.getComponent(i, HarvestableTreeComponent.getComponentType());
                if (tree == null) continue;
                // Skip trees that already have a ClaimedBlockComponent on the same entity.
                if (chunk.getComponent(i, ClaimedBlockComponent.getComponentType()) != null) continue;

                Vector3i base = tree.getBasePosition();
                if (base == null) continue;

                double dx = base.x + 0.5 - entityPos.x;
                double dz = base.z + 0.5 - entityPos.z;
                double distSq = dx * dx + dz * dz;

                if (distSq <= rangeSq && distSq < nearestDistSq[0]) {
                    nearestRef[0] = base;
                    nearestDistSq[0] = distSq;
                }
            }
        });

        return nearestRef[0];
    }

    /**
     * Releases the current claim via {@code world.execute()} and clears local state.
     */
    private void releaseClaim(@Nonnull Store<EntityStore> store) {
        if (claimedTreePos == null) return;
        World world = store.getExternalData().getWorld();
        final Vector3i pos = claimedTreePos;
        claimedTreePos = null;
        world.execute(() -> ClaimBlockUtil.unclaimBlock(world, pos));
    }
}
