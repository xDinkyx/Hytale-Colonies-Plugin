package com.hytalecolonies.npc.actions;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.util.MathUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
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
 * Runtime action that applies block damage to the block at the position
 * provided by the active sensor.
 *
 * <p>On each tick the entity's held tool and the target block position (from
 * {@link InfoProvider#getPositionProvider()}) are passed to
 * {@link BlockHarvestUtils#performBlockDamage} with a configurable damage scale.
 * Returns {@code true} when the block finally breaks.
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

        return BlockHarvestUtils.performBlockDamage(
                entity, ref,
                targetPos,
                heldItem, tool, null, false,
                damageScale, 0,
                chunkRef,
                store,
                world.getChunkStore().getStore());
    }
}
