package com.hytalecolonies.npc.actions.common;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ColonistStateUtil;

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
                    "[SetEcsJobState] Unknown state name '%s' in role JSON -- defaulting to Idle.", builder.getStateName(support));
            parsed = JobState.Idle;
        }
        this.targetState = parsed;
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           @Nullable InfoProvider sensorInfo, double dt,
                           @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        String npcId = DebugLog.npcId(ref, store);

        DebugLog.fine(DebugCategory.JOB_SYSTEM, "[SetEcsJobState] [%s] Action started (target=%s).", npcId, targetState);

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) {
            DebugLog.warning(DebugCategory.JOB_SYSTEM,
                    "[SetEcsJobState] [%s] No JobComponent -- cannot set state to %s.", npcId, targetState);
            return true;
        }

        if (targetState == JobState.CollectingDrops) {
            job.collectingDropsSince = System.currentTimeMillis();
        }
        ColonistStateUtil.setJobState(ref, store, job, targetState);

        DebugLog.info(DebugCategory.JOB_SYSTEM,
                "[SetEcsJobState] [%s] ECS job state set to %s.", npcId, targetState);

        return true;
    }
}
