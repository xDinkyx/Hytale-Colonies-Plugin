package com.hytalecolonies.npc.actions.common;

import com.hytalecolonies.components.jobs.ItemRequirement;
import com.hytalecolonies.components.jobs.JobTaskComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.BlockEntityUtil;
import com.hytalecolonies.utils.WorkStationUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Retrieves required items from the delivery container into the colonist's
 * inventory.
 *
 * <p>
 * The full list is built by merging two sources:
 * <ol>
 * <li>{@link WorkStationComponent#defaultRequiredItems} — tools, each with
 * quantity 1.</li>
 * <li>{@link JobTaskComponent#requiredItems} — task-specific materials with
 * explicit quantities
 * (e.g. blocks to place, ingredients), if the component is present.</li>
 * </ol>
 *
 * <p>
 * Returns {@code true} when every requirement is satisfied in the colonist's
 * storage,
 * {@code false} when items are still missing (caller should stay blocked and
 * retry).
 */
public class ActionRetrieveJobItems extends ActionBase {

    public ActionRetrieveJobItems(@Nonnull BuilderActionRetrieveJobItems builder,
            @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
            @Nullable InfoProvider sensorInfo, double dt,
            @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        String npcId = DebugLog.npcId(ref, store);

        WorkStationComponent workStation = WorkStationUtil.getWorkStation(store, ref);
        if (workStation == null) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[RetrieveJobItems] [%s] No WorkStationComponent.", npcId);
            return true;
        }

        // Build the merged requirement list.
        List<ItemRequirement> requiredItems = new ArrayList<>();
        for (String pattern : workStation.defaultRequiredItems) {
            requiredItems.add(new ItemRequirement(pattern, 1));
        }
        JobTaskComponent task = store.getComponent(ref, JobTaskComponent.getComponentType());
        if (task != null) {
            for (ItemRequirement req : task.requiredItems) {
                requiredItems.add(req);
            }
        }

        if (requiredItems.isEmpty()) {
            return true; // Nothing required; proceed immediately.
        }

        LivingEntity colonist = (LivingEntity) EntityUtils.getEntity(ref, store);
        if (colonist == null) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[RetrieveJobItems] [%s] Could not resolve colonist entity.", npcId);
            return true;
        }

        ItemContainer colonistStorage = colonist.getInventory().getStorage();

        // Fast path: inventory already satisfied.
        if (hasItemsInInventory(colonistStorage, requiredItems)) {
            DebugLog.fine(DebugCategory.COLONIST_DELIVERY,
                    "[RetrieveJobItems] [%s] All required items already in inventory.", npcId);
            return true;
        }

        // Get items from workstation container.
        if (workStation.deliveryContainerPosition == null) {
            DebugLog.fine(DebugCategory.COLONIST_DELIVERY,
                    "[RetrieveJobItems] [%s] No container linked to workstation -- waiting for items.", npcId);
            return false;
        }

        World world = store.getExternalData().getWorld();
        Ref<ChunkStore> blockRef = BlockEntityUtil.getBlockEntityAt(world,
                workStation.deliveryContainerPosition);

        if (blockRef == null || !blockRef.isValid()) {
            DebugLog.severe(DebugCategory.COLONIST_DELIVERY,
                    "[RetrieveJobItems] [%s] Container block missing at %s -- setting workstation container to null",
                    npcId,
                    workStation.deliveryContainerPosition);
            workStation.deliveryContainerPosition = null;
            return false;
        }

        ItemContainerBlock containerBlock = blockRef.getStore().getComponent(
                blockRef, BlockModule.get().getItemContainerBlockComponentType());
        if (containerBlock == null) {
            DebugLog.severe(DebugCategory.COLONIST_DELIVERY,
                    "[RetrieveJobItems] [%s] Block at %s is not an item container -- setting workstation container to null.",
                    npcId,
                    workStation.deliveryContainerPosition);
            workStation.deliveryContainerPosition = null;
            return false;
        }

        ItemContainer itemContainer = containerBlock.getItemContainer();
        transferMissing(npcId, requiredItems, itemContainer, colonistStorage);

        boolean satisfied = hasItemsInInventory(colonistStorage, requiredItems);
        if (satisfied) {
            DebugLog.fine(DebugCategory.COLONIST_DELIVERY,
                    "[RetrieveJobItems] [%s] All required items retrieved.", npcId);
        } else {
            DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                    "[RetrieveJobItems] [%s] Container could not supply all required items -- waiting for restock.",
                    npcId);
        }
        return satisfied;
    }

    /**
     * Check if colonist already has all required items in inventory.
     */
    private static boolean hasItemsInInventory(@Nonnull ItemContainer colonistStorage,
            @Nonnull List<ItemRequirement> requirements) {
        for (ItemRequirement req : requirements) {
            if (InventoryHelper.countItems(colonistStorage, List.of(req.item)) < req.quantity)
                return false;
        }
        return true;
    }

    /**
     * Take missing items from container.
     */
    private static void transferMissing(@Nonnull String npcId, @Nonnull List<ItemRequirement> requirements,
            @Nonnull ItemContainer chest, @Nonnull ItemContainer colonistStorage) {
        for (ItemRequirement req : requirements) {
            int have = InventoryHelper.countItems(colonistStorage, List.of(req.item));
            int needed = req.quantity - have;

            if (needed <= 0)
                continue;
            
            int remaining = needed;
            short capacity = chest.getCapacity();
            for (short slot = 0; slot < capacity && remaining > 0; slot++) 
            {
                ItemStack chestStack = chest.getItemStack(slot);
                if (!InventoryHelper.matchesItem(req.item, chestStack))
                    continue;

                int take = Math.min(remaining, chestStack.getQuantity());
                MoveTransaction<?> tx = chest.moveItemStackFromSlot(slot, take, colonistStorage);
                if (!tx.succeeded())
                    break;

                remaining -= take;
                DebugLog.fine(DebugCategory.COLONIST_DELIVERY,
                        "[RetrieveJobItems] [%s] Retrieved %dx'%s' from container.",
                        npcId, take, chestStack.getItemId());
            }
            if (remaining > 0) {
                DebugLog.fine(DebugCategory.COLONIST_DELIVERY,
                        "[RetrieveJobItems] [%s] Container missing %d of '%s'.", npcId, remaining, req.item);
            }
        }
    }
}
