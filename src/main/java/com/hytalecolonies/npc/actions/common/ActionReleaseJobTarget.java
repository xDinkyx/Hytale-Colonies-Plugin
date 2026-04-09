package com.hytalecolonies.npc.actions.common;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Unclaims the block at {@link JobTargetComponent#targetPosition} and sets the
 * position to {@code null}. {@code SensorJobTargetExists} will return false
 * after this action fires.
 */
public class ActionReleaseJobTarget extends ActionBase {

    public ActionReleaseJobTarget(@Nonnull BuilderActionReleaseJobTarget builder,
                                   @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           @Nullable InfoProvider sensorInfo, double dt,
                           @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        String npcId = DebugLog.npcId(ref, store);

        DebugLog.fine(DebugCategory.JOB_SYSTEM, "[ReleaseJobTarget] [%s] Action started.", npcId);

        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jobTarget == null) {
            DebugLog.fine(DebugCategory.JOB_SYSTEM, "[ReleaseJobTarget] [%s] No JobTargetComponent present -- nothing to do.", npcId);
            return true;
        }

        Vector3i position = jobTarget.targetPosition;
        if (position != null) {
            World world = store.getExternalData().getWorld();
            final Vector3i capturedPosition = new Vector3i(position.x, position.y, position.z);
            world.execute(() -> ClaimBlockUtil.unclaimBlock(world, capturedPosition));
            jobTarget.setTargetPosition(null);
            DebugLog.info(DebugCategory.JOB_SYSTEM,
                    "[ReleaseJobTarget] [%s] Released claim on %s.", npcId, position);
        } else {
            DebugLog.fine(DebugCategory.JOB_SYSTEM,
                    "[ReleaseJobTarget] [%s] No target to release.", npcId);
        }

        // Reset workAvailable so the next seek cycle can try again fresh.
        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job != null) {
            job.workAvailable = true;
        }

        return true;
    }
}
