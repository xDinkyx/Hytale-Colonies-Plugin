package com.hytalecolonies.npc.actions.constructor;

import com.hytalecolonies.ConstructionOrderStore;
import com.hytalecolonies.components.jobs.ConstructorWorkStationComponent;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.npc.actions.common.ActionSeekNextBlockBase;
import com.hytalecolonies.utils.ConstructorUtil;
import com.hytalecolonies.utils.WorkStationUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Scans for the next block that needs to be cleared (prefab expects air but world is solid),
 * claims it, and sets the NPC nav target toward it.
 *
 * <p>Sets {@code workAvailable = false} when no clearing targets remain, allowing the
 * JSON {@code NoWorkAvailable} sensor to react and transition to the construction phase.
 */
public class ActionSeekNextClearingBlock extends ActionSeekNextBlockBase {

    public ActionSeekNextClearingBlock(@Nonnull BuilderActionSeekNextClearingBlock builder,
                                       @Nonnull BuilderSupport support) {
        super(builder, support);
    }

    @Override
    @Nullable
    protected Vector3i findNextBlock(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref,
                                     @Nonnull World world, @Nonnull String npcId) {
        if (WorkStationUtil.getConstructorWorkStation(store, ref) == null) return null;
        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        ConstructionOrderStore.Entry order = job != null
                ? WorkStationUtil.getConstructionOrderForWorkstation(world, job.getWorkStationBlockPosition())
                : null;
        BlockSelection prefab = ConstructorUtil.loadPrefab(order);
        if (prefab == null) return null;
        return ConstructorUtil.findNextClearingTarget(order, world, prefab);
    }

    @Override
    protected String getClaimLabel() {
        return "Clear";
    }
}
