package com.hytalecolonies.npc.sensors.common;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.components.jobs.JobTaskComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;

/**
 * Fires when the colonist has a {@link JobTaskComponent} with at least one item requirement.
 */
public class SensorJobHasTaskItems extends SensorBase
{
    public SensorJobHasTaskItems(@Nonnull BuilderSensorJobHasTaskItems builder, @Nonnull BuilderSupport support)
    {
        super(builder);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, double dt, @Nonnull Store<EntityStore> store)
    {
        if (!super.matches(ref, role, dt, store))
            return false;

        JobTaskComponent task = store.getComponent(ref, JobTaskComponent.getComponentType());
        boolean result = task != null && task.requiredItems.length > 0;
        DebugLog.fine(DebugCategory.COLONIST_DELIVERY, "[SensorJobHasTaskItems] [%s] result=%b.", DebugLog.npcId(ref, store), result);
        return result;
    }

    @Override
    public InfoProvider getSensorInfo()
    {
        return null;
    }
}
