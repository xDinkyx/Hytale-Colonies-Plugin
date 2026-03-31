package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumMap;
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

    private static final Map<JobState, JobStateHandler> sharedHandlers =
            new EnumMap<>(JobState.class);

    private final Query<EntityStore> query = Query.and(JobComponent.getComponentType());

    public ColonistJobSystem() {
        super(2.0f);
    }

    /** Called from {@code HytaleColoniesPlugin.registerSharedJobHandlers()}. */
    public static void registerShared(@Nonnull JobState state, @Nonnull JobStateHandler handler) {
        sharedHandlers.put(state, handler);
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

        JobState state = job.getCurrentTask();
        if (state == null) {
            DebugLog.warning(DebugCategory.JOB_SYSTEM,
                    "[ColonistJob] Colonist has null JobState — resetting to Idle.");
            job.setCurrentTask(JobState.Idle);
            return;
        }

        JobStateHandler handler = sharedHandlers.get(state);
        if (handler != null) {
            Ref<EntityStore> colonistRef = archetypeChunk.getReferenceTo(index);
            handler.handle(new JobContext(colonistRef, job, store, commandBuffer));
        } else if (state == JobState.TravelingToJob) {
            // TravelingToJob was managed by old ECS handler code that no longer exists.
            // The JSON role engine now handles navigation directly, so reset to Idle
            // so the NPC instruction engine can take over cleanly.
            DebugLog.warning(DebugCategory.JOB_SYSTEM,
                    "[ColonistJob] Stale ECS state '%s' (no handler) — resetting to Idle.", state);
            job.setCurrentTask(JobState.Idle);
        }
        // Idle and Working are driven entirely by the NPC role JSON engine — no action needed here.
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return query;
    }
}
