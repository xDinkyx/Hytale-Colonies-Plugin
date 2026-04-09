package com.hytalecolonies.npc.actions.miner;

import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Increments {@link MinerJobComponent#blocksMinedThisRun} by one. */
public class ActionIncrementBlocksMined extends ActionBase {

    public ActionIncrementBlocksMined(@Nonnull BuilderActionIncrementBlocksMined builder,
                                       @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           @Nullable InfoProvider sensorInfo, double dt,
                           @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        String npcId = DebugLog.npcId(ref, store);

        DebugLog.fine(DebugCategory.MINER_JOB, "[IncrementBlocksMined] [%s] Action started.", npcId);

        MinerJobComponent minerJob = store.getComponent(ref, MinerJobComponent.getComponentType());
        if (minerJob != null) {
            minerJob.blocksMinedThisRun++;
            DebugLog.info(DebugCategory.MINER_JOB,
                    "[IncrementBlocksMined] [%s] Blocks mined this run: %d.", npcId, minerJob.blocksMinedThisRun);
        } else {
            DebugLog.warning(DebugCategory.MINER_JOB,
                    "[IncrementBlocksMined] [%s] No MinerJobComponent found -- cannot increment.", npcId);
        }

        return true;
    }
}
