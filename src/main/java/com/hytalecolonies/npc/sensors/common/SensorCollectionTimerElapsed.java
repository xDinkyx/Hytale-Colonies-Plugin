package com.hytalecolonies.npc.sensors.common;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;

/**
 * Fires when {@code COLLECTION_DURATION_MS} has elapsed since {@code JobComponent.collectingDropsSince} was set.
 *
 * <p>Constructed by {@link BuilderSensorCollectionTimerElapsed}.
 */
public class SensorCollectionTimerElapsed extends SensorBase {

    private static final long COLLECTION_DURATION_MS = 5_000L;

    public SensorCollectionTimerElapsed(@Nonnull BuilderSensorCollectionTimerElapsed builder,
                                        @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref,
                           @Nonnull Role role,
                           double dt,
                           @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, role, dt, store)) return false;

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) return false;

        return System.currentTimeMillis() - job.collectingDropsSince >= COLLECTION_DURATION_MS;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }
}
