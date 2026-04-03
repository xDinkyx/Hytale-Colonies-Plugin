package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.WorkstationContainerUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Navigates colonists in {@link JobState#DeliveringItems} to a nearby chest and deposits their inventory. */
public class ColonistDeliverySystem extends DelayedEntitySystem<EntityStore> {

    public static final int DELIVERY_RADIUS = 3;
    private static final float DELIVERY_ARRIVAL_XZ = 3.0f;

    private final Query<EntityStore> query = Query.and(
            ColonistComponent.getComponentType(),
            JobComponent.getComponentType()
    );

    public ColonistDeliverySystem() {
        super(2.0f);
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
            commandBuffer.addComponent(ref, MoveToTargetComponent.getComponentType(),
                    new MoveToTargetComponent(new Vector3d(containerPos.x + 0.5, containerPos.y, containerPos.z + 0.5)));
            DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                    "[ColonistDelivery] Heading to chest at %s.", containerPos);
            return;
        }

        // XZ-only arrival check — chest may be at a different Y than the colonist.
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        Vector3i cp = job.deliveryContainerPosition;
        Vector3d pos = transform.getPosition();
        double dx = pos.getX() - (cp.x + 0.5);
        double dz = pos.getZ() - (cp.z + 0.5);
        double xzDist = Math.sqrt(dx * dx + dz * dz);

        if (xzDist > DELIVERY_ARRIVAL_XZ) {
            DebugLog.fine(DebugCategory.COLONIST_DELIVERY,
                    "[ColonistDelivery] Heading to chest at %s — xzDist=%.2f.", cp, xzDist);
            return;
        }

        // Arrived — deposit.
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, cp.x, cp.y, cp.z);
        if (blockRef == null || !blockRef.isValid()) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[ColonistDelivery] Chest at %s is no longer present — skipping deposit.", cp);
            job.deliveryContainerPosition = null;
            navigateToWorkstation(ref, commandBuffer, workStationPos);
            job.setCurrentTask(JobState.TravelingHome);
            return;
        }

        ItemContainerBlock containerBlock = blockRef.getStore().getComponent(
                blockRef, BlockModule.get().getItemContainerBlockComponentType());
        if (containerBlock == null) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[ColonistDelivery] Block at %s is no longer a container — skipping deposit.", cp);
            job.deliveryContainerPosition = null;
            navigateToWorkstation(ref, commandBuffer, workStationPos);
            job.setCurrentTask(JobState.TravelingHome);
            return;
        }

        depositItems(ref, store, containerBlock.getItemContainer(), cp);
        job.deliveryContainerPosition = null;
        navigateToWorkstation(ref, commandBuffer, workStationPos);
        job.setCurrentTask(JobState.TravelingHome);
    }

    /** Tools stay on the colonist; everything else gets deposited. */
    private static boolean shouldKeep(@Nonnull ItemStack stack) {
        return stack.getItem() != null && stack.getItem().getTool() != null;
    }

    private void depositItems(@Nonnull Ref<EntityStore> ref,
                              @Nonnull Store<EntityStore> store,
                              @Nonnull ItemContainer chestContainer,
                              @Nonnull Vector3i chestPos) {
        LivingEntity colonist = (LivingEntity) EntityUtils.getEntity(ref, store);
        if (colonist == null) return;

        ItemContainer colonistStorage = colonist.getInventory().getStorage();
        short capacity = colonistStorage.getCapacity();
        Map<String, Integer> deposited = new LinkedHashMap<>();

        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = colonistStorage.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) continue;
            if (shouldKeep(stack)) continue;
            // Remove from colonist first, then attempt deposit.
            colonistStorage.removeItemStackFromSlot(slot);
            ItemStackTransaction tx = chestContainer.addItemStack(stack);
            ItemStack remainder = tx.getRemainder();
            int depositedQty = stack.getQuantity() - (remainder != null ? remainder.getQuantity() : 0);
            if (depositedQty > 0) deposited.merge(stack.getItemId(), depositedQty, Integer::sum);
            if (remainder != null && !remainder.isEmpty()) {
                colonistStorage.setItemStackForSlot(slot, remainder);
            }
        }

        DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                "[ColonistDelivery] -> %s", summarise(deposited));
    }

    /** Formats deposited items as {@code "id*qty, id*qty"}, or {@code "-"} if empty. */
    private static String summarise(@Nonnull Map<String, Integer> counts) {
        if (counts.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        counts.forEach((id, qty) -> {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(id).append('*').append(qty);
        });
        return sb.toString();
    }

    private static void navigateToWorkstation(@Nonnull Ref<EntityStore> ref,
                                              @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                              @Nonnull Vector3i workStationPos) {
        commandBuffer.addComponent(ref, MoveToTargetComponent.getComponentType(),
                new MoveToTargetComponent(new Vector3d(workStationPos.x + 0.5, workStationPos.y, workStationPos.z + 0.5)));
    }

    // ===== Container removal =====

    /**
     * Clears the cached delivery target when a container block entity is removed.
     * Uses {@link RefSystem} because block breaks remove the entire entity, not individual components.
     */
    public static class OnContainerRemoved extends RefSystem<ChunkStore> {

        private final ComponentType<ChunkStore, ItemContainerBlock> itemContainerType = ItemContainerBlock.getComponentType();
        private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> blockStateInfoType = BlockModule.BlockStateInfo.getComponentType();
        private final Query<ChunkStore> query = Query.and(itemContainerType, blockStateInfoType);

        private final Query<EntityStore> colonistQuery = Query.and(
                ColonistComponent.getComponentType(),
                JobComponent.getComponentType()
        );

        @Override
        public Query<ChunkStore> getQuery() {
            return query;
        }

        @Override
        public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason,
                                  @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {}

        @Override
        public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason reason,
                                   @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
            DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                    "[ColonistDelivery:ContainerRemoved] Fired — reason=%s.", reason);

            if (reason == RemoveReason.UNLOAD) return;

            BlockModule.BlockStateInfo info = commandBuffer.getComponent(ref, blockStateInfoType);
            if (info == null) {
                DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                        "[ColonistDelivery:ContainerRemoved] No BlockStateInfo on removed container entity — cannot resolve position.");
                return;
            }

            Ref<ChunkStore> chunkRef = info.getChunkRef();
            if (!chunkRef.isValid()) {
                DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                        "[ColonistDelivery:ContainerRemoved] ChunkRef is invalid — cannot resolve position.");
                return;
            }

            BlockChunk blockChunk = commandBuffer.getComponent(chunkRef, BlockChunk.getComponentType());
            if (blockChunk == null) {
                DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                        "[ColonistDelivery:ContainerRemoved] No BlockChunk on chunkRef — cannot resolve position.");
                return;
            }

            int idx = info.getIndex();
            int localX = ChunkUtil.xFromBlockInColumn(idx);
            int localY = ChunkUtil.yFromBlockInColumn(idx);
            int localZ = ChunkUtil.zFromBlockInColumn(idx);
            Vector3i pos = new Vector3i(
                    ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localX),
                    localY,
                    ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localZ)
            );

            DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                    "[ColonistDelivery:ContainerRemoved] Container removed at %s — scanning colonists.", pos);

            Store<EntityStore> entityStore = store.getExternalData().getWorld().getEntityStore().getStore();
            entityStore.forEachChunk(colonistQuery, (chunk, cb) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    JobComponent job = chunk.getComponent(i, JobComponent.getComponentType());
                    if (job == null) continue;
                    DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                            "[ColonistDelivery:ContainerRemoved] Colonist state=%s deliveryTarget=%s.",
                            job.getCurrentTask(), job.deliveryContainerPosition);
                    if (job.getCurrentTask() != JobState.DeliveringItems) continue;
                    if (!pos.equals(job.deliveryContainerPosition)) continue;

                    Ref<EntityStore> colonistRef = chunk.getReferenceTo(i);
                    job.deliveryContainerPosition = null;
                    Vector3i wsPos = job.getWorkStationBlockPosition();
                    if (wsPos != null) {
                        cb.addComponent(colonistRef, MoveToTargetComponent.getComponentType(),
                                new MoveToTargetComponent(new Vector3d(wsPos.x + 0.5, wsPos.y, wsPos.z + 0.5)));
                    }
                    job.setCurrentTask(JobState.TravelingHome);
                    DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                            "[ColonistDelivery:ContainerRemoved] Redirected colonist home.");
                }
            });
        }
    }
}
