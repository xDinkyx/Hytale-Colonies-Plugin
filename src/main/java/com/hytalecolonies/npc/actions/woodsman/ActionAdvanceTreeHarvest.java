package com.hytalecolonies.npc.actions.woodsman;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.WoodsmanWorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ClaimBlockUtil;
import com.hytalecolonies.utils.ColonistStateUtil;
import com.hytalecolonies.utils.JobNavigationUtil;
import com.hytalecolonies.utils.WoodsmanUtil;
import com.hytalecolonies.utils.WorkStationUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * After the current tree-base block breaks, finds the next adjacent base block
 * and
 * navigates to it, or transitions to {@link JobState#DeliveringItems} when all
 * bases are felled. Also handles the unclaim of the just-broken block.
 */
public class ActionAdvanceTreeHarvest extends ActionBase {

    public ActionAdvanceTreeHarvest(@Nonnull BuilderActionAdvanceTreeHarvest builder,
            @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
            @Nullable InfoProvider sensorInfo, double dt,
            @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);
        String npcId = DebugLog.npcId(ref, store);

        JobTargetComponent target = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (target == null || target.targetPosition == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB,
                    "[AdvanceTreeHarvest] [%s] No job target -- delivering.", npcId);
            JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
            if (job != null)
                ColonistStateUtil.setJobState(ref, store, job, JobState.DeliveringItems);
            return true;
        }

        Vector3i treeBase = target.targetPosition;
        World world = store.getExternalData().getWorld();

        WoodsmanWorkStationComponent workStation = WorkStationUtil.getWoodsmanWorkStation(store, ref);
        Set<String> allowedTreeTypes = workStation != null ? workStation.getAllowedTreeTypes() : null;
        if (allowedTreeTypes == null) {
            DebugLog.warning(DebugCategory.WOODSMAN_JOB,
                    "[AdvanceTreeHarvest] [%s] No workstation -- delivering.", npcId);
        }

        @Nullable
        Vector3i nextBase = allowedTreeTypes != null
                ? WoodsmanUtil.findNextBaseBlock(treeBase, allowedTreeTypes, world)
                : null;

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null)
            return true;

        final Vector3i capturedBase = new Vector3i(treeBase.x, treeBase.y, treeBase.z);

        if (nextBase == null) {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                    "[AdvanceTreeHarvest] [%s] No adjacent base at %s -- delivering.", npcId, capturedBase);
            world.execute(() -> ClaimBlockUtil.unclaimBlock(world, capturedBase));
            target.setTargetPosition(null);
            ColonistStateUtil.setJobState(ref, store, job, JobState.DeliveringItems);
        } else {
            DebugLog.fine(DebugCategory.WOODSMAN_JOB,
                    "[AdvanceTreeHarvest] [%s] Next base at %s -- traveling.", npcId, nextBase);
            world.execute(() -> ClaimBlockUtil.unclaimBlock(world, capturedBase));
            target.setTargetPosition(nextBase);
            JobNavigationUtil.dispatchNavigation(store, ref, nextBase);
            ColonistStateUtil.setJobState(ref, store, job, JobState.TravelingToWorkSite);
        }

        return true;
    }
}
