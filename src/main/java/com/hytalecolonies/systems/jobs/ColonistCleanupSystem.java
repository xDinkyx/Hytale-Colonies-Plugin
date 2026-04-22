package com.hytalecolonies.systems.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.DelayedSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.world.ClaimedBlockComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;

/**
 * Periodic safety-net system that runs every 30 seconds to repair two classes
 * of desync:
 *
 * <ol>
 *   <li><b>Orphaned block claims</b> -- Any block entity with a
 *       {@link ClaimedBlockComponent} whose colonist UUID is no longer valid
 *       (colonist has been fired/removed or no longer holds a job target) has
 *       its claim released. This covers claims left by crashes, hard removes,
 *       or server restarts.</li>
 *   <li><b>Colonists with missing workstations</b> -- Any colonist with a
 *       {@link JobComponent} whose recorded workstation no longer exists is
 *       fired. Primary firing happens in
 *       {@link JobAssignmentSystems.WorkStationEntitySystem#onEntityRemove};
 *       this is a catch-all backstop.</li>
 * </ol>
 */
public class ColonistCleanupSystem extends DelayedSystem<ChunkStore> {

    public ColonistCleanupSystem() {
        super(30.0f); // Audit every 30 seconds.
    }

    @Override
    public void delayedTick(float dt, int systemIndex, @Nonnull Store<ChunkStore> store) {
        World world = store.getExternalData().getWorld();
        EntityStore entityStore = world.getEntityStore();

        sweepOrphanedClaimMarks(store, entityStore);
        fireOrphanedColonists(world, entityStore);
    }

    private static void sweepOrphanedClaimMarks(Store<ChunkStore> store, EntityStore entityStore) {
        Query<ChunkStore> claimQuery = Query.and(ClaimedBlockComponent.getComponentType());
        List<Ref<ChunkStore>> orphanedRefs = new ArrayList<>();

        store.forEachChunk(claimQuery, (chunk, _cb) -> {
            for (int i = 0; i < chunk.size(); i++) {
                ClaimedBlockComponent claim = chunk.getComponent(i, ClaimedBlockComponent.getComponentType());
                if (claim == null) continue;

                UUID colonistUuid = claim.getClaimedByUuid();
                Ref<EntityStore> colonistRef = entityStore.getRefFromUUID(colonistUuid);

                boolean orphaned = (colonistRef == null || !colonistRef.isValid())
                        || entityStore.getStore().getComponent(colonistRef, JobTargetComponent.getComponentType()) == null;

                if (orphaned) {
                    orphanedRefs.add(chunk.getReferenceTo(i));
                }
            }
        });

        if (!orphanedRefs.isEmpty()) {
            World world = store.getExternalData().getWorld();
            world.execute(() -> releaseOrphanedClaims(orphanedRefs));
        }
    }

    private static void fireOrphanedColonists(World world, EntityStore entityStore) {
        Query<EntityStore> jobQuery = Query.and(JobComponent.getComponentType());
        List<Ref<EntityStore>> orphans = new ArrayList<>();

        entityStore.getStore().forEachChunk(jobQuery, (chunk, _cb) -> {
            for (int i = 0; i < chunk.size(); i++) {
                JobComponent job = chunk.getComponent(i, JobComponent.getComponentType());
                if (job == null) continue;
                Vector3i wsPos = job.getWorkStationBlockPosition();
                if (wsPos == null) continue;
                Ref<ChunkStore> wsRef = BlockModule.getBlockEntity(world, wsPos.x, wsPos.y, wsPos.z);
                WorkStationComponent workStation = wsRef != null
                        ? wsRef.getStore().getComponent(wsRef, WorkStationComponent.getComponentType())
                        : null;
                if (workStation != null) continue;
                orphans.add(chunk.getReferenceTo(i));
            }
        });

        if (!orphans.isEmpty()) {
            world.execute(() -> fireColonistsWithMissingWorkstation(orphans, entityStore));
        }
    }

    private static void releaseOrphanedClaims(@Nonnull List<Ref<ChunkStore>> orphanedRefs)
    {
        int cleared = 0;
        for (Ref<ChunkStore> ref : orphanedRefs) {
            if (!ref.isValid()) continue;
            // Just remove the claim component -- ClaimedBlockCleanupSystem will
            // automatically destroy the entity if it was created solely for the claim.
            ref.getStore().tryRemoveComponent(ref, ClaimedBlockComponent.getComponentType());
            cleared++;
        }
        if (cleared > 0) {
            DebugLog.info(DebugCategory.JOB_ASSIGNMENT,
                    "[ColonistCleanup] Released %d orphaned block claim(s).", cleared);
        }
    }

    private static void fireColonistsWithMissingWorkstation(
            @Nonnull List<Ref<EntityStore>> orphans,
            @Nonnull EntityStore entityStore)
    {
        for (Ref<EntityStore> ref : orphans) {
            if (ref.isValid()) {
                JobAssignmentSystems.fireColonist(ref, entityStore.getStore());
                DebugLog.warning(DebugCategory.JOB_ASSIGNMENT,
                        "[ColonistCleanup] [%s] Fired colonist with missing workstation (safety net).",
                        DebugLog.npcId(ref, entityStore.getStore()));
            }
        }
    }
}
