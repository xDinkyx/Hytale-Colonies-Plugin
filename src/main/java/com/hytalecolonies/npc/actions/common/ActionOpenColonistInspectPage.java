package com.hytalecolonies.npc.actions.common;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.utils.ColonistInventoryUtil;

/**
 * Opens the colonist's hotbar and storage inventory for the interacting player using the native bench/container page. Must run inside an InteractionInstruction
 * HasInteracted block so that {@link com.hypixel.hytale.server.npc.role.support.StateSupport#getInteractionIterationTarget()} is set.
 */
public class ActionOpenColonistInspectPage extends ActionBase
{
    public ActionOpenColonistInspectPage(@Nonnull BuilderActionOpenColonistInspectPage builder, @Nonnull BuilderSupport support)
    {
        super(builder);
    }

    @Override
    public boolean
    canExecute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, @Nullable InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store)
    {
        return super.canExecute(ref, role, sensorInfo, dt, store) && role.getStateSupport().getInteractionIterationTarget() != null;
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, @Nullable InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store)
    {
        super.execute(ref, role, sensorInfo, dt, store);

        Ref<EntityStore> playerRef = role.getStateSupport().getInteractionIterationTarget();
        if (playerRef == null)
            return false;

        PlayerRef playerRefComp = store.getComponent(playerRef, PlayerRef.getComponentType());
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null || playerRefComp == null)
            return false;

        if (store.getComponent(ref, NPCEntity.getComponentType()) == null)
            return false;

        return ColonistInventoryUtil.openForPlayer(ref, playerRef, player, store);
    }
}
