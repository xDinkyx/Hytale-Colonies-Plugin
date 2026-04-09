package com.hytalecolonies.npc.actions.miner;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.world.ClaimedBlockComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hytalecolonies.utils.JobNavigationUtil;
import com.hytalecolonies.utils.WorkStationUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Scans the mine shaft top-down for the first solid unclaimed block, claims it
 * atomically via {@code world.execute()}, sets it as the job target, and dispatches navigation.
 * If none is available the target is left unset and the instruction block retries next cycle.
 */
public class ActionSeekNextMineBlock extends ActionBase {

    /** Slot index for the "NavTarget" stored position in the colonist role JSON. */
    private static final int NAV_TARGET_SLOT = 0;

    public ActionSeekNextMineBlock(@Nonnull BuilderActionSeekNextMineBlock builder,
            @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
            @Nullable InfoProvider sensorInfo, double dt,
            @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        String npcId = DebugLog.npcId(ref, store);

        DebugLog.fine(DebugCategory.MINER_JOB, "[SeekNextMineBlock] [%s] Action started.", npcId);

        WorkStationComponent workStation = WorkStationUtil.resolve(store, ref);
        if (workStation == null) {
            DebugLog.fine(DebugCategory.MINER_JOB, "[SeekNextMineBlock] [%s] Workstation not found -- skipping.", npcId);
            return true;
        }

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) {
            DebugLog.warning(DebugCategory.MINER_JOB,
                    "[SeekNextMineBlock] [%s] No JobComponent -- cannot resolve workstation position.", npcId);
            return true;
        }
        Vector3i workStationPosition = job.getWorkStationBlockPosition();

        initialiseMineOriginIfNeeded(workStation, workStationPosition, npcId);

        if (uuidComponent == null) {
            DebugLog.warning(DebugCategory.MINER_JOB, "[SeekNextMineBlock] [%s] No UUIDComponent -- cannot claim block.", npcId);
            return true;
        }
        UUID colonistUuid = uuidComponent.getUuid();

        // If a job target is already set (e.g. persisted from a previous session),
        // keep navigation pointed at it -- but only if the block is still solid.
        JobTargetComponent existingTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        World world = store.getExternalData().getWorld();
        if (existingTarget != null && existingTarget.targetPosition != null) {
            Vector3i pos = existingTarget.targetPosition;
            if (world.getBlock(pos.x, pos.y, pos.z) == 0) {
                // Target block is already air -- release the stale claim and scan again.
                DebugLog.info(DebugCategory.MINER_JOB,
                        "[SeekNextMineBlock] [%s] Persisted target %s is already air -- releasing stale target.", npcId, pos);
                final Vector3i capturedPos = new Vector3i(pos.x, pos.y, pos.z);
                world.execute(() -> ClaimBlockUtil.unclaimBlock(world, capturedPos));
                existingTarget.setTargetPosition(null);
                // Fall through to scan for a new block below.
            } else {
                role.getMarkedEntitySupport().getStoredPosition(NAV_TARGET_SLOT)
                        .assign(pos.x + 0.5, (double) pos.y, pos.z + 0.5);
                DebugLog.fine(DebugCategory.MINER_JOB,
                        "[SeekNextMineBlock] [%s] Persisted target %s -- NavTarget restored.", npcId, pos);
                return true;
            }
        }
        Vector3i nextBlock = findNextAvailableMineBlock(workStation, world, npcId);

        if (nextBlock == null) {
            DebugLog.fine(DebugCategory.MINER_JOB,
                    "[SeekNextMineBlock] [%s] Shaft at origin %s has no solid unclaimed blocks -- marking no work available.",
                    npcId, workStation.mineOrigin);
            if (job != null) {
                job.workAvailable = false;
                DebugLog.fine(DebugCategory.MINER_JOB,
                        "[SeekNextMineBlock] [%s] workAvailable = false.", npcId);
            }
            return true;
        }

        DebugLog.info(DebugCategory.MINER_JOB,
                "[SeekNextMineBlock] [%s] Attempting to claim block at %s.", npcId, nextBlock);

