package com.hytalecolonies.listeners;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interface_.ChatMessage;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.ui.ConstructorPrefabPage;


/**
 * Intercepts "/prefab list" chat commands while the player holds Tool_Colony_Constructor_PlacePrefab
 * and redirects them to {@link ConstructorPrefabPage} instead of the native PrefabPage.
 */
public class ConstructorPrefabPageFilter implements PlayerPacketFilter
{

    private static final int CHAT_MESSAGE_PACKET_ID = ChatMessage.PACKET_ID;
    private static final String COLONY_CONSTRUCTOR_ITEM_ID = "Tool_Colony_Constructor_PlacePrefab";

    @Override public boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet)
    {
        if (packet.getId() != CHAT_MESSAGE_PACKET_ID)
            return false;

        ChatMessage chatPacket = (ChatMessage)packet;
        if (chatPacket.message == null)
            return false;

        // The client sends "/prefab list" (possibly with args) when 'e' is pressed with a paste tool.
        if (!chatPacket.message.startsWith("/prefab list"))
            return false;

        // IO thread: block the packet and redirect on world thread.
        UUID worldUuid = playerRef.getWorldUuid();
        if (worldUuid == null)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPageFilter] No world UUID for '%s'.", playerRef.getUsername());
            return false;
        }

        World world = Universe.get().getWorld(worldUuid);
        if (world == null)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPageFilter] World %s not found.", worldUuid);
            return false;
        }

        DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPageFilter] Intercepted /prefab list from '%s'.", playerRef.getUsername());

        final String originalCmd = chatPacket.message.substring(1); // strip leading '/'

        world.execute(() -> {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid())
            {
                DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPageFilter] Entity ref invalid on world thread.");
                return;
            }

            Store<EntityStore> store = entityRef.getStore();
            Player player = store.getComponent(entityRef, Player.getComponentType());
            PlayerRef playerRefComponent = store.getComponent(entityRef, PlayerRef.getComponentType());
            if (player == null || playerRefComponent == null)
            {
                DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPageFilter] Player/PlayerRef component null on world thread.");
                return;
            }

            ItemStack activeItem = player.getInventory().getItemInHand();
            boolean holdsConstructorTool = activeItem != null && COLONY_CONSTRUCTOR_ITEM_ID.equals(activeItem.getItemId());

            if (holdsConstructorTool)
            {
                DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorPageFilter] Opening ConstructorPrefabPage for '%s'.", playerRef.getUsername());
                BuilderToolsPlugin.BuilderState builderState = BuilderToolsPlugin.getState(player, playerRefComponent);
                player.getPageManager().openCustomPage(entityRef, store, new ConstructorPrefabPage(playerRefComponent, builderState));
            }
            else
            {
                DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB,
                              "[ConstructorPageFilter] Player '%s' holds '%s', not constructor tool -- executing native command.",
                              playerRef.getUsername(),
                              activeItem != null ? activeItem.getItemId() : "null");
                CommandManager.get().handleCommand(player, originalCmd);
            }
        });

        return true; // Block original packet; world thread handles it.
    }
}
