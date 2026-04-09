package com.hytalecolonies.npc.actions.common;

import com.hytalecolonies.components.jobs.JobRunCounterComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Increments {@link JobRunCounterComponent#count} by one. */
public class ActionIncrementJobCounter extends ActionBase {

    public ActionIncrementJobCounter(@Nonnull BuilderActionIncrementJobCounter builder,
                                     @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           @Nullable InfoProvider sensorInfo, double dt,
                           @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        JobRunCounterComponent counter = store.getComponent(ref, JobRunCounterComponent.getComponentType());
        if (counter != null)
            counter.count++;
        else
            store.addComponent(ref, JobRunCounterComponent.getComponentType(), new JobRunCounterComponent(1));

        return true;
    }
}
