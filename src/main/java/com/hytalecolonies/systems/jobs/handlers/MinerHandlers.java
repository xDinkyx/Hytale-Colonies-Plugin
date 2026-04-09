package com.hytalecolonies.systems.jobs.handlers;

import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.world.ClaimedBlockComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;

/** Utility methods for miner colonist job logic. */
public final class MinerHandlers {

    private MinerHandlers() {}

    // ===== Private helpers =====

    /** Scans the mine shaft top-down and returns the first solid unclaimed block, or {@code null}. */
    @Nullable
    public static Vector3i findNextMineBlock(WorkStationComponent workStation, World world) {
        Vector3i origin = workStation.mineOrigin;
        if (origin == null) return null;
        int size = workStation.mineSize;
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        int checkedTotal = 0;
        int nonAir = 0;
        int claimed = 0;
        for (int dy = 0; dy < size; dy++) {
            for (int dx = 0; dx < size; dx++) {
                for (int dz = 0; dz < size; dz++) {
                    int x = origin.x + dx;
                    int y = origin.y - dy;
                    int z = origin.z + dz;
                    checkedTotal++;
                    int blockId = world.getBlock(x, y, z);
                    if (blockId == 0) continue;
                    nonAir++;
                    DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Scan] Non-air block id=%d at (%d,%d,%d).", blockId, x, y, z);
                    Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, x, y, z);
                    if (blockRef != null && chunkStore.getComponent(blockRef, ClaimedBlockComponent.getComponentType()) != null) {
                        claimed++;
                        DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Scan] Block at (%d,%d,%d) is claimed -- skipping.", x, y, z);
                        continue;
                    }
                    DebugLog.info(DebugCategory.MINER_JOB, "[MinerJob:Scan] Returning block id=%d at (%d,%d,%d).", blockId, x, y, z);
                    return new Vector3i(x, y, z);
                }
            }
        }
        DebugLog.info(DebugCategory.MINER_JOB,
                "[MinerJob:Scan] Done -- checked=%d nonAir=%d claimed=%d -> no eligible block. origin=%s size=%d.",
                checkedTotal, nonAir, claimed, origin, size);
        return null;
    }

    public static Vector3d blockCenter(Vector3i block) {
        return new Vector3d(block.x + 0.5, block.y, block.z + 0.5);
    }
}
