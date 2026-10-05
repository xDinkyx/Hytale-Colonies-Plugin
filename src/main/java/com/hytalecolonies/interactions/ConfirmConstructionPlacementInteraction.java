package com.hytalecolonies.interactions;

import java.util.UUID;

import javax.annotation.Nonnull;

import org.joml.Vector3i;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.ConstructionOrderQueue;
import com.hytalecolonies.ConstructionOrderStore;
import com.hytalecolonies.ConstructionPlacementSession;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ConstructionPlacementUtil;

/** Finalizes the player's positioned placement ghost into a real {@link ConstructionOrderStore.Entry}, queued for a Constructor workstation to pick up. */
public class ConfirmConstructionPlacementInteraction extends SimpleInstantInteraction
{
    private static final Message MSG_NOTHING_TO_CONFIRM =
            Message.translation("server.items.Tool_Colony_Constructor_PlacePrefab.error.nothingToConfirm");
    private static final Message MSG_ORDER_QUEUED = Message.translation("server.items.Tool_Colony_Constructor_PlacePrefab.orderQueued");

    public static final BuilderCodec<ConfirmConstructionPlacementInteraction> CODEC =
            BuilderCodec.builder(ConfirmConstructionPlacementInteraction.class, ConfirmConstructionPlacementInteraction::new, SimpleInstantInteraction.CODEC)
                    .documentation("Confirms the pending construction placement ghost into a queued build order.")
                    .build();

    public ConfirmConstructionPlacementInteraction() {}

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler)
    {
        Ref<EntityStore> ref = context.getEntity();
        context.getCommandBuffer().run(store -> {
            PlayerRef playerRefComp = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRefComp == null)
                return;

            UUID                          uuid    = playerRefComp.getUuid();
            ConstructionPlacementSession session = ConstructionPlacementSession.get(uuid);
            if (session.prefabId == null || session.position == null)
            {
                playerRefComp.sendMessage(MSG_NOTHING_TO_CONFIRM);
                return;
            }

            ConstructionPlacementUtil.clearGhost(session, store);

            ConstructionOrderStore.Entry entry = new ConstructionOrderStore.Entry(UUID.randomUUID(), session.prefabId, new Vector3i(session.position));
            entry.rotationDegrees              = session.rotationDegrees;
            ConstructionOrderStore.get().add(entry);
            ConstructionOrderQueue.get().enqueue(entry.id);
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                          "[ConstructionPlacement] Confirmed order %s for '%s' at %s (rotation=%d).",
                          entry.id,
                          playerRefComp.getUsername(),
                          session.position,
                          session.rotationDegrees);

            ConstructionPlacementSession.clear(uuid);
            playerRefComp.sendMessage(MSG_ORDER_QUEUED);
        });
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler)
    {
        // Creating a construction order is server-authoritative only -- no client-side prediction.
    }
}
