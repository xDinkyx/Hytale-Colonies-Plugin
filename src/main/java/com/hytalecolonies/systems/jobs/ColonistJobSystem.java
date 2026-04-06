package com.hytalecolonies.systems.jobs;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ColonistStateUtil;
import com.hytalecolonies.utils.WorkStationUtil;

/**
 * Dispatches the shared ECS job states ({@link JobState#CollectingDrops} and
 * {@link JobState#TravelingHome}) for all colonists.
 *
 * <p>Job-specific states (Idle, Working) are now driven entirely by the NPC
 * role JSON instruction engine ({@code Colonist_Woodsman.json},
 * {@code Colonist_Miner.json}). This system handles only the ECS-side phases
 * that require timer or distance checks unavailable in JSON.
 */
public class ColonistJobSystem extends DelayedEntitySystem<EntityStore> {

    private static final Map<JobState, List<JobStateHandler>> sharedHandlers =
            new EnumMap<>(JobState.class);

    private final Query<EntityStore> query = Query.and(JobComponent.getComponentType());

    public ColonistJobSystem() {
        super(2.0f);
    }

    /** Registers a handler for {@code state}. Multiple handlers per state are invoked in order. */
    public static void registerShared(@Nonnull JobState state, @Nonnull JobStateHandler handler) {
        sharedHandlers.computeIfAbsent(state, k -> new ArrayList<>()).add(handler);
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        JobComponent job = archetypeChunk.getComponent(index, JobComponent.getComponentType());
        assert job != null;

        DebugLog.fine(DebugCategory.JOB_SYSTEM,
                "[ColonistJob] [%s] Tick colonist %d with job state %s.",
                DebugLog.npcId(archetypeChunk.getReferenceTo(index), store), index, job.getCurrentTask());

        // Self-correct if the NPC loaded with a persisted JobComponent but is still
        // on the wrong role (e.g. server restart after a role-switch failure).
        Ref<EntityStore> colonistRef = archetypeChunk.getReferenceTo(index);
        NPCEntity npcEntity = store.getComponent(colonistRef, NPCEntity.getComponentType());
        if (npcEntity != null) {
            Role currentRole = npcEntity.getRole();
            if (currentRole != null && !currentRole.isRoleChangeRequested()) {
                WorkStationComponent workStation = WorkStationUtil.resolve(store, colonistRef);
                if (workStation != null) {
                    String expectedRole = ColonistRoleMap.roleFor(workStation.getJobType());
                    String actualRole = NPCPlugin.get().getName(currentRole.getRoleIndex());
                    if (!expectedRole.equals(actualRole)) {
                        DebugLog.info(DebugCategory.JOB_ASSIGNMENT,
                                "[ColonistJob] [%s] Role mismatch: NPC is '%s' but should be '%s' -- switching.",
                                DebugLog.npcId(colonistRef, store), actualRole, expectedRole);
                        ColonistRoleMap.switchRole(colonistRef, store, expectedRole);
                        return; // Skip handlers this tick -- archetype change invalidates refs.
                    }
                }
            }
        }

        JobState state = job.getCurrentTask();
        if (state == null) {
            DebugLog.warning(DebugCategory.JOB_SYSTEM,
                    "[ColonistJob] [%s] Colonist has null JobState -- resetting to Idle.",
                    DebugLog.npcId(colonistRef, store));
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idling);
            return;
        }

        // Detect which job-type component this colonist carries.
        ComponentType<EntityStore, ?> jobType = null;
        for (ComponentType<EntityStore, ?> type : JobRegistry.getJobComponentTypes()) {
            if (store.getComponent(colonistRef, type) != null) {
                jobType = type;
                break;
            }
        }

        // Resolve: job-specific handler first, shared fallback second.
        JobStateHandler jobSpecific = JobBehaviorRegistry.resolve(jobType, state);
        JobContext ctx = new JobContext(colonistRef, job, store, commandBuffer);
        if (jobSpecific != null) {
            jobSpecific.handle(ctx);
        }
        List<JobStateHandler> shared = sharedHandlers.get(state);
        if (shared != null && !shared.isEmpty()) {
            for (JobStateHandler handler : shared) {
                handler.handle(ctx);
            }
        }
        // TravelingToJob, Working: driven by JSON navigation and MinerWorkingSystem respectively.
        // NoWork: fall through safely until ECS assigns new work.
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return query;
    }
}
