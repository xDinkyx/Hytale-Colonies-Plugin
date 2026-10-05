package com.hytalecolonies;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Per-player, in-memory state for the constructor tool's pick -> preview -> confirm placement flow: which prefab is pending, where the preview ghost is
 * currently shown, and at what rotation. Entirely owned by this plugin -- no dependency on BuilderTools clipboard/paste state.
 */
public final class ConstructionPlacementSession
{
    private static final Map<UUID, ConstructionPlacementSession> SESSIONS = new ConcurrentHashMap<>();

    /** Absolute zipfs path of the pending prefab (same format as {@link ConstructionOrderStore.Entry#prefabId}), or null if none selected yet. */
    @Nullable public String prefabId;

    /** World position of the last-placed preview ghost, or null if the ghost has not been positioned yet. */
    @Nullable public Vector3i position;

    /** Y-axis rotation (degrees, snapped to 0/90/180/270) to apply when the ghost/order is finally resolved. */
    public int rotationDegrees;

    /** The spawned {@code PersistentPrefabPreview} ghost entity, or null if none is currently shown. */
    @Nullable public Ref<EntityStore> ghostRef;

    private ConstructionPlacementSession() {}

    /** Returns the session for {@code playerUuid}, creating an empty one on first use. */
    @Nonnull
    public static ConstructionPlacementSession get(@Nonnull UUID playerUuid)
    {
        return SESSIONS.computeIfAbsent(playerUuid, id -> new ConstructionPlacementSession());
    }

    /** Drops the session's state. Callers are responsible for despawning {@link #ghostRef} first if it is still valid. */
    public static void clear(@Nonnull UUID playerUuid)
    {
        SESSIONS.remove(playerUuid);
    }
}
