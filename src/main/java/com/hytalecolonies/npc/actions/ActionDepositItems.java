package com.hytalecolonies.npc.actions;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deposits all non-tool items from the colonist's inventory into the delivery
 * container identified by {@link JobComponent#deliveryContainerPosition} and
 * clears the delivery target so the next work cycle starts fresh.
 *
 * <h3>Behaviour</h3>
 * <ol>
 *   <li>Reads {@code job.deliveryContainerPosition}. If null or the block entity
 *       is invalid, clears the job target and returns silently.</li>
 *   <li>Iterates the colonist's storage slots; items whose {@code Item.getTool()}
 *       is non-null are kept, all others are moved to the chest. Remainder
 *       (chest full) stays in the colonist's slot.</li>
 *   <li>Clears {@code job.deliveryContainerPosition} and nulls
 *       {@link JobTargetComponent#targetPosition} so that subsequent
 *       {@code SeekNextMineBlock}/{@code SeekNearestTree} scans do not mistake
 *       the chest position for a valid work target.</li>
 * </ol>
 *
 * <p>This action does NOT transition state -- the JSON instruction block should
 * follow this with {@code { "Type": "SetEcsJobState", "JobState": "TravelingToHome" }}
 * so the decision is visible in the role file.
 *
 * <p>Replaces the {@code depositItems} logic previously in
 * {@link com.hytalecolonies.systems.jobs.ColonistDeliverySystem}.
 *
 * <p>Constructed by {@link BuilderActionDepositItems}.
 */
public class ActionDepositItems extends ActionBase {

    public ActionDepositItems(@Nonnull BuilderActionDepositItems builder,
                              @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           @Nullable InfoProvider sensorInfo, double dt,
                           @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        String npcId = DebugLog.npcId(ref, store);

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[DepositItems] [%s] No JobComponent.", npcId);
            return true;
        }

        Vector3i cp = job.deliveryContainerPosition;
        if (cp == null) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[DepositItems] [%s] No deliveryContainerPosition -- skipping deposit.", npcId);
            clearJobTarget(store, ref);
            return true;
        }

        World world = store.getExternalData().getWorld();
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, cp.x, cp.y, cp.z);
        if (blockRef == null || !blockRef.isValid()) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[DepositItems] [%s] Container block at %s is no longer present.", npcId, cp);
            job.deliveryContainerPosition = null;
            clearJobTarget(store, ref);
            return true;
        }

        ItemContainerBlock containerBlock = blockRef.getStore().getComponent(
                blockRef, BlockModule.get().getItemContainerBlockComponentType());
        if (containerBlock == null) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[DepositItems] [%s] Block at %s is no longer an item container.", npcId, cp);
            job.deliveryContainerPosition = null;
            clearJobTarget(store, ref);
            return true;
        }

        LivingEntity colonist = (LivingEntity) EntityUtils.getEntity(ref, store);
        if (colonist == null) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[DepositItems] [%s] Could not resolve colonist entity.", npcId);
            return true;
        }

        depositItems(npcId, colonist, containerBlock.getItemContainer());

        job.deliveryContainerPosition = null;
        clearJobTarget(store, ref);

        DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                "[DepositItems] [%s] Deposit complete at %s.", npcId, cp);

        return true;
    }

    /** Nulls {@link JobTargetComponent#targetPosition} so stale position does not block future work-finding. */
    private static void clearJobTarget(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        JobTargetComponent jt = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jt != null) {
            jt.setTargetPosition(null);
        }
    }

    /** Tools are kept on the colonist; everything else is deposited. */
    private static boolean shouldKeep(@Nonnull ItemStack stack) {
        return stack.getItem() != null && stack.getItem().getTool() != null;
    }

    private static void depositItems(@Nonnull String npcId,
                                     @Nonnull LivingEntity colonist,
                                     @Nonnull ItemContainer chestContainer) {
        ItemContainer colonistStorage = colonist.getInventory().getStorage();
        short capacity = colonistStorage.getCapacity();
        Map<String, Integer> deposited = new LinkedHashMap<>();

        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = colonistStorage.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) continue;
            if (shouldKeep(stack)) continue;
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
                "[DepositItems] [%s] Deposited: %s", npcId, summarise(deposited));
    }

    private static String summarise(@Nonnull Map<String, Integer> counts) {
        if (counts.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        counts.forEach((id, qty) -> {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(id).append('*').append(qty);
        });
        return sb.toString();
    }
}
