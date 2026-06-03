package com.hytalecolonies.npc.actions.common;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ColonistStateUtil;
import com.hytalecolonies.utils.JobNavigationUtil;
import com.hytalecolonies.utils.WorkStationUtil;
import com.hytalecolonies.utils.WorkstationContainerUtil;

/**
 * Locates the nearest container within {@link #SEARCH_RADIUS} blocks of the workstation and sets it as the delivery target. If no container is found,
 * transitions to {@link JobState#TravelingToWorkstation}.
 */
public class ActionFindDeliveryContainer extends ActionBase
{
    private static final int NAV_TARGET_SLOT = 0;
    private static final int SEARCH_RADIUS   = 3;

    public ActionFindDeliveryContainer(@Nonnull BuilderActionFindDeliveryContainer builder, @Nonnull BuilderSupport support)
    {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, @Nullable InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store)
    {
        super.execute(ref, role, sensorInfo, dt, store);

        String npcId = DebugLog.npcId(ref, store);

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null)
        {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY, "[FindDeliveryContainer] [%s] No JobComponent.", npcId);
            return true;
        }

        WorkStationComponent workStation = WorkStationUtil.getWorkStation(store, ref);
        if (workStation == null)
        {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY, "[FindDeliveryContainer] [%s] No WorkStationComponent.", npcId);
            ColonistStateUtil.setJobState(ref, store, job, JobState.TravelingToWorkstation);
            return true;
        }

        if (workStation.deliveryContainerPosition != null)
        {
            JobNavigationUtil.setJobTarget(store, ref, workStation.deliveryContainerPosition);
            role.getMarkedEntitySupport()
                    .getStoredPosition(NAV_TARGET_SLOT)
                    .set(workStation.deliveryContainerPosition.x + 0.5,
                         (double)workStation.deliveryContainerPosition.y,
                         workStation.deliveryContainerPosition.z + 0.5);
            return true;
        }

        Vector3i wsPos = job.getWorkStationBlockPosition();
        if (wsPos == null)
        {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                             "[FindDeliveryContainer] [%s] No workstation position -- transitioning to TravelingToWorkstation.",
                             npcId);
            ColonistStateUtil.setJobState(ref, store, job, JobState.TravelingToWorkstation);
            return true;
        }

        World    world        = store.getExternalData().getWorld();
        Vector3i containerPos = WorkstationContainerUtil.findNearbyContainer(world, wsPos, SEARCH_RADIUS);

        if (containerPos == null)
        {
            DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                             "[FindDeliveryContainer] [%s] No container within %d blocks of workstation %s -- transitioning to TravelingToWorkstation.",
                             npcId,
                             SEARCH_RADIUS,
                             wsPos);
            ColonistStateUtil.setJobState(ref, store, job, JobState.TravelingToWorkstation);
            return true;
        }

        workStation.deliveryContainerPosition = containerPos;
        JobNavigationUtil.setJobTarget(store, ref, containerPos);
        role.getMarkedEntitySupport().getStoredPosition(NAV_TARGET_SLOT).set(containerPos.x + 0.5, (double)containerPos.y, containerPos.z + 0.5);

        DebugLog.fine(DebugCategory.COLONIST_DELIVERY, "[FindDeliveryContainer] [%s] Container at %s -- navigating.", npcId, containerPos);

        return true;
    }
}
