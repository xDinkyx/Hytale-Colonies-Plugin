package com.hytalecolonies.npc.actions.common;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.components.jobs.JobRunCounterComponent;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;


/** Resets {@link JobRunCounterComponent#count} to zero. */
public class ActionResetJobCounter extends ActionBase
{
    public ActionResetJobCounter(@Nonnull BuilderActionResetJobCounter builder, @Nonnull BuilderSupport support)
    {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, @Nullable InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store)
    {
        super.execute(ref, role, sensorInfo, dt, store);

        JobRunCounterComponent counter = store.getComponent(ref, JobRunCounterComponent.getComponentType());
        if (counter != null)
            counter.count = 0;
        else
            store.addComponent(ref, JobRunCounterComponent.getComponentType(), new JobRunCounterComponent(0));
        return true;
    }
}
