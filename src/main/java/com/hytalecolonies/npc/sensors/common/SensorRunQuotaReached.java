package com.hytalecolonies.npc.sensors.common;

import com.hytalecolonies.components.jobs.JobRunCounterComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.utils.WorkStationUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Fires when {@link JobRunCounterComponent#count} is at or above
 * {@link WorkStationComponent#blocksPerRun}. Usable by any job type that
 * enforces a per-run block quota.
 */
public class SensorRunQuotaReached extends SensorBase {

    public SensorRunQuotaReached(@Nonnull BuilderSensorRunQuotaReached builder,
                                  @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           double dt, @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, role, dt, store)) 
            return false;

        JobRunCounterComponent counter = store.getComponent(ref, JobRunCounterComponent.getComponentType());
        if (counter == null) 
            return false;

        WorkStationComponent workStation = WorkStationUtil.resolve(store, ref);
        if (workStation == null) 
            return false;

        return counter.count >= workStation.blocksPerRun;
    }

    @Override
    public @Nullable InfoProvider getSensorInfo() {
        return null;
    }
}
