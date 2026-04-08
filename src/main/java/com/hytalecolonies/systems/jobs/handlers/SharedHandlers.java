package com.hytalecolonies.systems.jobs.handlers;

import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.systems.jobs.JobContext;
import com.hytalecolonies.systems.jobs.JobStateHandler;
import com.hytalecolonies.utils.ColonistLeashUtil;
import com.hytalecolonies.utils.ColonistStateUtil;
import com.hytalecolonies.utils.ColonistToolUtil;
import com.hytalecolonies.utils.JobNavigationUtil;

/** {@link JobStateHandler} implementations shared as defaults across all job types. */
public final class SharedHandlers {

    /** How long a colonist lingers at the drop site before delivering. */
    private static final long COLLECTING_DROPS_DURATION_MS = 5_000L;

    /**
     * XZ distance (blocks) to consider arrived at a job target.
     * Must be <= the smallest JobTarget sensor Range used in any role JSON (currently 2.5)
     * so the NPC is guaranteed within sensor range the moment the ECS transitions to Working.
     */
    private static final float JOB_ARRIVAL_XZ = 2.5f;

    /** XZ distance (blocks) to consider arrived at the workstation. */
    private static final float WORKSTATION_ARRIVAL_XZ = 3.0f;

    /** Consecutive stuck ticks before forcing state advance or re-dispatching navigation. */
    private static final int STUCK_TICKS_LIMIT = 5;

    private SharedHandlers() {
    }

    // ===== Idle factory =====

    /**
     * Functional interface for the job-specific part of an Idle handler: locates the next
     * work target and performs any job-specific preparation (e.g. resetting run counters).
     */
    @FunctionalInterface
    public interface TargetFinder {
        /** Finds the next target block, or {@code null} if none is available. */
        @Nullable
        Vector3i find(JobContext ctx, WorkStationComponent workStation, Vector3i workStationPos);
    }

    /**
     * Builds a handler for both {@link JobState#Idle} and {@link JobState#WaitingForWork}.
     *
     * <p>When the colonist is not yet at the workstation (e.g. freshly assigned), dispatches
     * navigation there and transitions to {@link JobState#TravelingToWorkstation}. Once at the
     * workstation, checks tools, finds a target block, claims it, and transitions to
     * {@link JobState#TravelingToWorkSite}.
     *
     * @param requiredGatherTypes
     *                                gather-type tags the colonist must hold a tool for (all required)
     * @param targetFinder
     *                                job-specific function to locate the next target block
     * @param claimType
     *                                label stored on the claimed block (e.g. {@code "Mine"}, {@code "Harvest"})
     */
    public static JobStateHandler idle(String[] requiredGatherTypes, TargetFinder targetFinder, String claimType) {
        return ctx -> {
            Vector3i workStationPos = ctx.job.getWorkStationBlockPosition();
            if (workStationPos == null)
                return;

            final Vector3i wsPos = workStationPos;

            // If not yet at the workstation, navigate there first.
            // NOTE: do NOT dispatch any world.execute tasks here -- the entity may be mid-role-switch
            // and the ref would be invalid by the time the lambda runs.
            TransformComponent proximityTransform = ctx.getTransform();
            if (proximityTransform != null) {
                Vector3d colonistPos = proximityTransform.getTransform().getPosition();
                double pdx = colonistPos.x - (wsPos.x + 0.5);
                double pdz = colonistPos.z - (wsPos.z + 0.5);
                if (pdx * pdx + pdz * pdz > WORKSTATION_ARRIVAL_XZ * WORKSTATION_ARRIVAL_XZ) {
                    ColonistStateUtil.setJobState(ctx.colonistRef, ctx.store, ctx.job,
                            JobState.TravelingToWorkstation);
                    return;
                }
            }

            // Already at workstation -- anchor leash and keep nav pointed here.
            ColonistLeashUtil.setLeashToBlockCenter(ctx.colonistRef, ctx.store, wsPos);
            ctx.world.execute(() -> JobNavigationUtil.dispatchNavigation(ctx.world.getEntityStore().getStore(),
                    ctx.colonistRef, wsPos));

            WorkStationComponent workStation = ctx.getWorkStation();
            if (workStation == null)
                return;

            LivingEntity colonist = (LivingEntity) EntityUtils.getEntity(ctx.colonistRef, ctx.store);
            if (colonist == null)
                return;
            for (String gatherType : requiredGatherTypes) {
                if (!ColonistToolUtil.hasToolForGatherType(colonist.getInventory(), gatherType, 0))
                    return;
            }

            Vector3i target = targetFinder.find(ctx, workStation, workStationPos);
            if (target == null) {
                ctx.job.workAvailable = false;
                return;
            }
            ctx.job.workAvailable = true;

            final var entityStore = ctx.world.getEntityStore();
            final Vector3i claimTarget = target;
            ctx.world.execute(() -> {
                JobComponent liveJob = entityStore.getStore().getComponent(ctx.colonistRef,
                        JobComponent.getComponentType());
                if (liveJob == null)
                    return;
                JobState currentTask = liveJob.getCurrentTask();
                if (currentTask != JobState.Idle && currentTask != JobState.WaitingForWork)
                    return;
                UUIDComponent uuidComp = entityStore.getStore().getComponent(ctx.colonistRef,
                        UUIDComponent.getComponentType());
                if (uuidComp == null)
                    return;
                // claimAndNavigateTo atomically claims, sets JobTargetComponent, and dispatches navigation.
                if (!JobNavigationUtil.claimAndNavigateTo(ctx.world, entityStore.getStore(), ctx.colonistRef,
                        uuidComp.getUuid(), claimTarget, claimType))
                    return;
                // Set state after claim+nav so PathFindingSystem reads a consistent state.
                ColonistStateUtil.setJobState(ctx.colonistRef, entityStore.getStore(), liveJob,
                        JobState.TravelingToWorkSite);
            });
        };
    }

