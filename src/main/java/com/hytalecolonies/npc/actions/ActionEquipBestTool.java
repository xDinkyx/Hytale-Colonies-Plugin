package com.hytalecolonies.npc.actions;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.utils.ColonistToolUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime action that equips the best available tool from the NPC's inventory for
 * a given gather type, delegating all inventory logic to {@link ColonistToolUtil}.
 *
 * <p>Constructed by {@link BuilderActionEquipBestTool} when an NPC role is built.
 */
public class ActionEquipBestTool extends ActionBase {

    private final String gatherType;
    private final int minQuality;

    public ActionEquipBestTool(@Nonnull BuilderActionEquipBestTool builder, @Nonnull BuilderSupport support) {
        super(builder);
        this.gatherType = builder.getGatherType(support);
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

        LivingEntity entity = (LivingEntity) EntityUtils.getEntity(ref, store);
        if (entity == null) return false;

        Inventory inventory = entity.getInventory();
        if (inventory == null) return false;

        return ColonistToolUtil.equipBestToolForGatherType(inventory, gatherType, minQuality, ref, store);
    }
}
