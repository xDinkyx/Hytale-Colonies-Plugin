package com.hytalecolonies.npc.actions.common;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hytalecolonies.utils.JobNavigationUtil;
import com.hytalecolonies.utils.WorkStationUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

/**
 * Abstract base for NPC actions that scan for a target block to work at,
 * and sets navigation toward it. Marks {@code workAvailable = false} when the
 * scan returns nothing.
 *
 * <p>Subclasses implement {@link #findNextBlock} for job-specific scans, and
 * may override {@link #preProcess} for one-time setup and {@link #getClaimLabel}
 * for debug identification.
 */
public abstract class ActionSeekNextBlockBase extends ActionBase {

    /** Slot index for the "NavTarget" stored position in the colonist role JSON. */
    protected static final int NAV_TARGET_SLOT = 0;

    protected ActionSeekNextBlockBase(@Nonnull BuilderActionBase builder,
                                      @Nonnull BuilderSupport support) {
        super(builder);
    }

    /**
     * Called once per execute before the scan. Override for per-execute
     * workstation-side initialization (e.g., mine origin computation).
     * Default is a no-op.
     */
    protected void preProcess(@Nonnull WorkStationComponent ws,
                              @Nonnull JobComponent job,
                              @Nonnull String npcId) {}

    /**
     * Scans for the next eligible target block. Return {@code null} when none
     * is available (signals exhaustion).
     */
    @Nullable
    protected abstract Vector3i findNextBlock(@Nonnull WorkStationComponent ws,
                                              @Nonnull World world,
                                              @Nonnull String npcId);

    /** Label passed to {@link ClaimBlockUtil} for debug identification. */
    protected String getClaimLabel() {
        return "Job";
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           @Nullable InfoProvider sensorInfo, double dt,
                           @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);
        String npcId = DebugLog.npcId(ref, store);

        WorkStationComponent workStation = WorkStationUtil.resolve(store, ref);
        if (workStation == null) {
            DebugLog.fine(DebugCategory.PERFORMANCE, "[SeekNextBlock] [%s] Workstation not found -- skipping.", npcId);
            return true;
        }

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) {
            DebugLog.warning(DebugCategory.PERFORMANCE, "[SeekNextBlock] [%s] No JobComponent -- skipping.", npcId);
            return true;
        }

        preProcess(workStation, job, npcId);

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            DebugLog.warning(DebugCategory.PERFORMANCE, "[SeekNextBlock] [%s] No UUIDComponent -- cannot claim.", npcId);
            return true;
        }
        UUID colonistUuid = uuidComponent.getUuid();

        World world = store.getExternalData().getWorld();

        // Re-use an existing valid target without re-scanning.
        JobTargetComponent existingTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (existingTarget != null && existingTarget.targetPosition != null) {
            Vector3i pos = existingTarget.targetPosition;
            if (world.getBlock(pos.x, pos.y, pos.z) == 0) {
                // Block already air -- release stale claim and fall through to re-scan.
                final Vector3i stalePos = new Vector3i(pos.x, pos.y, pos.z);
                world.execute(() -> ClaimBlockUtil.unclaimBlock(world, stalePos));
                existingTarget.setTargetPosition(null);
            } else {
                // Valid target still exists -- just restore the NavTarget slot.
                role.getMarkedEntitySupport().getStoredPosition(NAV_TARGET_SLOT)
                        .assign(pos.x + 0.5, (double) pos.y, pos.z + 0.5);
                return true;
            }
        }

        Vector3i nextBlock = findNextBlock(workStation, world, npcId);
        if (nextBlock == null) {
            job.workAvailable = false;
            return true;
        }

        final Vector3i candidate = nextBlock;
        final Role capturedRole = role;
        final String claimLabel = getClaimLabel();
        world.execute(() -> {
            // Guard against duplicate callbacks in the same cycle.
            JobTargetComponent current = store.getComponent(ref, JobTargetComponent.getComponentType());
            if (current != null && current.targetPosition != null) return;

            if (!ClaimBlockUtil.claimBlock(world, candidate, colonistUuid, claimLabel)) {
                DebugLog.fine(DebugCategory.PERFORMANCE,
                        "[SeekNextBlock] [%s] Claim of %s FAILED (race) -- will retry.", npcId, candidate);
                return;
            }

            JobNavigationUtil.setJobTarget(store, ref, candidate);
            capturedRole.getMarkedEntitySupport().getStoredPosition(NAV_TARGET_SLOT)
                    .assign(candidate.x + 0.5, (double) candidate.y, candidate.z + 0.5);

            JobComponent liveJob = store.getComponent(ref, JobComponent.getComponentType());
            if (liveJob != null) liveJob.workAvailable = true;
        });

        return true;
    }
}
