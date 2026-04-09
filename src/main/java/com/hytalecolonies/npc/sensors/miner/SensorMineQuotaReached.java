package com.hytalecolonies.npc.sensors.miner;

import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.WorkStationUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;

/**
 * Runtime sensor -- fires when {@link MinerJobComponent#blocksMinedThisRun}
 * is at or above {@link WorkStationComponent#blocksPerRun}.
 */
public class SensorMineQuotaReached extends SensorBase {

    public SensorMineQuotaReached(@Nonnull BuilderSensorMineQuotaReached builder,
                                   @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           double dt, @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, role, dt, store)) {
            return false;
        }

        MinerJobComponent minerJob = store.getComponent(ref, MinerJobComponent.getComponentType());
        if (minerJob == null) {
            return false;
        }

        WorkStationComponent workStation = WorkStationUtil.resolve(store, ref);
        if (workStation == null) {
            DebugLog.fine(DebugCategory.MINER_JOB,
                    "[SensorMineQuotaReached] [%s] WorkStation not found -- returning false.",
                    DebugLog.npcId(ref, store));
            return false;
        }

        boolean quotaReached = minerJob.blocksMinedThisRun >= workStation.blocksPerRun;
        DebugLog.fine(DebugCategory.MINER_JOB,
                "[SensorMineQuotaReached] [%s] mined=%d quota=%d result=%b.",
                DebugLog.npcId(ref, store), minerJob.blocksMinedThisRun, workStation.blocksPerRun, quotaReached);
        return quotaReached;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }
}
