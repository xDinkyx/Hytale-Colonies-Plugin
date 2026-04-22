package com.hytalecolonies.listeners;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolPasteClipboard;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.ConstructionOrderQueue;
import com.hytalecolonies.ConstructionOrderStore;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;

/** Intercepts paste packets for the constructor tool and creates a {@link ConstructionOrderStore.Entry} for the paste site. */
public class ConstructorBuildOrderFilter implements PlayerPacketFilter
{

    private static final int BUILD_TOOL_PASTE_PACKET_ID = 407;

    /** Prefab path armed by {@link com.hytalecolonies.ui.ConstructorPrefabPage}, keyed by player UUID. */
    public static final ConcurrentHashMap<UUID, String> pendingPrefabPath = new ConcurrentHashMap<>();

    /** Rotated clipboard at paste time, keyed by build-site for colonist use. */
    public static final ConcurrentHashMap<Vector3i, BlockSelection> pendingSelections = new ConcurrentHashMap<>();

    /** Players locked from pasting until they select a new prefab via {@link com.hytalecolonies.ui.ConstructorPrefabPage}. */
    public static final Set<UUID> pasteLocked = ConcurrentHashMap.newKeySet();

    public static void clearPasteLock(UUID uuid)
    {
        pasteLocked.remove(uuid);
    }

    private static final Message MSG_ORDER_QUEUED = Message.translation("items.Tool_Colony_Constructor_PlacePrefab.orderQueued");

    @Override public boolean test(@Nonnull PlayerRef playerRef, @Nonnull Packet packet)
    {
        if (packet.getId() != BUILD_TOOL_PASTE_PACKET_ID)
            return false;

        UUID uuid = playerRef.getUuid();

        if (pasteLocked.contains(uuid))
        {
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                          "[ConstructorFilter] Paste from '%s' blocked -- order already active (select a new prefab to place another).",
                          playerRef.getUsername());
            return true;
        }

        // Remove atomically on the IO thread so a concurrent paste sees null and hits the lock check above.
        String prefabAbsPath = pendingPrefabPath.remove(uuid);
        if (prefabAbsPath == null)
        {
            DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorFilter] Paste from '%s' -- no pending prefab, allowing.", playerRef.getUsername());
            return false;
        }

        pasteLocked.add(uuid);

        BuilderToolPasteClipboard pastePacket = (BuilderToolPasteClipboard)packet;
        int pasteX = pastePacket.x;
        int pasteY = pastePacket.y;
        int pasteZ = pastePacket.z;

        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                      "[ConstructorFilter] Intercepted paste from '%s' at %d,%d,%d -- scheduling order creation.",
                      playerRef.getUsername(),
                      pasteX,
                      pasteY,
                      pasteZ);

        UUID worldUuid = playerRef.getWorldUuid();
        if (worldUuid == null)
        {
            pasteLocked.remove(uuid);
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorFilter] Player '%s' has no world UUID.", playerRef.getUsername());
            return true;
        }
        World world = Universe.get().getWorld(worldUuid);
        if (world == null)
        {
            pasteLocked.remove(uuid);
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorFilter] World %s not found.", worldUuid);
            return true;
        }

        final Vector3i buildSite = new Vector3i(pasteX, pasteY, pasteZ);

        world.execute(() -> {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid())
            {
                pasteLocked.remove(uuid);
                DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                                 "[ConstructorFilter] Entity ref null or invalid for '%s' on world thread.",
                                 playerRef.getUsername());
                return;
            }

            Player player = entityRef.getStore().getComponent(entityRef, Player.getComponentType());
            if (player == null)
            {
                pasteLocked.remove(uuid);
                DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorFilter] Player component null on world thread for '%s'.", playerRef.getUsername());
                return;
            }

            BuilderToolsPlugin.addToQueue(player, playerRef, (r, builderState, componentAccessor) -> {
                BlockSelection selection = builderState.getSelection();
                if (selection != null)
                {
                    pendingSelections.put(buildSite, selection.cloneSelection());
                    DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                                  "[ConstructorFilter] Captured rotated selection (%d blocks) for site %s.",
                                  selection.getBlockCount(),
                                  buildSite);
                }
                else
                {
                    DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                                     "[ConstructorFilter] BuilderState has no selection for '%s' -- order will load from disk.",
                                     playerRef.getUsername());
                }

                ConstructionOrderStore.Entry entry = new ConstructionOrderStore.Entry(UUID.randomUUID(), prefabAbsPath, buildSite);
                ConstructionOrderStore.get().add(entry);
                ConstructionOrderQueue.get().enqueue(entry.id);
                DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorFilter] Queued order %s for site %s.", entry.id, buildSite);
                // pasteLocked remains set until a new prefab is selected in ConstructorPrefabPage.
            });
        });

        playerRef.sendMessage(MSG_ORDER_QUEUED);
        return true;
    }
}
