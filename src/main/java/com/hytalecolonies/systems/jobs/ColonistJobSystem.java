package com.hytalecolonies.systems.jobs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ColonistStateUtil;
import com.hytalecolonies.utils.WorkStationUtil;

/**
 * Periodic consistency-check system for colonist NPCs.
 *
 * <p>All state transitions (Idle, WaitingForWork, Working, TravelingToWorkSite,
 * TravelingToWorkstation, TravelingToHome, CollectingDrops, DeliveringItems) are
 * now fully driven by the NPC role JSON instruction engine via custom actions and
 * sensors. This system retains only two lightweight safety checks:
 *
 * <ol>
 *   <li><b>Role-consistency guard</b>: ensures the NPC is on the correct JSON role
 *       for its assigned job type (catches stale state after server restart or
 *       mid-flight role-switch failure).</li>
 *   <li><b>Null-state reset</b>: resets colonists whose {@code JobState} is
 *       {@code null} (should not happen in normal operation) back to {@code Idle}.</li>
 * </ol>
 */
public class ColonistJobSystem extends DelayedEntitySystem<EntityStore> {

    private final Query<EntityStore> query = Query.and(JobComponent.getComponentType());

    public ColonistJobSystem() {
        super(2.0f);
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        JobComponent job = archetypeChunk.getComponent(index, JobComponent.getComponentType());
        assert job != null;

        Ref<EntityStore> colonistRef = archetypeChunk.getReferenceTo(index);

        // Null-state guard: should not happen, but recover gracefully.
        JobState state = job.getCurrentTask();
        if (state == null) {
            DebugLog.warning(DebugCategory.JOB_SYSTEM,
                    "[ColonistJob] [%s] Colonist has null JobState -- resetting to Idle.",
                    DebugLog.npcId(colonistRef, store));
            ColonistStateUtil.setJobState(colonistRef, store, job, JobState.Idle);
            return;
        }

        // Role-consistency guard: self-correct if the NPC is on the wrong role.
        NPCEntity npcEntity = store.getComponent(colonistRef, NPCEntity.getComponentType());
        if (npcEntity == null) return;

        Role currentRole = npcEntity.getRole();
        if (currentRole == null || currentRole.isRoleChangeRequested()) return;

        WorkStationComponent workStation = WorkStationUtil.getWorkStation(store, colonistRef);
        if (workStation == null) return;

        String expectedRole = ColonistRoleMap.roleFor(workStation.getJobType());
        String actualRole = NPCPlugin.get().getName(currentRole.getRoleIndex());
        if (!expectedRole.equals(actualRole)) {
            DebugLog.info(DebugCategory.JOB_ASSIGNMENT,
                    "[ColonistJob] [%s] Role mismatch: NPC is '%s' but should be '%s' -- switching.",
                    DebugLog.npcId(colonistRef, store), actualRole, expectedRole);
            ColonistRoleMap.switchRole(colonistRef, store, expectedRole);
        }
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return query;
    }
}
