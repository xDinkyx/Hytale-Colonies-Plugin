package com.hytalecolonies.systems.jobs.handlers;

import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.systems.jobs.JobContext;
import com.hytalecolonies.systems.jobs.JobStateHandler;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;

import javax.annotation.Nullable;

/** {@link JobStateHandler} implementations shared as defaults across all job types. */
public final class SharedHandlers {

    /** How long a colonist lingers at the drop site before delivering. */
    private static final long COLLECTING_DROPS_DURATION_MS = 5_000L;

    /** XZ distance (blocks) to consider arrived at a job target. */
    private static final float JOB_ARRIVAL_XZ = 3.0f;

    /** XZ distance (blocks) to consider arrived at the workstation. */
    private static final float WORKSTATION_ARRIVAL_XZ = 3.0f;

    /** Consecutive stuck ticks before forcing state advance or re-dispatching navigation. */
    private static final int STUCK_TICKS_LIMIT = 5;

    private SharedHandlers() {}

    // ===== Handlers =====

    /** Waits at the drop site for items to settle, then transitions to {@link JobState#DeliveringItems}. */
    public static final JobStateHandler COLLECTING_DROPS = ctx -> {
        long elapsedMs = System.currentTimeMillis() - ctx.job.collectingDropsSince;
        if (elapsedMs < COLLECTING_DROPS_DURATION_MS) {
            DebugLog.fine(DebugCategory.JOB_SYSTEM, "[Shared] Collecting drops -- %.1f s remaining.",
                    (COLLECTING_DROPS_DURATION_MS - elapsedMs) / 1000.0);
            return;
        }
        DebugLog.info(DebugCategory.JOB_SYSTEM, "[Shared] Done collecting drops -- delivering items.");
        ctx.job.deliveryContainerPosition = null; // Clear stale cache so ColonistDeliverySystem scans fresh.
        ctx.job.setCurrentTask(JobState.DeliveringItems);
    };

    /** Moves toward the job target. Advances to {@link JobState#Working} on arrival or after being stuck. */
    public static final JobStateHandler TRAVELING_TO_JOB = ctx -> {
        JobTargetComponent jobTarget = ctx.store.getComponent(ctx.colonistRef, JobTargetComponent.getComponentType());
        if (jobTarget == null) {
            ctx.job.setCurrentTask(JobState.Idling);
            return;
        }
        Vector3i targetPos = jobTarget.targetPosition;
        if (targetPos == null) {
            ctx.job.setCurrentTask(JobState.Idling);
            return;
        }

        TransformComponent transform = ctx.getTransform();
        if (transform == null) {
            DebugLog.warning(DebugCategory.MOVEMENT, "[Shared] TravelingToJob -- colonist has no TransformComponent, skipping.");
            return;
        }

        Vector3d colonistPos = transform.getTransform().getPosition();
        double dx = colonistPos.x - (targetPos.x + 0.5);
        double dz = colonistPos.z - (targetPos.z + 0.5);
        double xzDistSq = dx * dx + dz * dz;
        double xzDist = Math.sqrt(xzDistSq);

        Vector3i currentCell = new Vector3i((int) colonistPos.x, (int) colonistPos.y, (int) colonistPos.z);
        if (currentCell.equals(jobTarget.lastKnownPosition)) {
            jobTarget.stuckTicks++;
        } else {
            jobTarget.stuckTicks = 0;
            jobTarget.lastKnownPosition = currentCell;
        }

        boolean arrivedXZ = xzDistSq <= JOB_ARRIVAL_XZ * JOB_ARRIVAL_XZ;
        boolean stuck = jobTarget.stuckTicks >= STUCK_TICKS_LIMIT;

        DebugLog.fine(DebugCategory.MOVEMENT,
                "[Shared] TravelingToJob -- xzDist=%.2f to %s (threshold %.1f) stuckTicks=%d.",
                xzDist, targetPos, JOB_ARRIVAL_XZ, jobTarget.stuckTicks);

        if (arrivedXZ || stuck) {
            if (stuck && !arrivedXZ) {
                DebugLog.info(DebugCategory.MOVEMENT,
                        "[Shared] Stuck near job target %s (xzDist=%.2f) -- advancing to Working.", targetPos, xzDist);
            }
            jobTarget.stuckTicks = 0;
            jobTarget.lastKnownPosition = null;
            ctx.job.setCurrentTask(JobState.Working);
            DebugLog.info(DebugCategory.MOVEMENT, "[Shared] Arrived at job target %s.", targetPos);
        }
    };

    /** Moves toward the workstation. On arrival removes {@link JobTargetComponent} and returns to {@link JobState#Idling}. */
    public static final JobStateHandler TRAVELING_HOME = ctx -> {
        Vector3i workStationPos = ctx.job.getWorkStationBlockPosition();
        if (workStationPos == null) return;

        TransformComponent transform = ctx.getTransform();
        if (transform == null) {
            DebugLog.warning(DebugCategory.MOVEMENT, "[Shared] TravelingHome -- colonist has no TransformComponent, skipping.");
            return;
        }

        Vector3d colonistPos = transform.getTransform().getPosition();
        double dx = colonistPos.x - (workStationPos.x + 0.5);
        double dz = colonistPos.z - (workStationPos.z + 0.5);
        double xzDist = Math.sqrt(dx * dx + dz * dz);

        DebugLog.fine(DebugCategory.MOVEMENT,
                "[Shared] TravelingHome -- xzDist=%.2f to workstation %s (threshold %.1f).",
                xzDist, workStationPos, WORKSTATION_ARRIVAL_XZ);

        // Fetch once -- used in both the arrival and stuck-detection paths.
        @Nullable JobTargetComponent jobTarget =
                ctx.store.getComponent(ctx.colonistRef, JobTargetComponent.getComponentType());

        if (xzDist <= WORKSTATION_ARRIVAL_XZ) {
            if (jobTarget != null) { jobTarget.stuckTicks = 0; jobTarget.lastKnownPosition = null; }
            ctx.commandBuffer.removeComponent(ctx.colonistRef, JobTargetComponent.getComponentType());
            ctx.job.setCurrentTask(JobState.Idling);
            DebugLog.info(DebugCategory.MOVEMENT, "[Shared] Arrived home at workstation.");
            return;
        }

        // Stuck detection -- re-dispatch nav if the colonist hasn't moved.
        if (jobTarget != null) {
            Vector3i currentCell = new Vector3i((int) colonistPos.x, (int) colonistPos.y, (int) colonistPos.z);
            if (currentCell.equals(jobTarget.lastKnownPosition)) {
                jobTarget.stuckTicks++;
            } else {
                jobTarget.stuckTicks = 0;
                jobTarget.lastKnownPosition = currentCell;
            }
            if (jobTarget.stuckTicks >= STUCK_TICKS_LIMIT) {
                jobTarget.stuckTicks = 0;
                jobTarget.lastKnownPosition = null;
                DebugLog.info(DebugCategory.MOVEMENT,
                        "[Shared] TravelingHome -- stuck at xzDist=%.2f, re-dispatching nav.", xzDist);
                Vector3d wsTarget = new Vector3d(workStationPos.x + 0.5, workStationPos.y, workStationPos.z + 0.5);
                ctx.commandBuffer.addComponent(ctx.colonistRef, MoveToTargetComponent.getComponentType(),
                        new MoveToTargetComponent(wsTarget));
            }
        }
    };
}
