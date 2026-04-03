package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.utils.WorkStationUtil;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
                "[ColonistJob] Tick colonist %d with job state %s.",
                index, job.getCurrentTask());

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
                                "[ColonistJob] Role mismatch: NPC is '%s' but should be '%s' -- switching.",
                                actualRole, expectedRole);
                        ColonistRoleMap.switchRole(colonistRef, store, expectedRole);
                        return; // Skip handlers this tick -- archetype change invalidates refs.
                    }
                }
            }
        }

        JobState state = job.getCurrentTask();
        if (state == null) {
            DebugLog.warning(DebugCategory.JOB_SYSTEM,
                    "[ColonistJob] Colonist has null JobState -- resetting to Idle.");
            job.setCurrentTask(JobState.Idling);
            return;
        }

        List<JobStateHandler> handlers = sharedHandlers.get(state);
        if (handlers != null && !handlers.isEmpty()) {
            JobContext ctx = new JobContext(colonistRef, job, store, commandBuffer);
            for (JobStateHandler handler : handlers) {
                handler.handle(ctx);
            }
        }
        // TravelingToJob, Working: driven by JSON navigation and MinerWorkingSystem respectively.
        // NoWork, Idle (job-specific): handled by registered handlers above or fall through safely.
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return query;
    }
}
