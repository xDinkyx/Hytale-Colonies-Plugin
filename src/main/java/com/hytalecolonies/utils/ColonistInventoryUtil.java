package com.hytalecolonies.utils;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;

/**
 * Utility for opening colonist inventory (hotbar + storage).
 */
public final class ColonistInventoryUtil
{
    private ColonistInventoryUtil() {}

    /**
     * Opens the colonist's combined hotbar + storage as a single Bench container window for the given player. Returns {@code false} if the colonist has no
     * inventory containers or the page could not be opened.
     *
     * @param colonistRef ref to the colonist NPC entity
     * @param playerRef   ref to the player entity
     * @param player      the {@link Player} component of the player
     * @param store       the entity store
     */
    public static boolean
    openForPlayer(@Nonnull Ref<EntityStore> colonistRef, @Nonnull Ref<EntityStore> playerRef, @Nonnull Player player, @Nonnull Store<EntityStore> store)
    {
        InventoryComponent.Hotbar  hotbarComp  = store.getComponent(colonistRef, InventoryComponent.Hotbar.getComponentType());
        InventoryComponent.Storage storageComp = store.getComponent(colonistRef, InventoryComponent.Storage.getComponentType());

        if (hotbarComp == null && storageComp == null)
        {
            DebugLog.warning(DebugCategory.COLONIST_LIFECYCLE, "[ColonistInventory] [%s] No inventory containers.", DebugLog.npcId(colonistRef, store));
            return false;
        }

        ItemContainer container;
        if (hotbarComp != null && storageComp != null)
        {
            // Combine hotbar (tools) first, then storage. The Bench page renders one
            // container panel — two separate ContainerWindows only show the first.
            container = InventoryComponent.getCombined(store,
                                                       colonistRef,
                                                       InventoryComponent.Hotbar.getComponentType(),
                                                       InventoryComponent.Storage.getComponentType());
        }
        else if (hotbarComp != null)
        {
            container = hotbarComp.getInventory();
        }
        else
        {
            container = storageComp.getInventory();
        }

        DebugLog.info(DebugCategory.COLONIST_LIFECYCLE,
                      "[ColonistInventory] [%s] opening window cap=%d",
                      DebugLog.npcId(colonistRef, store),
                      container.getCapacity());

        return player.getPageManager().setPageWithWindows(playerRef, store, Page.Bench, true, new ContainerWindow(container));
    }
}
