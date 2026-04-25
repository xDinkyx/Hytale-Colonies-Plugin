package com.hytalecolonies.listeners;

import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.ui.ColonistInspectPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Opens the colonist inspect page when the player presses the Use key (F)
 * while looking at a colonist NPC.
 */
public class ColonistInspectListener {

    public void register(@Nonnull EventRegistry eventRegistry) {
        eventRegistry.registerGlobal(PlayerInteractEvent.class, this::onPlayerInteract);
    }

    private void onPlayerInteract(@Nonnull PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        if (event.getActionType() != InteractionType.Use) return;

        Ref<EntityStore> targetRef = event.getTargetRef();
        if (targetRef == null || !targetRef.isValid()) return;

        Player player = event.getPlayer();
        Ref<EntityStore> playerRef = player.getReference();
        Store<EntityStore> store = playerRef.getStore();

        ColonistComponent colonist = store.getComponent(targetRef, ColonistComponent.getComponentType());
        if (colonist == null) return;

        NPCEntity npc = store.getComponent(targetRef, NPCEntity.getComponentType());
        if (npc == null) return;

        UUIDComponent uuidComp = store.getComponent(targetRef, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        UUID colonistUuid = uuidComp.getUuid();

        PlayerRef playerRefComp = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComp == null) return;

        DebugLog.info(DebugCategory.COLONIST_LIFECYCLE,
                "[ColonistInspect] Player '%s' opened inspect page for colonist '%s' (%s).",
                playerRefComp.getUsername(), colonist.getColonistName(), colonistUuid);

        event.setCancelled(true);

        player.getPageManager().openCustomPage(playerRef, store,
                new ColonistInspectPage(playerRefComp, colonistUuid));
    }
}
