package com.hytalecolonies.npc.sensors;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
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
 * Runtime sensor -- fires when {@link JobComponent#getCurrentTask()} equals
 * the state name configured in {@link BuilderSensorEcsJobState}.
 *
 * <p>This is the bridge between the NPC role state machine and the ECS job
 * pipeline, allowing JSON instructions to gate on whether the ECS side is
 * idle, collecting drops, delivering, etc.
 */
public class SensorEcsJobState extends SensorBase {

    private final JobState targetState;

    public SensorEcsJobState(@Nonnull BuilderSensorEcsJobState builder,
                              @Nonnull BuilderSupport support) {
        super(builder);
        JobState parsed;
        try {
            parsed = JobState.valueOf(builder.getStateName());
        } catch (IllegalArgumentException e) {
            parsed = JobState.Idle;
        }
        this.targetState = parsed;
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           double dt, @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, role, dt, store)) {
            return false;
        }

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) {
            return false;
        }

        boolean matches = job.getCurrentTask() == targetState;
        DebugLog.fine(DebugCategory.JOB_SYSTEM,
                "[SensorEcsJobState] current=%s target=%s result=%b.",
                job.getCurrentTask(), targetState, matches);
        return matches;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }
}
