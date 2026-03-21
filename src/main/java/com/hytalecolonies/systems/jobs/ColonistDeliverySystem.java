package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.WorkstationContainerUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerBlockState;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Generic delivery system for colonists. Handles the {@link JobState#DeliveringItems}
 * state for <em>any</em> job type: navigates the colonist to the nearest chest
 * within {@link #DELIVERY_RADIUS} blocks of their workstation, deposits their
 * storage inventory, then transitions them to {@link JobState#TravelingHome}.
 *
 * <p>Job-specific systems (e.g. {@link WoodsmanJobSystem}) are responsible for
 * entering the {@code DeliveringItems} state after collecting drops. This system
 * takes over from there and is completely job-agnostic.
 *
 * <p>If no container is found within range, a warning is logged and the colonist
 * skips delivery and heads straight home.
 */
public class ColonistDeliverySystem extends DelayedEntitySystem<EntityStore> {

    /** Block radius around the workstation to search for a linked container. */
    public static final int DELIVERY_RADIUS = 3;

    /** 3D distance (blocks) at which the colonist is considered "at" the chest. */
    private static final float DELIVERY_ARRIVAL_DIST = 2.5f;

    private final Query<EntityStore> query = Query.and(
            ColonistComponent.getComponentType(),
            JobComponent.getComponentType()
    );

    public ColonistDeliverySystem() {
        super(2.0f); // Same cadence as WoodsmanJobSystem.
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        JobComponent job = archetypeChunk.getComponent(index, JobComponent.getComponentType());
        assert job != null;

        if (job.getCurrentTask() != JobState.DeliveringItems) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        handleDeliveringItems(ref, job, store, commandBuffer);
    }

    // ===== State handler =====

    private void handleDeliveringItems(@Nonnull Ref<EntityStore> ref,
                                       @Nonnull JobComponent job,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Vector3i workStationPos = job.getWorkStationBlockPosition();
        if (workStationPos == null) {
            job.setCurrentTask(JobState.TravelingHome);
            return;
        }

        World world = store.getExternalData().getWorld();

        // Discover the linked container on the first tick of this delivery run.
        if (job.deliveryContainerPosition == null) {
            Vector3i containerPos = WorkstationContainerUtil.findNearbyContainer(world, workStationPos, DELIVERY_RADIUS);
            if (containerPos == null) {
                DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                        "[ColonistDelivery] No chest within %d blocks of workstation %s — skipping delivery.",
                        DELIVERY_RADIUS, workStationPos);
                navigateToWorkstation(ref, commandBuffer, workStationPos);
                job.setCurrentTask(JobState.TravelingHome);
                return;
            }
            job.deliveryContainerPosition = containerPos;
            // Dispatch navigation to the chest.
            commandBuffer.addComponent(ref, MoveToTargetComponent.getComponentType(),
                    new MoveToTargetComponent(new Vector3d(containerPos.x + 0.5, containerPos.y, containerPos.z + 0.5)));
            DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                    "[ColonistDelivery] Heading to chest at %s.", containerPos);
            return;
        }

        // Check arrival at the chest.
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        Vector3i cp = job.deliveryContainerPosition;
        Vector3d pos = transform.getPosition();
        double dx = pos.getX() - (cp.x + 0.5);
        double dy = pos.getY() - cp.y;
        double dz = pos.getZ() - (cp.z + 0.5);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist > DELIVERY_ARRIVAL_DIST) {
            DebugLog.fine(DebugCategory.COLONIST_DELIVERY,
                    "[ColonistDelivery] Heading to chest at %s — dist=%.2f.", cp, dist);
            return;
        }

        // Arrived — attempt deposit.
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, cp.x, cp.y, cp.z);
        if (blockRef == null || !blockRef.isValid()) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[ColonistDelivery] Chest at %s is no longer present — skipping deposit.", cp);
            job.deliveryContainerPosition = null;
            navigateToWorkstation(ref, commandBuffer, workStationPos);
            job.setCurrentTask(JobState.TravelingHome);
            return;
        }

        @SuppressWarnings("removal") // BlockStateModule is deprecated in favour of BlockModule (available ≥ 2026.02.26).
        ItemContainerBlockState containerState = blockRef.getStore().getComponent(
                blockRef, BlockStateModule.get().getComponentType(ItemContainerState.class));
        if (containerState == null) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[ColonistDelivery] Block at %s is no longer a container — skipping deposit.", cp);
            job.deliveryContainerPosition = null;
            navigateToWorkstation(ref, commandBuffer, workStationPos);
            job.setCurrentTask(JobState.TravelingHome);
            return;
        }

        depositItems(ref, store, containerState.getItemContainer(), cp);
        job.deliveryContainerPosition = null;
        navigateToWorkstation(ref, commandBuffer, workStationPos);
        job.setCurrentTask(JobState.TravelingHome);
    }

    /**
     * Moves all items from the colonist's storage (non-hotbar) into the chest.
     * Any items that don't fit are returned to the colonist's storage.
     */
    private void depositItems(@Nonnull Ref<EntityStore> ref,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull ItemContainer chestContainer,
                              @Nonnull Vector3i chestPos) {
        LivingEntity colonist = (LivingEntity) EntityUtils.getEntity(ref, store);
        if (colonist == null) return;

        ItemContainer colonistStorage = colonist.getInventory().getStorage();

        // Remove all items from storage — we'll put back anything that doesn't fit.
        List<ItemStack> toDeposit = colonistStorage.dropAllItemStacks();
        int depositedStacks = 0;

        for (ItemStack stack : toDeposit) {
            if (ItemStack.isEmpty(stack)) continue;
            ItemStackTransaction tx = chestContainer.addItemStack(stack);
            ItemStack remainder = tx.getRemainder();
            if (remainder != null && !remainder.isEmpty()) {
                // Chest is full (or partially full) — return what didn't fit.
                colonistStorage.addItemStack(remainder);
                DebugLog.fine(DebugCategory.COLONIST_DELIVERY,
                        "[ColonistDelivery] Chest at %s full — %dx %s returned to colonist.",
                        chestPos, remainder.getQuantity(), remainder.getItemId());
            } else {
                depositedStacks++;
            }
        }

        DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                "[ColonistDelivery] Deposited %d stack(s) into chest at %s.", depositedStacks, chestPos);
    }

    private static void navigateToWorkstation(@Nonnull Ref<EntityStore> ref,
                                              @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                              @Nonnull Vector3i workStationPos) {
        commandBuffer.addComponent(ref, MoveToTargetComponent.getComponentType(),
                new MoveToTargetComponent(new Vector3d(workStationPos.x + 0.5, workStationPos.y, workStationPos.z + 0.5)));
    }
}
