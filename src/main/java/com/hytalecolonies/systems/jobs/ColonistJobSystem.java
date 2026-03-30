package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Dispatches per-tick job logic for all colonists via {@link JobBehaviorRegistry}.
 * Resolves which job type the colonist has, then delegates to the registered {@link JobStateHandler}.
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
        JobState state = job.getCurrentTask();

        if (state == null) {
            DebugLog.warning(DebugCategory.JOB_SYSTEM,
                    "[ColonistJob] Colonist has null JobState — resetting to Idle.");
            job.setCurrentTask(JobState.Idle);
            return;
        }

        JobContext ctx = new JobContext(colonistRef, job, store, commandBuffer);
        ComponentType<EntityStore, ?> jobType = resolveJobType(colonistRef, store);
        JobStateHandler handler = JobBehaviorRegistry.resolve(jobType, state);

        if (handler != null) {
            handler.handle(ctx);
        } else {
            DebugLog.fine(DebugCategory.JOB_SYSTEM,
                    "[ColonistJob] No handler for jobType=%s state=%s — skipping.",
                    jobType != null ? jobType : "<none>", state);
        }
    }

    /** Returns the job-type component this colonist has, or {@code null} if none is registered. */
    @Nullable
    private static ComponentType<EntityStore, ?> resolveJobType(
            Ref<EntityStore> colonistRef, Store<EntityStore> store) {
        for (ComponentType<EntityStore, ?> type : JobRegistry.getJobComponentTypes()) {
            if (store.getComponent(colonistRef, type) != null) return type;
        }
        return null;
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return query;
    }
}
