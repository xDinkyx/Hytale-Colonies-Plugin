package com.hytalecolonies.npc.sensors;

import com.hytalecolonies.components.jobs.WorkerComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;

/**
 * Generic sensor that fires when the colonist's {@link WorkerComponent#noWorkAvailable}
 * flag is {@code true}.
 *
 * <p>Any seek action (SeekNextMineBlock, SeekNextTree, etc.) sets this flag when
 * it scans for task targets and finds none. The flag is cleared when work is found,
 * allowing the colonist to use this sensor to transition away from the Working state
 * once all available tasks are exhausted.
 */
public class SensorNoWorkAvailable extends SensorBase {

    public SensorNoWorkAvailable(@Nonnull BuilderSensorNoWorkAvailable builder,
                                  @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           double dt, @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, role, dt, store)) {
            return false;
        }

        WorkerComponent worker = store.getComponent(ref, WorkerComponent.getComponentType());
        boolean result = worker != null && worker.noWorkAvailable;
        DebugLog.fine(DebugCategory.JOB_SYSTEM,
                "[SensorNoWorkAvailable] worker=%s noWorkAvailable=%s result=%s.",
                worker != null ? "present" : "null",
                worker != null ? worker.noWorkAvailable : "N/A",
                result);
        return result;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }
}
