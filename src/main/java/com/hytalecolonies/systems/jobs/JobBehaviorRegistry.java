package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.components.jobs.JobState;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps (job-type component, {@link JobState}) to the {@link JobStateHandler} to run.
 *
 * <p>Job-specific handlers take priority over shared fallbacks.
 */
public final class JobBehaviorRegistry {

    private static final Map<ComponentType<EntityStore, ?>, Map<JobState, JobStateHandler>> jobDefaults
            = new HashMap<>();
    private static final Map<JobState, JobStateHandler> sharedHandlers
            = new EnumMap<>(JobState.class);

    private JobBehaviorRegistry() {}

    // ===== Registration =====

    /** Registers a job-specific handler. Replaces any existing handler for the same combination. */
    public static void registerDefault(ComponentType<EntityStore, ?> jobType, JobState state,
                                       JobStateHandler handler) {
        jobDefaults.computeIfAbsent(jobType, k -> new EnumMap<>(JobState.class)).put(state, handler);
    }

    /** Registers a fallback handler used when no job-specific handler is registered for a state. */
    public static void registerShared(JobState state, JobStateHandler handler) {
        sharedHandlers.put(state, handler);
    }

    // ===== Resolution =====
    /**
     * Resolves the handler for the given job type and state.
     * Falls back to the shared handler if no job-specific one is registered.
     */
    @Nullable
    public static JobStateHandler resolve(@Nullable ComponentType<EntityStore, ?> jobType, JobState state) {
        if (jobType != null) {
            Map<JobState, JobStateHandler> typeHandlers = jobDefaults.get(jobType);
            if (typeHandlers != null) {
                JobStateHandler h = typeHandlers.get(state);
                if (h != null) return h;
            }
        }
        return sharedHandlers.get(state);
    }
}
