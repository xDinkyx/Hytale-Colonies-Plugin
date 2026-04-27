package com.hytalecolonies.npc.actions.common;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.components.jobs.ConstructorJobComponent;
import com.hytalecolonies.components.jobs.JobType;
import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.components.jobs.WoodsmanJobComponent;
import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.ui.ColonistInfoHud;


/**
 * Opens the colonist's storage using Page.Bench (same as a native chest/container)
 * and shows a ColonistInfoHud overlay with the colonist's name, job, and level.
 * The HUD is cleared via the window's close callback when the player closes the bench.
 */
public class ActionOpenColonistInspectPage extends ActionBase
{

    public ActionOpenColonistInspectPage(@Nonnull BuilderActionOpenColonistInspectPage builder, @Nonnull BuilderSupport support)
    {
        super(builder);
    }

    @Override
    public boolean canExecute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, @Nullable InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store)
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

        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null)
            return false;

        ItemContainer storage = npc.getInventory().getStorage();
        if (storage == null)
            return false;

        DebugLog.info(DebugCategory.COLONIST_LIFECYCLE,
                      "[OpenColonistInspectPage] Player '%s' opened bench for colonist %s.",
                      playerRefComp.getUsername(), colonistUuid);

        // Build and show the colonist info HUD overlay.
        ColonistInfoHud hud = new ColonistInfoHud(playerRefComp);
        hud.setData(
            colonistName(ref, store),
            colonistJob(ref, store),
            "Level " + colonistLevel(ref, store),
            "Colony: " + colonistColony(ref, store)
        );
        player.getHudManager().setCustomHud(playerRefComp, hud);

        // Open the native bench page. setPageWithWindows sends SetPage (writeNoCache)
        // before the OpenWindow packets, so the client receives them in the right order
        // and native drag-and-drop works correctly.
        ContainerWindow storageWindow = new ContainerWindow(storage);
        storageWindow.registerCloseEvent(e -> player.getHudManager().setCustomHud(playerRefComp, null));

        return player.getPageManager().setPageWithWindows(playerRef, store, Page.Bench, true, storageWindow);
    }

    // -------------------------------------------------------------------------
    // Colonist data helpers
    // -------------------------------------------------------------------------

    @Nonnull
    private static String colonistName(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store)
    {
        ColonistComponent c = store.getComponent(ref, ColonistComponent.getComponentType());
        return c != null ? c.getColonistName() : "Colonist";
    }

    @Nonnull
    private static String colonistJob(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store)
    {
        if (store.getComponent(ref, WoodsmanJobComponent.getComponentType())    != null) return JobType.Woodsman.name();
        if (store.getComponent(ref, MinerJobComponent.getComponentType())       != null) return JobType.Miner.name();
        if (store.getComponent(ref, ConstructorJobComponent.getComponentType()) != null) return JobType.Constructor.name();
        return "Unemployed";
    }

    private static int colonistLevel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store)
    {
        ColonistComponent c = store.getComponent(ref, ColonistComponent.getComponentType());
        return c != null ? c.getColonistLevel() : 1;
    }

    @Nonnull
    private static String colonistColony(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store)
    {
        ColonistComponent c = store.getComponent(ref, ColonistComponent.getComponentType());
        return c != null ? c.getColonyId() : "";
    }
}

