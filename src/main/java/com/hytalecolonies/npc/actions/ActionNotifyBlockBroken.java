package com.hytalecolonies.npc.actions;

import com.hytalecolonies.components.jobs.JobComponent;
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

/**
 * JSON->ECS notification bridge for the miner. Sets {@link JobComponent#blockBrokenNotification};
 * no game-logic. {@link com.hytalecolonies.systems.jobs.MinerWorkingSystem} reads and clears the flag.
 */
public class ActionNotifyBlockBroken extends ActionBase {

    public ActionNotifyBlockBroken(@Nonnull BuilderActionNotifyBlockBroken builder,
                                    @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           @Nullable InfoProvider sensorInfo, double dt,
                           @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) {
            DebugLog.fine(DebugCategory.MINER_JOB,
                    "[NotifyBlockBroken] [%s] No JobComponent -- skipping.",
                    DebugLog.npcId(ref, store));
            return true;
        }

        if (!job.blockBrokenNotification) {
            job.blockBrokenNotification = true;
            DebugLog.fine(DebugCategory.MINER_JOB, "[NotifyBlockBroken] [%s] Flag set.", DebugLog.npcId(ref, store));
        }

        return true;
    }
}
