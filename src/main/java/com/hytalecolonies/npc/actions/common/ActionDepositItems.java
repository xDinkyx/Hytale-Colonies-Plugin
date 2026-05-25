package com.hytalecolonies.npc.actions.common;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.WorkStationUtil;
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
import com.hypixel.hytale.server.npc.util.InventoryHelper;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deposits all non-tool items into the delivery container cached on
 * {@link WorkStationComponent#deliveryContainerPosition}, then clears the
 * delivery target.
 *
 * <p>
 * Constructed by {@link BuilderActionDepositItems}.
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

        WorkStationComponent workStation = WorkStationUtil.getWorkStation(store, ref);
        if (workStation == null) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[DepositItems] [%s] No WorkStationComponent.", npcId);
            clearJobTarget(store, ref);
            return true;
        }

        Vector3i deliveryContainerPosition = workStation.deliveryContainerPosition;
        if (deliveryContainerPosition == null) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[DepositItems] [%s] No deliveryContainerPosition -- skipping deposit.", npcId);
            clearJobTarget(store, ref);
            return true;
        }

        World world = store.getExternalData().getWorld();
        Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, deliveryContainerPosition.x,
                deliveryContainerPosition.y, deliveryContainerPosition.z);
        if (blockRef == null || !blockRef.isValid()) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[DepositItems] [%s] Container block at %s is no longer present.", npcId,
                    deliveryContainerPosition);
            workStation.deliveryContainerPosition = null;
            clearJobTarget(store, ref);
            return true;
        }

        ItemContainerBlock containerBlock = blockRef.getStore().getComponent(
                blockRef, BlockModule.get().getItemContainerBlockComponentType());
        if (containerBlock == null) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[DepositItems] [%s] Block at %s is no longer an item container.", npcId,
                    deliveryContainerPosition);
            workStation.deliveryContainerPosition = null;
            clearJobTarget(store, ref);
            return true;
        }

        LivingEntity colonist = (LivingEntity) EntityUtils.getEntity(ref, store);
        if (colonist == null) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[DepositItems] [%s] Could not resolve colonist entity.", npcId);
            return true;
        }

        depositItems(npcId, colonist, containerBlock.getItemContainer(), workStation.defaultRequiredItems);

        workStation.deliveryContainerPosition = null;
        clearJobTarget(store, ref);

        DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                "[DepositItems] [%s] Deposit complete at %s.", npcId, deliveryContainerPosition);

        return true;
    }

    /**
     * Nulls {@link JobTargetComponent#targetPosition} so stale position does not
     * block future work-finding.
     */
    private static void clearJobTarget(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        JobTargetComponent jt = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jt != null) {
            jt.setTargetPosition(null);
        }
    }

    /** Tools are kept on the colonist; everything else is deposited. */
    private static boolean isTool(@Nonnull ItemStack stack) {
        return stack.getItem() != null && stack.getItem().getTool() != null;
    }

    /**
     * Keeps items matching any pattern in requiredItems; falls back to tool check
     * if list is empty.
     */
    private static boolean shouldKeep(@Nonnull ItemStack stack, @Nonnull String[] requiredItems) {
        if (stack.getItem() == null)
            return false;
        if (requiredItems.length == 0)
            return isTool(stack);
        return InventoryHelper.matchesItem(Arrays.asList(requiredItems), stack);
    }

    private static void depositItems(@Nonnull String npcId,
            @Nonnull LivingEntity colonist,
            @Nonnull ItemContainer chestContainer,
            @Nonnull String[] requiredItems) {
        ItemContainer colonistStorage = colonist.getInventory().getStorage();
        short capacity = colonistStorage.getCapacity();
        Map<String, Integer> deposited = new LinkedHashMap<>();

        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = colonistStorage.getItemStack(slot);
            if (ItemStack.isEmpty(stack))
                continue;
            if (shouldKeep(stack, requiredItems))
                continue;
            colonistStorage.removeItemStackFromSlot(slot);
            ItemStackTransaction tx = chestContainer.addItemStack(stack);
            ItemStack remainder = tx.getRemainder();
            int depositedQty = stack.getQuantity() - (remainder != null ? remainder.getQuantity() : 0);
            if (depositedQty > 0)
                deposited.merge(stack.getItemId(), depositedQty, Integer::sum);
            if (remainder != null && !remainder.isEmpty()) {
                colonistStorage.setItemStackForSlot(slot, remainder);
            }
        }

        DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                "[DepositItems] [%s] Deposited: %s", npcId, summarise(deposited));
    }

    private static String summarise(@Nonnull Map<String, Integer> counts) {
        if (counts.isEmpty())
            return "-";
        StringBuilder sb = new StringBuilder();
        counts.forEach((id, qty) -> {
            if (!sb.isEmpty())
                sb.append(", ");
            sb.append(id).append('*').append(qty);
        });
        return sb.toString();
    }
}
