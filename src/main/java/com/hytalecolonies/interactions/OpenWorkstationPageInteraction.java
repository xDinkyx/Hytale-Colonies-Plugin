package com.hytalecolonies.interactions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hytalecolonies.ui.WorkstationInspectPage;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Opens the {@link WorkstationInspectPage} for the player who interacts with the workstation block. */
public class OpenWorkstationPageInteraction extends SimpleBlockInteraction {

    public static final BuilderCodec<OpenWorkstationPageInteraction> CODEC = BuilderCodec.builder(
                    OpenWorkstationPageInteraction.class, OpenWorkstationPageInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Opens the workstation management page for the interacting player.")
            .build();

    public OpenWorkstationPageInteraction() {}

    @Override
    protected void interactWithBlock(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull Vector3i blockPos,
            @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        commandBuffer.run(store -> {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRefComp = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || playerRefComp == null) return;
            player.getPageManager().openCustomPage(ref, store, new WorkstationInspectPage(playerRefComp, blockPos));
        });
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull World world,
            @Nonnull Vector3i blockPos) {
        // No client-side simulation for UI pages.
    }
}
