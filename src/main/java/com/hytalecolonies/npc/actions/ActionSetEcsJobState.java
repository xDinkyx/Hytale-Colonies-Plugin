package com.hytalecolonies.npc.actions;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Sets {@link JobComponent#getCurrentTask()} to the state configured in
 * {@link BuilderActionSetEcsJobState}.
 *
 * <p>When transitioning to {@link JobState#CollectingDrops}, also records
 * {@code collectingDropsSince} so that the ECS delivery pipeline can begin.
 */
public class ActionSetEcsJobState extends ActionBase {

    private final JobState targetState;

    public ActionSetEcsJobState(@Nonnull BuilderActionSetEcsJobState builder,
                                 @Nonnull BuilderSupport support) {
        super(builder);
        JobState parsed;
        try {
            parsed = JobState.valueOf(builder.getStateName(support));
        } catch (IllegalArgumentException e) {
            DebugLog.warning(DebugCategory.JOB_SYSTEM,
                    "[SetEcsJobState] Unknown state name '%s' in role JSON — defaulting to Idle.", builder.getStateName(support));
            parsed = JobState.Idle;
        }
        this.targetState = parsed;
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           @Nullable InfoProvider sensorInfo, double dt,
                           @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) {
            DebugLog.warning(DebugCategory.JOB_SYSTEM,
                    "[SetEcsJobState] No JobComponent — cannot set state to %s.", targetState);
            return true;
        }

        if (targetState == JobState.CollectingDrops) {
            job.collectingDropsSince = System.currentTimeMillis();
        }
        job.setCurrentTask(targetState);

        DebugLog.info(DebugCategory.JOB_SYSTEM,
                "[SetEcsJobState] ECS job state set to %s.", targetState);

        return true;
    }
}
