package com.hytalecolonies.systems.jobs;

/**
 * Executes one tick of logic for a colonist in a particular {@link com.hytalecolonies.components.jobs.JobState}.
 *
 * <p>Implementations must be stateless — all runtime state lives in ECS components
 * accessible through the {@link JobContext}.
 */
@FunctionalInterface
public interface JobStateHandler {
    void handle(JobContext ctx);
}
