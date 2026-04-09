package com.hytalecolonies.npc.sensors;

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
 * Runtime sensor that fires when 5 seconds have elapsed since the colonist entered the
 * {@link com.hytalecolonies.components.jobs.JobState#CollectingDrops} state.
 *
 * <p>The timestamp is recorded by {@link com.hytalecolonies.npc.actions.ActionSetEcsJobState}
 * when transitioning to {@code CollectingDrops} ({@code job.collectingDropsSince}).
 *
 * <p>Replaces the timer-check logic previously in
 * {@link com.hytalecolonies.systems.jobs.handlers.SharedHandlers#COLLECTING_DROPS}.
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
