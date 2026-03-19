package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.logging.Level;

/**
 * Generic colonist movement system. Drives the travel legs of the job state
 * machine for any job type:
 *
 * <ul>
 *   <li>{@link JobState#TravelingToJob} — moves the colonist toward
 *       {@code jobTarget.targetPosition}; advances to {@link JobState#Working}
 *       on XZ arrival or after getting stuck.</li>
 *   <li>{@link JobState#TravelingHome} — moves the colonist toward the
 *       workstation origin; on arrival removes {@link JobTargetComponent}
 *       and returns to {@link JobState#Idle}.</li>
 * </ul>
 *
 * The {@link JobState#Idle} and {@link JobState#Working} transitions are
 * handled by job-specific systems (e.g. {@link WoodsmanJobSystem}).
 * Those systems add/clear {@link JobTargetComponent} and dispatch initial
 * navigation; this system takes over from there.
 */
public class ColonistMovementSystem extends DelayedEntitySystem<EntityStore> {

    /** XZ distance to consider a colonist "at" their job target. */
    private static final float JOB_ARRIVAL_XZ = 4.0f;
    /** 3D distance to consider a colonist "at" the workstation. */
    private static final float WORKSTATION_ARRIVAL_3D = 3.5f;
    /** Consecutive stuck ticks before forcing a state advance. */
    private static final int STUCK_TICKS_LIMIT = 5;

    private final Query<EntityStore> query = Query.and(
            JobComponent.getComponentType(),
            JobTargetComponent.getComponentType()
    );

    public ColonistMovementSystem() {
        super(2.0f); // Check travel state every 2 seconds.
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        JobComponent job = archetypeChunk.getComponent(index, JobComponent.getComponentType());
        JobTargetComponent jobTarget = archetypeChunk.getComponent(index, JobTargetComponent.getComponentType());
        assert job != null && jobTarget != null;

        Ref<EntityStore> colonistRef = archetypeChunk.getReferenceTo(index);
        TransformComponent transform = store.getComponent(colonistRef, TransformComponent.getComponentType());
        if (transform == null) {
            DebugLog.log(DebugCategory.MOVEMENT, Level.WARNING, "[ColonistMovement] Colonist has no TransformComponent — skipping.");
            return;
        }

        Vector3d colonistPos = transform.getTransform().getPosition();
        JobState state = job.getCurrentTask();

        if (state == JobState.TravelingToJob) {
            handleTravelingToJob(job, jobTarget, colonistPos);
        } else if (state == JobState.TravelingHome) {
            handleTravelingHome(colonistRef, job, jobTarget, colonistPos, commandBuffer);
        }
    }

    // ===== State handlers =====

    private void handleTravelingToJob(JobComponent job, JobTargetComponent jobTarget, Vector3d colonistPos) {
        Vector3i targetPos = jobTarget.targetPosition;
        if (targetPos == null) {
            // Job system cleared the target — reset so it can pick a new one.
            job.setCurrentTask(JobState.Idle);
            return;
        }

        double dx = colonistPos.x - (targetPos.x + 0.5);
        double dz = colonistPos.z - (targetPos.z + 0.5);
        double xzDistSq = dx * dx + dz * dz;
        double xzDist = Math.sqrt(xzDistSq);

        // Stuck detection: track cell-level position changes.
        Vector3i currentCell = new Vector3i((int) colonistPos.x, (int) colonistPos.y, (int) colonistPos.z);
        if (currentCell.equals(jobTarget.lastKnownPosition)) {
            jobTarget.stuckTicks++;
        } else {
            jobTarget.stuckTicks = 0;
            jobTarget.lastKnownPosition = currentCell;
        }

        boolean arrivedXZ = xzDistSq <= JOB_ARRIVAL_XZ * JOB_ARRIVAL_XZ;
        boolean stuck = jobTarget.stuckTicks >= STUCK_TICKS_LIMIT;

        DebugLog.log(DebugCategory.MOVEMENT,
                "[ColonistMovement] TravelingToJob — xzDist=%.2f to %s (threshold %.1f) stuckTicks=%d.",
                xzDist, targetPos, JOB_ARRIVAL_XZ, jobTarget.stuckTicks);

        if (arrivedXZ || stuck) {
            if (stuck && !arrivedXZ) {
                DebugLog.log(DebugCategory.MOVEMENT, Level.INFO,
                        "[ColonistMovement] Stuck near job target %s (xzDist=%.2f) — advancing to Working.", targetPos, xzDist);
            }
            jobTarget.stuckTicks = 0;
            jobTarget.lastKnownPosition = null;
            job.setCurrentTask(JobState.Working);
            DebugLog.log(DebugCategory.MOVEMENT, Level.INFO, "[ColonistMovement] Arrived at job target %s.", targetPos);
        }
    }

    private void handleTravelingHome(Ref<EntityStore> ref, JobComponent job, JobTargetComponent jobTarget,
                                     Vector3d colonistPos, CommandBuffer<EntityStore> commandBuffer) {
        Vector3i workStationPos = job.getWorkStationBlockPosition();
        if (workStationPos == null) return;

        double dx = colonistPos.x - (workStationPos.x + 0.5);
        double dy = colonistPos.y - workStationPos.y;
        double dz = colonistPos.z - (workStationPos.z + 0.5);
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        DebugLog.log(DebugCategory.MOVEMENT,
                "[ColonistMovement] TravelingHome — dist=%.2f to workstation %s (threshold %.1f).",
                dist, workStationPos, WORKSTATION_ARRIVAL_3D);

        if (dist <= WORKSTATION_ARRIVAL_3D) {
            jobTarget.stuckTicks = 0;
            jobTarget.lastKnownPosition = null;
            commandBuffer.removeComponent(ref, JobTargetComponent.getComponentType());
            job.setCurrentTask(JobState.Idle);
            DebugLog.log(DebugCategory.MOVEMENT, Level.INFO, "[ColonistMovement] Arrived home at workstation.");
            return;
        }

        // Stuck detection — re-dispatch nav if the colonist hasn't moved (e.g. after server restart).
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
            Vector3d wsTarget = new Vector3d(workStationPos.x + 0.5, workStationPos.y, workStationPos.z + 0.5);
            commandBuffer.addComponent(ref, MoveToTargetComponent.getComponentType(), new MoveToTargetComponent(wsTarget));
            DebugLog.log(DebugCategory.MOVEMENT, Level.INFO, "[ColonistMovement] TravelingHome — stuck, re-dispatching nav to workstation.");
        }
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return query;
    }
}