        final Vector3i blockToMine = nextBlock;
        // Capture the role so we can write the NavTarget stored-position slot directly
        // inside world.execute(). This avoids a timing race between the CommandBuffer
        // flush (which removes MoveToTargetComponent) and world.execute() firing:
        // if MoveToTargetComponent still exists at that moment, dispatchNavigation
        // only updates the target field in-place, never triggering
        // PathFindingSystem.onComponentAdded, so the NavTarget slot is stale after
        // the first block. Writing the slot directly sidesteps the issue entirely.
        final Role capturedRole = role;
        world.execute(() -> {
            DebugLog.fine(DebugCategory.MINER_JOB,
                    "[SeekNextMineBlock] [%s] world.execute() callback fired for candidate %s.", npcId, blockToMine);
            JobTargetComponent currentTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
            if (currentTarget != null && currentTarget.targetPosition != null) {
                DebugLog.fine(DebugCategory.MINER_JOB,
                        "[SeekNextMineBlock] [%s] Already has job target %s -- skipping claim of %s.",
                        npcId, currentTarget.targetPosition, blockToMine);
                return;
            }

            if (!ClaimBlockUtil.claimBlock(world, blockToMine, colonistUuid, "Mine")) {
                DebugLog.fine(DebugCategory.MINER_JOB,
                        "[SeekNextMineBlock] [%s] Block at %s claim FAILED (already taken) -- will retry next cycle.", npcId, blockToMine);
                return;
            }

            JobNavigationUtil.setJobTarget(store, ref, blockToMine);
            capturedRole.getMarkedEntitySupport().getStoredPosition(NAV_TARGET_SLOT)
                    .assign(blockToMine.x + 0.5, (double) blockToMine.y, blockToMine.z + 0.5);
            // Clear the flag now that work has been found.
            JobComponent jobForClear = store.getComponent(ref, JobComponent.getComponentType());
            if (jobForClear != null) {
                jobForClear.workAvailable = true;
            }
            DebugLog.info(DebugCategory.MINER_JOB,
                    "[SeekNextMineBlock] [%s] Claimed block at %s -- navigating.", npcId, blockToMine);
        });

        return true;
    }

    private static void initialiseMineOriginIfNeeded(@Nonnull WorkStationComponent workStation,
            @Nullable Vector3i workStationPosition, @Nonnull String npcId) {
        if (workStation.mineOrigin == null && workStationPosition != null) {
            workStation.mineOrigin = new Vector3i(
                    workStationPosition.x,
                    workStationPosition.y,
                    workStationPosition.z + workStation.mineOffsetZ);
            DebugLog.info(DebugCategory.MINER_JOB,
                    "[SeekNextMineBlock] [%s] Mine origin initialised at %s.", npcId, workStation.mineOrigin);
        }
    }

    /**
     * Scans the shaft volume top-down, left-to-right, returning the first solid
     * unclaimed block.
     */
    @Nullable
    private static Vector3i findNextAvailableMineBlock(@Nonnull WorkStationComponent workStation,
            @Nonnull World world, @Nonnull String npcId) {
        Vector3i origin = workStation.mineOrigin;
        if (origin == null) {
            DebugLog.warning(DebugCategory.MINER_JOB,
                    "[SeekNextMineBlock] [%s] Mine origin is null -- workstation may not have been initialised.", npcId);
            return null;
        }
        int size = workStation.mineSize;
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();

        int airCount = 0;
        int claimedCount = 0;
        for (int dy = 0; dy < size; dy++) {
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    int x = origin.x + dx;
                    int y = origin.y - dy;
                    int z = origin.z + dz;

                    if (world.getBlock(x, y, z) == 0) {
                        airCount++;
                        continue;
                    }
                    Ref<ChunkStore> blockEntity = BlockModule.getBlockEntity(world, x, y, z);
                    if (blockEntity != null
                            && chunkStore.getComponent(blockEntity, ClaimedBlockComponent.getComponentType()) != null) {
                        claimedCount++;
                        DebugLog.fine(DebugCategory.MINER_JOB,
                                "[SeekNextMineBlock] [%s] Block at (%d,%d,%d) is claimed -- skipping.",
                                npcId, x, y, z);
                        continue;
                    }
                    DebugLog.fine(DebugCategory.MINER_JOB,
                            "[SeekNextMineBlock] [%s] Scan complete: %d air, %d claimed -- returning (%d,%d,%d).",
                            npcId, airCount, claimedCount, x, y, z);
                    return new Vector3i(x, y, z);
                }
            }
        }
        DebugLog.fine(DebugCategory.MINER_JOB,
                "[SeekNextMineBlock] [%s] Scan complete: %d air, %d claimed -- shaft exhausted.",
                npcId, airCount, claimedCount);
        return null;
    }
}
