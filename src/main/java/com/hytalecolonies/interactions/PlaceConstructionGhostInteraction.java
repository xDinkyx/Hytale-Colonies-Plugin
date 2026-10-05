package com.hytalecolonies.interactions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3i;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.ConstructionPlacementSession;
import com.hytalecolonies.utils.ConstructionPlacementUtil;

/** Positions (or repositions) the construction placement ghost at the targeted block, facing the player's current body yaw snapped to 90 degrees. */
public class PlaceConstructionGhostInteraction extends SimpleBlockInteraction
{
    private static final Message MSG_NO_PREFAB_SELECTED =
            Message.translation("server.items.Tool_Colony_Constructor_PlacePrefab.error.noPrefabSelected");

    public static final BuilderCodec<PlaceConstructionGhostInteraction> CODEC =
            BuilderCodec.builder(PlaceConstructionGhostInteraction.class, PlaceConstructionGhostInteraction::new, SimpleBlockInteraction.CODEC)
                    .documentation("Positions the pending construction order's preview ghost at the targeted block.")
                    .build();

    public PlaceConstructionGhostInteraction() {}

    @Override
    protected void interactWithBlock(@Nonnull World world,
                                     @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                     @Nonnull InteractionType            type,
                                     @Nonnull InteractionContext         context,
                                     @Nullable ItemStack                 itemInHand,
                                     @Nonnull Vector3i                   blockPos,
                                     @Nonnull CooldownHandler            cooldownHandler)
    {
        Ref<EntityStore> ref = context.getEntity();
        commandBuffer.run(store -> {
            PlayerRef playerRefComp = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRefComp == null)
                return;

            ConstructionPlacementSession session = ConstructionPlacementSession.get(playerRefComp.getUuid());
            if (session.prefabId == null)
            {
                playerRefComp.sendMessage(MSG_NO_PREFAB_SELECTED);
                return;
            }

            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            session.rotationDegrees = transform != null ? ConstructionPlacementUtil.snapYawToRotationDegrees(transform.getRotation().yaw()) : 0;
            session.position        = new Vector3i(blockPos);
            ConstructionPlacementUtil.updateGhost(session, store);
        });
    }

    @Override
    protected void simulateInteractWithBlock(@Nonnull InteractionType    type,
                                             @Nonnull InteractionContext context,
                                             @Nullable ItemStack         itemInHand,
                                             @Nonnull World              world,
                                             @Nonnull Vector3i           blockPos)
    {
        // No client-side simulation -- the ghost is a server-owned hologram entity.
    }
}
