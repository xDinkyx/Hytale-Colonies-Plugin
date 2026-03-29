package com.hytalecolonies.systems.jobs;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of job-specific entity component types.
 *
 * <p>Register each job component type on plugin startup.
 * {@link JobAssignmentSystems#fireColonist} iterates this list to remove every
 * job-type component without hardcoding the individual types.
 *
 * <p>Example registration in the plugin:
 * <pre>{@code
 * JobRegistry.register(WoodsmanJobComponent.getComponentType());
 * JobRegistry.register(MinerJobComponent.getComponentType());
 * }</pre>
 */
public final class JobRegistry {

    private static final List<ComponentType<EntityStore, ?>> jobComponentTypes = new ArrayList<>();

    private JobRegistry() {}

    /**
     * Registers a job-specific entity component type.
     * Must be called during plugin setup before any colonist is assigned a job.
     */
    public static void register(ComponentType<EntityStore, ?> type) {
        jobComponentTypes.add(type);
    }

    /**
     * Returns an unmodifiable view of all registered job component types.
     * Used by {@link JobAssignmentSystems#fireColonist} to strip all job state
     * from a colonist without knowing which job they held.
     */
    public static List<ComponentType<EntityStore, ?>> getJobComponentTypes() {
        return Collections.unmodifiableList(jobComponentTypes);
    }
}
