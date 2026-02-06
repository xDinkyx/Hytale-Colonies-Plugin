package com.colonies.coloniesplugin.interactions;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import org.jspecify.annotations.NonNull;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ActivateJobProviderBlockInteraction extends SimpleBlockInteraction {

    @Override
    protected void interactWithBlock(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull Vector3i targetBlock,
            @Nonnull CooldownHandler cooldownHandler
    ) {
    }

    @Override
    protected void simulateInteractWithBlock(@NonNull InteractionType var1, @NonNull InteractionContext var2, @org.jspecify.annotations.Nullable ItemStack var3, @NonNull World var4, @NonNull Vector3i var5) {

    }
}
