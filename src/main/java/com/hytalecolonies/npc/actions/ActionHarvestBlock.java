package com.hytalecolonies.npc.actions;

import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthChunk;
import com.hypixel.hytale.server.core.modules.blockhealth.BlockHealthModule;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.IPositionProvider;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies block damage each tick to the block at the sensor position using a configurable scale.
 * Returns {@code true} when the block breaks.
 *
 * <p>Constructed by {@link BuilderActionHarvestBlock}.
 */
public class ActionHarvestBlock extends ActionBase {

    private final float damageScale;
    private final Vector3d targetVec = new Vector3d();

    public ActionHarvestBlock(@Nonnull BuilderActionHarvestBlock builder,
                              @Nonnull BuilderSupport support) {
        super(builder);
        this.damageScale = builder.getDamageScale(support);
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

        DebugLog.fine(DebugCategory.JOB_SYSTEM, "[HarvestBlock] [%s] Action started.", npcId);

        if (sensorInfo == null || !sensorInfo.hasPosition()) return false;

        IPositionProvider pos = sensorInfo.getPositionProvider();
        if (pos == null) return false;

        if (!pos.providePosition(targetVec)) return false;

        Vector3i targetPos = new Vector3i(
                MathUtil.floor(targetVec.x),
                MathUtil.floor(targetVec.y),
                MathUtil.floor(targetVec.z));

        LivingEntity entity = (LivingEntity) EntityUtils.getEntity(ref, store);
        if (entity == null) return false;

        Inventory inventory = entity.getInventory();
        if (inventory == null) return false;

        World world = store.getExternalData().getWorld();
        long chunkIdx = ChunkUtil.indexChunkFromBlock(targetPos.x, targetPos.z);
        Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(chunkIdx);
        if (chunkRef == null || !chunkRef.isValid()) return false;

        ItemStack heldItem = inventory.getItemInHand();
        ItemTool tool = (heldItem != null && heldItem.getItem() != null)
                ? heldItem.getItem().getTool()
                : null;

        String heldId = (heldItem != null && heldItem.getItem() != null) ? heldItem.getItem().getId() : "null";
        String toolSpecs = (tool != null && tool.getSpecs() != null)
                ? java.util.Arrays.stream(tool.getSpecs())
                        .map(s -> s.getGatherType() + "q" + s.getQuality() + "p" + s.getPower())
                        .collect(java.util.stream.Collectors.joining(","))
                : "null";

        // Diagnose what the block actually is at the target position.
        Ref<ChunkStore> chunkRef2 = world.getChunkStore().getChunkReference(chunkIdx);
        if (chunkRef2 != null && chunkRef2.isValid()) {
            WorldChunk worldChunk = world.getChunkStore().getStore().getComponent(chunkRef2, WorldChunk.getComponentType());
            BlockHealthChunk bhc = world.getChunkStore().getStore().getComponent(chunkRef2, BlockHealthModule.get().getBlockHealthChunkComponentType());
            if (worldChunk != null) {
                BlockType bt = worldChunk.getBlockType(targetPos.x, targetPos.y, targetPos.z);
                BlockGathering bg = bt != null ? bt.getGathering() : null;
                float currentHealth = bhc != null ? bhc.getBlockHealth(targetPos) : -1f;
                boolean isSoft = bg != null && bg.isSoft();
                String gatherType = (bg != null && bg.getBreaking() != null) ? bg.getBreaking().getGatherType() : "null";
                int reqQuality = (bg != null && bg.getBreaking() != null) ? bg.getBreaking().getQuality() : -1;
                DebugLog.fine(DebugCategory.JOB_SYSTEM,
                        "[HarvestBlock] [%s] target=%s blockType=%s gatherType=%s reqQuality=%d isSoft=%b health=%.3f heldItem=%s toolSpecs=[%s].",
                        npcId, targetPos, bt != null ? bt.getId() : "null", gatherType, reqQuality, isSoft, currentHealth, heldId, toolSpecs);
            }
        }

        boolean broke = BlockHarvestUtils.performBlockDamage(
                entity, ref,
                targetPos,
                heldItem, tool, null, false,
                damageScale, 0,
                chunkRef,
                store,
                world.getChunkStore().getStore());
        if (broke) {
            DebugLog.fine(DebugCategory.JOB_SYSTEM, "[HarvestBlock] [%s] Block broke at %s.", npcId, targetPos);
        } else {
            DebugLog.fine(DebugCategory.JOB_SYSTEM, "[HarvestBlock] [%s] performBlockDamage returned false for %s.", npcId, targetPos);
        }
        // Always return true so the blocking action list advances to the Timeout delay.
        // Break detection is handled by SensorJobTargetBroken, not by this return value.
        return true;
    }
}
