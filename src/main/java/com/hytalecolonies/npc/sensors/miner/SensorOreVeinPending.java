package com.hytalecolonies.npc.sensors.miner;

import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Fires when {@link MinerJobComponent#oreVeinQueue} is non-empty. */
public class SensorOreVeinPending extends SensorBase {

    public SensorOreVeinPending(@Nonnull BuilderSensorOreVeinPending builder,
                                @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           double dt, @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, role, dt, store))
            return false;

        MinerJobComponent minerJob = store.getComponent(ref, MinerJobComponent.getComponentType());
        boolean result = minerJob != null && !minerJob.oreVeinQueue.isEmpty();
        DebugLog.fine(DebugCategory.MINER_JOB,
                "[SensorOreVeinPending] [%s] result=%s queueSize=%d.",
                DebugLog.npcId(ref, store), result,
                minerJob != null ? minerJob.oreVeinQueue.size() : 0);
        return result;
    }

    @Override
    public @Nullable InfoProvider getSensorInfo() {
        return null;
    }
}
