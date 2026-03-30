package com.hytalecolonies.npc.actions;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.world.ClaimedBlockComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
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
 * atomically via {@code world.execute()}, sets it as the active job target, and
 * dispatches navigation toward it.
 *
 * <p>If the shaft has no solid unclaimed blocks the action completes without setting
 * a target. {@code SensorJobTargetExists} will remain false and the instruction
 * evaluator will retry this action on the next cycle until a block is available.
 *
 * <p>Race safety: the claim is performed inside {@code world.execute()} so that two
 * miners finding the same block in the same tick serialize here — the first succeeds,
 * the second backs off.
 */
public class ActionSeekNextMineBlock extends ActionBase {

    public ActionSeekNextMineBlock(@Nonnull BuilderActionSeekNextMineBlock builder,
                                    @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           @Nullable InfoProvider sensorInfo, double dt,
                           @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        DebugLog.fine(DebugCategory.MINER_JOB, "[SeekNextMineBlock] Action fired.");

        WorkStationComponent workStation = WorkStationUtil.resolve(store, ref);
        if (workStation == null) {
            DebugLog.fine(DebugCategory.MINER_JOB, "[SeekNextMineBlock] Workstation not found — skipping.");
            return true;
        }

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) {
            DebugLog.warning(DebugCategory.MINER_JOB, "[SeekNextMineBlock] No JobComponent — cannot resolve workstation position.");
            return true;
        }
        Vector3i workStationPosition = job.getWorkStationBlockPosition();

        initialiseMineOriginIfNeeded(workStation, workStationPosition);

        UUIDComponent uuidComponent = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComponent == null) {
            DebugLog.warning(DebugCategory.MINER_JOB, "[SeekNextMineBlock] No UUIDComponent — cannot claim block.");
            return true;
        }
        UUID colonistUuid = uuidComponent.getUuid();

        World world = store.getExternalData().getWorld();
        Vector3i nextBlock = findNextAvailableMineBlock(workStation, world);

        if (nextBlock == null) {
            DebugLog.fine(DebugCategory.MINER_JOB,
                    "[SeekNextMineBlock] Shaft at origin %s has no solid unclaimed blocks — will retry next cycle.",
                    workStation.mineOrigin);
            return true;
        }

        DebugLog.info(DebugCategory.MINER_JOB,
                "[SeekNextMineBlock] Attempting to claim block at %s.", nextBlock);

        final Vector3i blockToMine = nextBlock;
        world.execute(() -> {
            JobTargetComponent existingTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
            if (existingTarget != null && existingTarget.targetPosition != null) {
                DebugLog.fine(DebugCategory.MINER_JOB,
                        "[SeekNextMineBlock] Already has a job target — skipping claim.");
                return;
            }

            boolean claimed = JobNavigationUtil.claimAndNavigateTo(world, store, ref, colonistUuid, blockToMine, "Mine");
            if (claimed) {
                DebugLog.info(DebugCategory.MINER_JOB,
                        "[SeekNextMineBlock] Claimed block at %s — navigating.", blockToMine);
            } else {
                DebugLog.fine(DebugCategory.MINER_JOB,
                        "[SeekNextMineBlock] Block at %s was already taken — will retry next cycle.", blockToMine);
            }
        });

        return true;
    }

    private static void initialiseMineOriginIfNeeded(@Nonnull WorkStationComponent workStation,
                                                      @Nullable Vector3i workStationPosition) {
        if (workStation.mineOrigin == null && workStationPosition != null) {
            workStation.mineOrigin = new Vector3i(
                    workStationPosition.x,
                    workStationPosition.y,
                    workStationPosition.z + workStation.mineOffsetZ);
            DebugLog.info(DebugCategory.MINER_JOB,
                    "[SeekNextMineBlock] Mine origin initialised at %s.", workStation.mineOrigin);
        }
    }

    /** Scans the shaft volume top-down, left-to-right, returning the first solid unclaimed block. */
    @Nullable
    private static Vector3i findNextAvailableMineBlock(@Nonnull WorkStationComponent workStation,
                                                        @Nonnull World world) {
        Vector3i origin = workStation.mineOrigin;
        if (origin == null) {
            DebugLog.warning(DebugCategory.MINER_JOB, "[SeekNextMineBlock] Mine origin is null — workstation may not have been initialised.");
            return null;
        }
        int size = workStation.mineSize;
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();

        for (int dy = 0; dy < size; dy++) {
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    int x = origin.x + dx;
                    int y = origin.y - dy;
                    int z = origin.z + dz;

                    if (world.getBlock(x, y, z) == 0) {
                        continue;
                    }
                    Ref<ChunkStore> blockEntity = BlockModule.getBlockEntity(world, x, y, z);
                    if (blockEntity != null && chunkStore.getComponent(blockEntity, ClaimedBlockComponent.getComponentType()) != null) {
                        continue;
                    }
                    return new Vector3i(x, y, z);
                }
            }
        }
        return null;
    }
}
