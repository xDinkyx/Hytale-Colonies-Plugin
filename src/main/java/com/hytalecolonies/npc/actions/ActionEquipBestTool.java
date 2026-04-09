package com.hytalecolonies.npc.actions;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.IPositionProvider;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ColonistToolUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Equips the best available tool from the NPC's inventory.
 * Uses the configured {@code GatherType}, or auto-detects from the sensor block position.
 *
 * <p>Constructed by {@link BuilderActionEquipBestTool}.
 */
public class ActionEquipBestTool extends ActionBase {

    @Nullable
    private final String gatherType;
    private final int minQuality;
    private final Vector3d targetVec = new Vector3d();

    public ActionEquipBestTool(@Nonnull BuilderActionEquipBestTool builder, @Nonnull BuilderSupport support) {
        super(builder);
        this.gatherType = builder.getGatherType();
        this.minQuality = builder.getMinQuality(support);
    }

    @Override
    public boolean execute(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Role role,
            @Nullable InfoProvider sensorInfo,
            double dt,
            @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        String npcId = DebugLog.npcId(ref, store);

        DebugLog.fine(DebugCategory.JOB_SYSTEM, "[EquipBestTool] [%s] Action started.", npcId);

        LivingEntity entity = (LivingEntity) EntityUtils.getEntity(ref, store);
        if (entity == null) return false;

        Inventory inventory = entity.getInventory();
        if (inventory == null) return false;

        if (gatherType != null) {
            // Explicit mode -- use the configured gather type.
            boolean equipped = ColonistToolUtil.equipBestToolForGatherType(inventory, gatherType, minQuality, ref, store);
            DebugLog.fine(DebugCategory.JOB_SYSTEM, "[EquipBestTool] [%s] Action finished (explicit type=%s, equipped=%b).", npcId, gatherType, equipped);
            return equipped;
        }

        // Auto-detect mode -- resolve block gather type from the sensor position.
        if (sensorInfo == null || !sensorInfo.hasPosition()) return false;
        IPositionProvider pos = sensorInfo.getPositionProvider();
        if (pos == null || !pos.providePosition(targetVec)) return false;

        Vector3i blockPos = new Vector3i(
                MathUtil.floor(targetVec.x),
                MathUtil.floor(targetVec.y),
                MathUtil.floor(targetVec.z));

        World world = store.getExternalData().getWorld();
        long chunkIdx = ChunkUtil.indexChunkFromBlock(blockPos.x, blockPos.z);
        Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(chunkIdx);
        if (chunkRef == null || !chunkRef.isValid()) return false;

        BlockBreakingDropType breaking = ColonistToolUtil.getBreakingConfig(world, chunkRef, blockPos);
        if (breaking == null) {
            DebugLog.fine(DebugCategory.JOB_SYSTEM, "[EquipBestTool] [%s] Action finished (no tool required).", npcId);
            return true; // Block requires no special tool.
        }

        boolean equipped = ColonistToolUtil.equipBestToolForBlock(inventory, breaking, ref, store);
        DebugLog.fine(DebugCategory.JOB_SYSTEM, "[EquipBestTool] [%s] Action finished (auto-detect, equipped=%b).", npcId, equipped);
        return equipped;
    }
}
