package com.hytalecolonies.npc.sensors;

import com.hytalecolonies.components.jobs.JobTargetComponent;
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

/** Runtime sensor -- fires when {@link JobTargetComponent} is present and its position is non-null. */
public class SensorJobTargetExists extends SensorBase {

    public SensorJobTargetExists(@Nonnull BuilderSensorJobTargetExists builder,
                                  @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           double dt, @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, role, dt, store)) {
            return false;
        }

        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        boolean exists = jobTarget != null && jobTarget.targetPosition != null;
        DebugLog.fine(DebugCategory.JOB_SYSTEM, "[SensorJobTargetExists] [%s] result=%b.", DebugLog.npcId(ref, store), exists);
        return exists;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }
}
