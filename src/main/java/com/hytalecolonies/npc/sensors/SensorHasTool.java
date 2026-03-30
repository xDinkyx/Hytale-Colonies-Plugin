package com.hytalecolonies.npc.sensors;

import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ColonistToolUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;

/**
 * Runtime sensor -- fires when the NPC inventory contains a tool that satisfies
 * {@link #gatherType} at any quality tier.
 */
public class SensorHasTool extends SensorBase {

    private final String gatherType;

    public SensorHasTool(@Nonnull BuilderSensorHasTool builder, @Nonnull BuilderSupport support) {
        super(builder);
        this.gatherType = builder.getGatherType(support);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           double dt, @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, role, dt, store)) {
            return false;
        }

        LivingEntity entity = (LivingEntity) EntityUtils.getEntity(ref, store);
        if (entity == null || entity.getInventory() == null) {
            DebugLog.fine(DebugCategory.JOB_SYSTEM,
                    "[SensorHasTool] Entity or inventory is null -- returning false.");
            return false;
        }

        boolean hasTool = ColonistToolUtil.hasToolForGatherType(entity.getInventory(), gatherType, 0);
        DebugLog.fine(DebugCategory.JOB_SYSTEM,
                "[SensorHasTool] gather=%s result=%b.", gatherType, hasTool);
        return hasTool;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }
}
