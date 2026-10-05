package com.hytalecolonies.utils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentPrefabPreview;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.ConstructionOrderStore;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;

/**
 * Manages the player-facing hologram preview ({@link PersistentPrefabPreview}) for a construction order. Toggled from the constructor workstation UI, cycling
 * Off -> Progress -> Full. Progress reveals only the layers already completed plus the layer currently being worked on; Full shows the entire prefab.
 *
 * <p>
 * The hologram is oriented via its entity transform rotation, using {@link ConstructionOrderStore.Entry#rotationDegrees} directly -- the same value the
 * player chose at placement time (see {@code ConstructionPlacementUtil}), so there is no need to re-derive or guess the rotation here.
 */
public final class ConstructionPreviewUtil
{
    private ConstructionPreviewUtil() {}

    public enum Mode
    {
        OFF,
        PROGRESS,
        FULL
    }

    @Nonnull
    public static Mode getMode(@Nonnull ConstructionOrderStore.Entry order)
    {
        if (order.previewRef == null || !order.previewRef.isValid())
            return Mode.OFF;
        return order.previewFullMode ? Mode.FULL : Mode.PROGRESS;
    }

    /** Cycles Off -> Progress -> Full -> Off and applies the new mode. Returns the mode now active. */
    @Nonnull
    public static Mode cycle(@Nonnull ConstructionOrderStore.Entry order,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull World               world,
                             @Nullable BlockSelection     prefab)
    {
        Mode current = getMode(order);
        Mode next    = switch (current)
        {
            case OFF -> Mode.PROGRESS;
            case PROGRESS -> Mode.FULL;
            case FULL -> Mode.OFF;
        };
        apply(order, next, store, world, prefab);
        return next;
    }

    /** Refreshes the visible layer count of an already-active Progress preview. No-op if the preview is off or in Full mode. */
    public static void refreshProgress(@Nonnull ConstructionOrderStore.Entry order, @Nonnull World world, @Nullable BlockSelection prefab)
    {
        if (order.previewRef == null || !order.previewRef.isValid() || order.previewFullMode || prefab == null)
            return;
        int layers = ConstructorUtil.computeVisibleLayerCount(order, world, prefab, false);
        PersistentPrefabPreview.updateLayers(order.previewRef.getStore(), order.previewRef, layers);
    }

    /** Removes an active hologram, if any. Safe to call repeatedly or when no preview is active. */
    public static void remove(@Nonnull ConstructionOrderStore.Entry order)
    {
        if (order.previewRef != null && order.previewRef.isValid())
            PersistentPrefabPreview.remove(order.previewRef.getStore(), order.previewRef);
        order.previewRef      = null;
        order.previewFullMode = false;
    }

    private static void apply(@Nonnull ConstructionOrderStore.Entry order,
                              @Nonnull Mode                mode,
                              @Nonnull Store<EntityStore>  store,
                              @Nonnull World                world,
                              @Nullable BlockSelection     prefab)
    {
        if (mode == Mode.OFF)
        {
            remove(order);
            return;
        }

        if (order.buildOrigin == null || prefab == null)
            return;

        String prefabKey = ConstructorUtil.derivePrefabKey(order.prefabId);
        if (prefabKey == null)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                             "[ConstructionPreview] Could not derive a browsable prefab key from '%s' -- preview unavailable.",
                             order.prefabId);
            return;
        }

        order.previewFullMode = (mode == Mode.FULL);
        int layers = ConstructorUtil.computeVisibleLayerCount(order, world, prefab, mode == Mode.FULL);

        if (order.previewRef != null && order.previewRef.isValid())
        {
            PersistentPrefabPreview.updateLayers(store, order.previewRef, layers);
            return;
        }

        Vector3d position = new Vector3d(order.buildOrigin.x - prefab.getAnchorX(),
                                          order.buildOrigin.y - prefab.getAnchorY(),
                                          order.buildOrigin.z - prefab.getAnchorZ());
        Rotation3f rotation = order.rotationDegrees == 0 ? new Rotation3f() : new Rotation3f(0f, (float) Math.toRadians(order.rotationDegrees), 0f);
        order.previewRef = PersistentPrefabPreview.spawn(store, position, rotation, prefabKey, layers);
        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                      "[ConstructionPreview] Spawned %s hologram for order %s at %s (yaw=%d).",
                      mode,
                      order.id,
                      order.buildOrigin,
                      order.rotationDegrees);
    }
}
