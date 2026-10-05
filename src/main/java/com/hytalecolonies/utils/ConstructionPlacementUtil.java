package com.hytalecolonies.utils;

import java.nio.file.Path;

import javax.annotation.Nonnull;

import org.joml.Vector3d;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentPrefabPreview;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.ConstructionPlacementSession;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;

/** Spawns/moves/removes the ghost hologram shown while a player is positioning a construction order (before it is confirmed into a real order). */
public final class ConstructionPlacementUtil
{
    private ConstructionPlacementUtil() {}

    /** Converts a body yaw (radians) into a snapped 0/90/180/270 rotation, in whichever direction the player is currently facing. */
    public static int snapYawToRotationDegrees(float yawRadians)
    {
        double degrees = Math.toDegrees(yawRadians);
        int    snapped = (int) Math.round(degrees / 90.0) * 90;
        return ((snapped % 360) + 360) % 360;
    }

    /** (Re)spawns the ghost at {@code session.position}/{@code session.rotationDegrees}, replacing any previous ghost. No-op if no prefab is pending. */
    public static void updateGhost(@Nonnull ConstructionPlacementSession session, @Nonnull Store<EntityStore> store)
    {
        if (session.prefabId == null || session.position == null)
            return;

        String prefabKey = ConstructorUtil.derivePrefabKey(session.prefabId);
        if (prefabKey == null)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructionPlacement] Could not derive a browsable prefab key from '%s'.", session.prefabId);
            return;
        }

        Path rawPath = PrefabStore.get().findBrowsablePrefabPath(prefabKey);
        if (rawPath == null)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructionPlacement] Prefab key '%s' not found.", prefabKey);
            return;
        }
        BlockSelection raw = PrefabStore.get().getPrefab(rawPath);
        if (raw == null)
            return;

        clearGhost(session, store);

        Vector3d position = new Vector3d(session.position.x - raw.getAnchorX(), session.position.y - raw.getAnchorY(), session.position.z - raw.getAnchorZ());
        Rotation3f rotation = session.rotationDegrees == 0 ? new Rotation3f() : new Rotation3f(0f, (float) Math.toRadians(session.rotationDegrees), 0f);
        session.ghostRef = PersistentPrefabPreview.spawn(store, position, rotation, prefabKey, Integer.MAX_VALUE);
    }

    /** Removes the session's ghost, if any. Safe to call repeatedly. */
    public static void clearGhost(@Nonnull ConstructionPlacementSession session, @Nonnull Store<EntityStore> store)
    {
        if (session.ghostRef != null && session.ghostRef.isValid())
            PersistentPrefabPreview.remove(store, session.ghostRef);
        session.ghostRef = null;
    }
}
