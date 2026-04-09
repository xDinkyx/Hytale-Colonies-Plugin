package com.hytalecolonies.npc.actions;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ColonistStateUtil;
import com.hytalecolonies.utils.JobNavigationUtil;
import com.hytalecolonies.utils.WorkstationContainerUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Locates the nearest item-container within {@link #SEARCH_RADIUS} blocks of the workstation,
 * sets it as the active job target and NavTarget.
 * Transitions to {@link JobState#TravelingToHome} if no container is found.
 *
 * <p>Constructed by {@link BuilderActionFindDeliveryContainer}.
 */
public class ActionFindDeliveryContainer extends ActionBase {

    /** Slot index of "NavTarget" in the colonist role's stored-position list. */
    private static final int NAV_TARGET_SLOT = 0;

    /** 3D Euclidean radius in blocks to search around the workstation. */
    private static final int SEARCH_RADIUS = 3;

    public ActionFindDeliveryContainer(@Nonnull BuilderActionFindDeliveryContainer builder,
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
                    "[FindDeliveryContainer] [%s] No JobComponent.", npcId);
            return true;
        }

        // If already heading to a container this delivery run, just keep nav current.
        if (job.deliveryContainerPosition != null) {
            Vector3i cp = job.deliveryContainerPosition;
            role.getMarkedEntitySupport().getStoredPosition(NAV_TARGET_SLOT)
                    .assign(cp.x + 0.5, (double) cp.y, cp.z + 0.5);
            DebugLog.fine(DebugCategory.COLONIST_DELIVERY,
                    "[FindDeliveryContainer] [%s] Already heading to container at %s.", npcId, cp);
            return true;
        }

        // Need the workstation position to search near.
        Vector3i wsPos = job.getWorkStationBlockPosition();
        if (wsPos == null) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[FindDeliveryContainer] [%s] No workstation position -- transitioning home.", npcId);
            ColonistStateUtil.setJobState(ref, store, job, JobState.TravelingToHome);
            return true;
        }

        // Scan the spatial index for the nearest container near the workstation.
        World world = store.getExternalData().getWorld();
        Vector3i containerPos = WorkstationContainerUtil.findNearbyContainer(world, wsPos, SEARCH_RADIUS);

        if (containerPos == null) {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                    "[FindDeliveryContainer] [%s] No container within %d blocks of workstation %s -- going home.",
                    npcId, SEARCH_RADIUS, wsPos);
            ColonistStateUtil.setJobState(ref, store, job, JobState.TravelingToHome);
            return true;
        }

        // Found a container -- set target and update navigation.
        job.deliveryContainerPosition = containerPos;
        JobNavigationUtil.setJobTarget(store, ref, containerPos);
        role.getMarkedEntitySupport().getStoredPosition(NAV_TARGET_SLOT)
                .assign(containerPos.x + 0.5, (double) containerPos.y, containerPos.z + 0.5);

        DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                "[FindDeliveryContainer] [%s] Found container at %s -- navigating.", npcId, containerPos);

        return true;
    }
}
