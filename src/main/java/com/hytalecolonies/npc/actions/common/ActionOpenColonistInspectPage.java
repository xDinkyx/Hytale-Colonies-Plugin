package com.hytalecolonies.npc.actions.common;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.ui.ColonistInspectPage;


/** Opens the colonist inspect page for the interacting player. */
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
        if (playerRefComp == null)
            return false;

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null)
            return false;

        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null)
            return false;
        UUID colonistUuid = uuidComp.getUuid();

        DebugLog.info(DebugCategory.COLONIST_LIFECYCLE,
                      "[OpenColonistInspectPage] Player '%s' opened inspect page for colonist %s.",
                      playerRefComp.getUsername(),
                      colonistUuid);

        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null)
            return false;

        // Open page with two ContainerWindows so the client can perform native drag-and-drop:
        //   Window 1 (InventorySectionId 1) = NPC tool slots
        //   Window 2 (InventorySectionId 2) = NPC storage
        // WindowManager auto-sends UpdateWindow when containers change, keeping grids in sync.
        player.getPageManager().openCustomPageWithWindows(playerRef,
                                                          store,
                                                          new ColonistInspectPage(playerRefComp, colonistUuid),
                                                          new ContainerWindow(npc.getInventory().getHotbar()),
                                                          new ContainerWindow(npc.getInventory().getStorage()));
        return true;
    }
}