    // ===== Handlers =====

    /**
     * Moves toward the workstation building after initial job assignment (or when returning
     * from a previous task). On arrival transitions to {@link JobState#WaitingForWork}.
     */
    public static final JobStateHandler TRAVELING_TO_WORKSTATION = ctx -> {
        Vector3i workStationPos = ctx.job.getWorkStationBlockPosition();
        if (workStationPos == null)
            return;

        final Vector3i wsPos = workStationPos;
        ctx.world.execute(() -> {
            if (!ctx.colonistRef.isValid()) return;
            JobNavigationUtil.dispatchNavigation(ctx.world.getEntityStore().getStore(), ctx.colonistRef, wsPos);
        });

        TransformComponent transform = ctx.getTransform();
        if (transform == null) {
            DebugLog.warning(DebugCategory.MOVEMENT,
                    "[Shared] [%s] TravelingToWorkstation -- colonist has no TransformComponent, skipping.",
                    DebugLog.npcId(ctx.colonistRef, ctx.store));
            return;
        }

        Vector3d colonistPos = transform.getTransform().getPosition();
        double dx = colonistPos.x - (wsPos.x + 0.5);
        double dz = colonistPos.z - (wsPos.z + 0.5);
        double xzDist = Math.sqrt(dx * dx + dz * dz);

        DebugLog.fine(DebugCategory.MOVEMENT,
                "[Shared] [%s] TravelingToWorkstation -- xzDist=%.2f to %s (threshold %.1f).",
                DebugLog.npcId(ctx.colonistRef, ctx.store), xzDist, wsPos, WORKSTATION_ARRIVAL_XZ);

        if (xzDist <= WORKSTATION_ARRIVAL_XZ) {
            ColonistStateUtil.setJobState(ctx.colonistRef, ctx.store, ctx.job, JobState.WaitingForWork);
            DebugLog.info(DebugCategory.MOVEMENT, "[Shared] [%s] Arrived at workstation -- waiting for work.",
                    DebugLog.npcId(ctx.colonistRef, ctx.store));
        }
    };

    /** Waits at the drop site for items to settle, then transitions to {@link JobState#DeliveringItems}. */
    public static final JobStateHandler COLLECTING_DROPS = ctx -> {
        long elapsedMs = System.currentTimeMillis() - ctx.job.collectingDropsSince;
        if (elapsedMs < COLLECTING_DROPS_DURATION_MS) {
            DebugLog.fine(DebugCategory.JOB_SYSTEM, "[Shared] [%s] Collecting drops -- %.1f s remaining.",
                    DebugLog.npcId(ctx.colonistRef, ctx.store), (COLLECTING_DROPS_DURATION_MS - elapsedMs) / 1000.0);
            return;
        }
        DebugLog.info(DebugCategory.JOB_SYSTEM, "[Shared] [%s] Done collecting drops -- delivering items.",
                DebugLog.npcId(ctx.colonistRef, ctx.store));
        ctx.job.deliveryContainerPosition = null; // Clear stale cache so ColonistDeliverySystem scans fresh.
        ColonistStateUtil.setJobState(ctx.colonistRef, ctx.store, ctx.job, JobState.DeliveringItems);
    };

