package com.hytalecolonies.listeners;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.hytalecolonies.ConstructionOrderQueue;
import com.hytalecolonies.components.jobs.ConstructionOrderComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolPasteClipboard;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Intercepts {@code BuilderToolPasteClipboard} (ID 407) for players holding the Colony
 * Constructor Assign tool. Cancels the paste, places a {@link ConstructionOrderComponent}
 * on the block entity at the paste position, and enqueues it for workstation assignment.
 *
 * <p>Pair with {@code /hc construct <name>} which loads the prefab to the clipboard first.
 */
public class ConstructorBuildOrderFilter implements PlayerPacketFilter {

    private static final int BUILD_TOOL_PASTE_PACKET_ID = 407;
    private static final String COLONY_CONSTRUCTOR_ITEM_ID = "Tool_Colony_Constructor_PlacePrefab";

    // Player UUID -> prefab name set by /hc construct <name>
    private static final ConcurrentHashMap<UUID, String> playerPrefabSelections = new ConcurrentHashMap<>();

    private static final Message MSG_NO_PREFAB_SELECTED = Message.translation(
            "items.Tool_Colony_Constructor_PlacePrefab.error.noPrefabSelected");
    private static final Message MSG_ORDER_QUEUED = Message.translation(
            "items.Tool_Colony_Constructor_PlacePrefab.orderQueued");

    /** Called by {@code /hc construct <name>} after loading the prefab to clipboard. */
    public static void setPrefabForPlayer(@Nonnull UUID playerUuid, @Nonnull String prefabName) {
        playerPrefabSelections.put(playerUuid, prefabName);
    }

    /** Called on player disconnect to clean up the side-map. */
    public static void clearPrefabForPlayer(@Nonnull UUID playerUuid) {
        playerPrefabSelections.remove(playerUuid);
    }

    @Override
    public boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        if (packet.getId() != BUILD_TOOL_PASTE_PACKET_ID) return false;

        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) return false;

        Store<EntityStore> store = entityRef.getStore();
        Player player = store.getComponent(entityRef, Player.getComponentType());
        if (player == null) return false;

        ItemStack itemInHand = player.getInventory().getItemInHand();
        if (ItemStack.isEmpty(itemInHand)) return false;
        if (!COLONY_CONSTRUCTOR_ITEM_ID.equals(itemInHand.getItemId())) return false;

        BuilderToolPasteClipboard pastePacket = (BuilderToolPasteClipboard) packet;
        int pasteX = pastePacket.x;
        int pasteY = pastePacket.y;
        int pasteZ = pastePacket.z;

        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                "[ConstructorBuildOrderFilter] Intercepted paste from '%s' at %d,%d,%d.",
                playerRef.getUsername(), pasteX, pasteY, pasteZ);

        UUID playerUuid = playerRef.getUuid();
        String prefabName = playerPrefabSelections.get(playerUuid);
        if (prefabName == null || prefabName.isEmpty()) {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                    "[ConstructorBuildOrderFilter] Player '%s' has no prefab selected.", playerRef.getUsername());
            playerRef.sendMessage(MSG_NO_PREFAB_SELECTED);
            return true;
        }

        UUID worldUuid = playerRef.getWorldUuid();
        if (worldUuid == null) {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                    "[ConstructorBuildOrderFilter] Player '%s' has no world UUID.", playerRef.getUsername());
            return true;
        }
        World world = Universe.get().getWorld(worldUuid);
        if (world == null) {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                    "[ConstructorBuildOrderFilter] World %s not found.", worldUuid);
            return true;
        }

        final String finalPrefabName = prefabName;
        final Vector3i buildSite = new Vector3i(pasteX, pasteY, pasteZ);

        world.execute(() -> {
            Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
            Ref<ChunkStore> orderRef = BlockModule.getBlockEntity(world, buildSite.x, buildSite.y, buildSite.z);
            if (orderRef == null || !orderRef.isValid()) {
                DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                        "[ConstructorBuildOrderFilter] Could not get block entity at build site %s.", buildSite);
                return;
            }
            chunkStore.addComponent(orderRef, ConstructionOrderComponent.getComponentType(),
                    new ConstructionOrderComponent(finalPrefabName, buildSite));
            ConstructionOrderQueue.get().enqueue(buildSite);
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                    "[ConstructorBuildOrderFilter] Order '%s' placed at %s and enqueued (queue size: %d).",
                    finalPrefabName, buildSite, ConstructionOrderQueue.get().size());
        });

        playerRef.sendMessage(MSG_ORDER_QUEUED);
        return true;
    }
}
