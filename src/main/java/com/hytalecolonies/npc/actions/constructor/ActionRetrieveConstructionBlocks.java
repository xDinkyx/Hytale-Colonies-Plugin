package com.hytalecolonies.npc.actions.constructor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.components.jobs.ConstructorJobComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;

/**
 * Stub action: pretends to retrieve construction materials from the workstation. Sets {@link ConstructorJobComponent#itemsRetrievedNotification}; no actual
 * inventory transfer yet.
 */
public class ActionRetrieveConstructionBlocks extends ActionBase
{
    public ActionRetrieveConstructionBlocks(@Nonnull BuilderActionRetrieveConstructionBlocks builder, @Nonnull BuilderSupport support)
    {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, @Nullable InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store)
    {
        super.execute(ref, role, sensorInfo, dt, store);

        ConstructorJobComponent constructorJob = store.getComponent(ref, ConstructorJobComponent.getComponentType());
        if (constructorJob == null)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                             "[RetrieveConstructionBlocks] [%s] No ConstructorJobComponent -- skipping.",
                             DebugLog.npcId(ref, store));
            return true;
        }

        if (!constructorJob.itemsRetrievedNotification)
        {
            constructorJob.itemsRetrievedNotification = true;
            DebugLog.fine(DebugCategory.CONSTRUCTOR_JOB, "[RetrieveConstructionBlocks] [%s] Flag set (stub).", DebugLog.npcId(ref, store));
        }

        return true;
    }
}
