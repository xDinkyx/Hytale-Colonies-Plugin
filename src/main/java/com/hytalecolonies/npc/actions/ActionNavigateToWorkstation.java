package com.hytalecolonies.npc.actions;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ColonistLeashUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Sets the NavTarget stored-position slot (index 0) to the colonist's assigned workstation
 * and re-anchors the NPC leash to the same position.
 *
 * <p>Use this in {@code TravelingToHome} and {@code Default} instruction blocks so that
 * the {@code ReadPosition} sensor always has a valid destination even after a server restart,
 * and the {@code WanderInCircle} motion stays anchored at the workstation.
 *
 * <p>No-ops when the colonist has no {@link JobComponent} or workstation position.
 *
 * <p>Replaces the {@code world.execute(() -> JobNavigationUtil.dispatchNavigation(...))} calls
 * that were previously issued from
 * {@link com.hytalecolonies.systems.jobs.handlers.SharedHandlers#TRAVELING_TO_WORKSTATION}
 * and the idle factory.
 *
 * <p>Constructed by {@link BuilderActionNavigateToWorkstation}.
 */
public class ActionNavigateToWorkstation extends ActionBase {

    /** Slot index of "NavTarget" in the colonist role's stored-position list. */
    private static final int NAV_TARGET_SLOT = 0;

    public ActionNavigateToWorkstation(@Nonnull BuilderActionNavigateToWorkstation builder,
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
            DebugLog.warning(DebugCategory.MOVEMENT,
                    "[NavigateToWorkstation] [%s] No JobComponent -- cannot set nav target.", npcId);
            return true;
        }

        Vector3i wsPos = job.getWorkStationBlockPosition();
        if (wsPos == null) {
            DebugLog.warning(DebugCategory.MOVEMENT,
                    "[NavigateToWorkstation] [%s] No workstation position -- cannot set nav target.", npcId);
            return true;
        }

        double navX = wsPos.x + 0.5;
        double navY = wsPos.y;
        double navZ = wsPos.z + 0.5;

        role.getMarkedEntitySupport().getStoredPosition(NAV_TARGET_SLOT).assign(navX, navY, navZ);
        ColonistLeashUtil.setLeash(ref, store, new Vector3d(navX, navY, navZ));

        DebugLog.fine(DebugCategory.MOVEMENT,
                "[NavigateToWorkstation] [%s] NavTarget + leash set to workstation at %s.", npcId, wsPos);

        return true;
    }
}
