package com.hytalecolonies.npc.sensors.common;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.WorkStationUtil;

/** Fires when {@link WorkStationComponent#workAvailable} is {@code false}. */
public class SensorNoWorkAvailable extends SensorBase
{
    public SensorNoWorkAvailable(@Nonnull BuilderSensorNoWorkAvailable builder, @Nonnull BuilderSupport support)
    {
        super(builder);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, double dt, @Nonnull Store<EntityStore> store)
    {
        if (!super.matches(ref, role, dt, store))
        {
            return false;
        }

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null)
            return false;
        WorkStationComponent workStation = WorkStationUtil.getWorkStation(store, ref);
        boolean              result      = workStation != null && !workStation.workAvailable;
        DebugLog.fine(DebugCategory.JOB_SYSTEM,
                      "[SensorNoWorkAvailable] [%s] workStation=%s workAvailable=%s result=%s.",
                      DebugLog.npcId(ref, store),
                      workStation != null ? "present" : "null",
                      workStation != null ? workStation.workAvailable : "N/A",
                      result);
        return result;
    }

    @Override
    public InfoProvider getSensorInfo()
    {
        return null;
    }
}
