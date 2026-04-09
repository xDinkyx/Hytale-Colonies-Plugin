package com.hytalecolonies.npc.actions.constructor;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ConstructorUtil;
import com.hytalecolonies.utils.WorkStationUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Places the correct prefab block at the colonist's job-target position (currently a stub -- block is spawned from thin air).
 * Sets {@link JobComponent#blockPlacedNotification} so the constructor working system can advance.
 */
public class ActionPlaceConstructionBlock extends ActionBase {

    private static final String EMPTY_BLOCK_KEY = "Empty";

    public ActionPlaceConstructionBlock(@Nonnull BuilderActionPlaceConstructionBlock builder,
                                        @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           @Nullable InfoProvider sensorInfo, double dt,
                           @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);
        String npcId = DebugLog.npcId(ref, store);

        JobTargetComponent target = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (target == null || target.targetPosition == null) {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                    "[PlaceConstructionBlock] [%s] No job target -- skipping.", npcId);
            return true;
        }

        WorkStationComponent ws = WorkStationUtil.resolve(store, ref);
        if (ws == null) {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                    "[PlaceConstructionBlock] [%s] No workstation -- skipping.", npcId);
            return true;
        }

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                    "[PlaceConstructionBlock] [%s] No JobComponent -- skipping.", npcId);
            return true;
        }

        World world = store.getExternalData().getWorld();
        Vector3i pos = target.targetPosition;
        final int wx = pos.x;
        final int wy = pos.y;
        final int wz = pos.z;

        BlockSelection prefab = ConstructorUtil.loadPrefab(ws, world);
        if (prefab == null) {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                    "[PlaceConstructionBlock] [%s] Could not load prefab -- skipping.", npcId);
            return true;
        }

        String blockKey = ConstructorUtil.getDesiredBlockKey(ws, prefab, wx, wy, wz);
        if (blockKey == null || blockKey.isEmpty() || EMPTY_BLOCK_KEY.equals(blockKey)) {
            DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB,
                    "[PlaceConstructionBlock] [%s] Desired block at %d,%d,%d is air/empty -- skipping place.",
                    npcId, wx, wy, wz);
            // Still notify so ECS can advance past this position.
            job.blockPlacedNotification = true;
            return true;
        }

        final int blockId = BlockType.getAssetMap().getIndex(blockKey);

        world.execute(() -> {
            WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(wx, wz));
            if (chunk == null) {
                DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                        "[PlaceConstructionBlock] Chunk not in memory at %d,%d -- cannot place.", wx, wz);
                return;
            }
            BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
            if (blockType == null) {
                DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                        "[PlaceConstructionBlock] Unknown block id %d for key '%s'.", blockId, blockKey);
                return;
            }
            chunk.setBlock(wx, wy, wz, blockId, blockType, 0, 0, 0);
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                    "[PlaceConstructionBlock] Placed '%s' at %d,%d,%d.", blockKey, wx, wy, wz);
        });

        if (!job.blockPlacedNotification) {
            job.blockPlacedNotification = true;
            DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB,
                    "[PlaceConstructionBlock] [%s] Notification flag set.", npcId);
        }

        return true;
    }
}
