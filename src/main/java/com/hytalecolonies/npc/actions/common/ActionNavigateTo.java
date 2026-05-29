package com.hytalecolonies.npc.actions.common;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ColonistLeashUtil;
import com.hytalecolonies.utils.JobNavigationUtil;

/**
 * Dispatches one-shot navigation via {@link com.hytalecolonies.components.npc.MoveToTargetComponent}. Constructed by {@link BuilderActionNavigateTo}.
 *
 * <p>
 * Supported targets:
 * <ul>
 * <li>{@code "Workstation"} -- seeks {@code JobComponent.getWorkStationBlockPosition()} and re-anchors the leash.</li>
 * <li>{@code "JobTarget"} -- seeks {@code JobTargetComponent.targetPosition} (the currently claimed work block).</li>
 * </ul>
 */
public class ActionNavigateTo extends ActionBase
{
    private static final String TARGET_WORKSTATION = "Workstation";
    private static final String TARGET_JOB_TARGET = "JobTarget";

    /**
     * NavTarget stored-position slot index — must match Template_Colonist.json slot 0.
     */
    private static final int NAV_TARGET_SLOT = 0;

    private final String target;

    public ActionNavigateTo(@Nonnull BuilderActionNavigateTo builder, @Nonnull BuilderSupport support)
    {
        super(builder);
        this.target = builder.getTarget(support);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, @Nullable InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store)
    {
        super.execute(ref, role, sensorInfo, dt, store);

        if (TARGET_WORKSTATION.equals(target))
        {
            navigateToWorkstation(ref, role, store);
        }
        else if (TARGET_JOB_TARGET.equals(target))
        {
            navigateToJobTarget(ref, role, store);
        }
        else
        {
            DebugLog.warning(DebugCategory.MOVEMENT, "[NavigateTo] [%s] Unknown target type '%s'.", DebugLog.npcId(ref, store), target);
        }

        return true;
    }

    private void navigateToJobTarget(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, @Nonnull Store<EntityStore> store)
    {
        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jobTarget == null || jobTarget.getTargetPosition() == null)
        {
            DebugLog.fine(DebugCategory.MOVEMENT, "[NavigateTo] [%s] No JobTargetComponent or target position -- cannot navigate.", DebugLog.npcId(ref, store));
            return;
        }
        Vector3i pos = jobTarget.getTargetPosition();
        setNavTarget(role, pos.x + 0.5, (double)pos.y, pos.z + 0.5);
        JobNavigationUtil.dispatchNavigation(store, ref, pos);
    }

    private void navigateToWorkstation(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, @Nonnull Store<EntityStore> store)
    {
        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null)
        {
            DebugLog.fine(DebugCategory.MOVEMENT, "[NavigateTo] [%s] No JobComponent -- cannot navigate to workstation.", DebugLog.npcId(ref, store));
            return;
        }
        Vector3i wsPos = job.getWorkStationBlockPosition();
        if (wsPos == null)
        {
            DebugLog.fine(DebugCategory.MOVEMENT, "[NavigateTo] [%s] No workstation position -- cannot navigate.", DebugLog.npcId(ref, store));
            return;
        }
        // Write directly to NavTarget slot so the Seek sensor sees the correct position
        // in the same instruction tick. PathFindingSystem fires after the NPC
        // instruction
        // pass, so dispatching via MoveToTargetComponent alone would leave slot stale
        // for one tick and briefly navigate toward the old position.
        setNavTarget(role, wsPos.x + 0.5, (double)wsPos.y, wsPos.z + 0.5);
        JobNavigationUtil.dispatchNavigation(store, ref, wsPos);
        ColonistLeashUtil.setLeashToBlockCenter(ref, store, wsPos);
    }

    private void setNavTarget(@Nonnull Role role, double x, double y, double z)
    {
        role.getMarkedEntitySupport().getStoredPosition(NAV_TARGET_SLOT).assign(x, y, z);
    }
}
