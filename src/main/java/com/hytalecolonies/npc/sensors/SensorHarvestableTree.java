package com.hytalecolonies.npc.sensors;

import com.hytalecolonies.components.world.HarvestableTreeComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
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

/**
 * Runtime sensor that detects the nearest unclaimed harvestable tree within
 * a configured range and provides its base position for {@code Seek} body motion.
 *
 * <p>Claim semantics:
 * <ul>
 *   <li>When the sensor first fires it marks the found tree's
 *       {@link HarvestableTreeComponent} with {@code markedForHarvest = true}.
 *   <li>On subsequent ticks the cached position is validated: if the block
 *       entity is still present and still claimed the sensor keeps returning
 *       {@code true} without scanning.
 *   <li>When the block is broken (HarvestableTreeComponent removed or block
 *       gone) the sensor returns {@code false} and releases internal state.
 * </ul>
 *
 * <p>Constructed by {@link BuilderSensorHarvestableTree}.
 */
public class SensorHarvestableTree extends SensorBase {

    private static final Query<ChunkStore> TREE_QUERY =
            Query.and(HarvestableTreeComponent.getComponentType());

    private final double range;
    private final PositionProvider positionProvider = new PositionProvider();

    /** Position of the tree this sensor has claimed, or {@code null} if none. */
    @Nullable
    private Vector3i claimedTreePos = null;

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

        World world = store.getExternalData().getWorld();
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            positionProvider.clear();
            return false;
        }
        Vector3d entityPos = transform.getPosition();

        // ------------------------------------------------------------------
        // Validate existing claim
        // ------------------------------------------------------------------
        if (claimedTreePos != null) {
            HarvestableTreeComponent tree = getTree(world, claimedTreePos);
            if (tree != null && tree.isMarkedForHarvest()) {
                // Still valid — keep providing the same position.
                positionProvider.setTarget(
                        claimedTreePos.x + 0.5, claimedTreePos.y, claimedTreePos.z + 0.5);
                return true;
            }
            // Tree was broken or claim was externally released.
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

        // Claim the tree.
        HarvestableTreeComponent tree = getTree(world, nearest);
        if (tree == null) {
            positionProvider.clear();
            return false;
        }
        tree.markForHarvest();
        claimedTreePos = nearest;

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
                if (tree == null || tree.isMarkedForHarvest()) continue;

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
     * Returns the {@link HarvestableTreeComponent} for the block entity at
     * {@code pos}, or {@code null} if no such block entity exists.
     */
    @Nullable
    private static HarvestableTreeComponent getTree(
            @Nonnull World world, @Nonnull Vector3i pos) {
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
        if (blockRef == null || !blockRef.isValid()) return null;
        return blockRef.getStore().getComponent(blockRef, HarvestableTreeComponent.getComponentType());
    }

    /**
     * Releases the current claim (sets {@code markedForHarvest = false}) if
     * we hold one, given the entity store (to reach the world).
     */
    private void releaseClaim(@Nonnull Store<EntityStore> store) {
        if (claimedTreePos == null) return;
        World world = store.getExternalData().getWorld();
        HarvestableTreeComponent tree = getTree(world, claimedTreePos);
        if (tree != null) {
            tree.setMarkedForHarvest(false);
        }
        claimedTreePos = null;
    }
}