    /** Moves toward the job target. Advances to {@link JobState#Working} on arrival or after being stuck. */
    public static final JobStateHandler TRAVELING_TO_JOB = ctx -> {
        JobTargetComponent jobTarget = ctx.store.getComponent(ctx.colonistRef, JobTargetComponent.getComponentType());
        if (jobTarget == null) {
            ColonistStateUtil.setJobState(ctx.colonistRef, ctx.store, ctx.job, JobState.Idle);
            return;
        }
        Vector3i targetPos = jobTarget.targetPosition;
        if (targetPos == null) {
            ColonistStateUtil.setJobState(ctx.colonistRef, ctx.store, ctx.job, JobState.Idle);
            return;
        }

        TransformComponent transform = ctx.getTransform();
        if (transform == null) {
            DebugLog.warning(DebugCategory.MOVEMENT,
                    "[Shared] [%s] TravelingToJob -- colonist has no TransformComponent, skipping.",
                    DebugLog.npcId(ctx.colonistRef, ctx.store));
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
                "[Shared] [%s] TravelingToJob -- xzDist=%.2f to %s (threshold %.1f) stuckTicks=%d.",
                DebugLog.npcId(ctx.colonistRef, ctx.store), xzDist, targetPos, JOB_ARRIVAL_XZ, jobTarget.stuckTicks);

        if (arrivedXZ || stuck) {
            if (stuck && !arrivedXZ) {
                DebugLog.info(DebugCategory.MOVEMENT,
                        "[Shared] [%s] Stuck near job target %s (xzDist=%.2f) -- advancing to Working.",
                        DebugLog.npcId(ctx.colonistRef, ctx.store), targetPos, xzDist);
            }
            jobTarget.stuckTicks = 0;
            jobTarget.lastKnownPosition = null;
            ColonistStateUtil.setJobState(ctx.colonistRef, ctx.store, ctx.job, JobState.Working);
            DebugLog.info(DebugCategory.MOVEMENT, "[Shared] [%s] Arrived at job target %s.",
                    DebugLog.npcId(ctx.colonistRef, ctx.store), targetPos);
        }
    };

    /** Moves toward the workstation. On arrival removes {@link JobTargetComponent} and returns to {@link JobState#Idle}. */
    public static final JobStateHandler TRAVELING_HOME = ctx -> {
        Vector3i workStationPos = ctx.job.getWorkStationBlockPosition();
        if (workStationPos == null)
            return;

        TransformComponent transform = ctx.getTransform();
        if (transform == null) {
            DebugLog.warning(DebugCategory.MOVEMENT,
                    "[Shared] [%s] TravelingHome -- colonist has no TransformComponent, skipping.",
                    DebugLog.npcId(ctx.colonistRef, ctx.store));
            return;
        }

        Vector3d colonistPos = transform.getTransform().getPosition();
        double dx = colonistPos.x - (workStationPos.x + 0.5);
        double dz = colonistPos.z - (workStationPos.z + 0.5);
        double xzDist = Math.sqrt(dx * dx + dz * dz);

        DebugLog.fine(DebugCategory.MOVEMENT,
                "[Shared] [%s] TravelingHome -- xzDist=%.2f to workstation %s (threshold %.1f).",
                DebugLog.npcId(ctx.colonistRef, ctx.store), xzDist, workStationPos, WORKSTATION_ARRIVAL_XZ);

        // Fetch once -- used in both the arrival and stuck-detection paths.
        @Nullable
        JobTargetComponent jobTarget = ctx.store.getComponent(ctx.colonistRef, JobTargetComponent.getComponentType());

        if (xzDist <= WORKSTATION_ARRIVAL_XZ) {
            if (jobTarget != null) {
                jobTarget.stuckTicks = 0;
                jobTarget.lastKnownPosition = null;
            }
            ctx.commandBuffer.removeComponent(ctx.colonistRef, JobTargetComponent.getComponentType());
            ColonistStateUtil.setJobState(ctx.colonistRef, ctx.store, ctx.job, JobState.Idle);
            DebugLog.info(DebugCategory.MOVEMENT, "[Shared] [%s] Arrived home at workstation.",
                    DebugLog.npcId(ctx.colonistRef, ctx.store));
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
                        "[Shared] [%s] TravelingHome -- stuck at xzDist=%.2f, re-dispatching nav.",
                        DebugLog.npcId(ctx.colonistRef, ctx.store), xzDist);
                Vector3d wsTarget = new Vector3d(workStationPos.x + 0.5, workStationPos.y, workStationPos.z + 0.5);
                ctx.commandBuffer.addComponent(ctx.colonistRef, MoveToTargetComponent.getComponentType(),
                        new MoveToTargetComponent(wsTarget));
            }
        }
    };
}
