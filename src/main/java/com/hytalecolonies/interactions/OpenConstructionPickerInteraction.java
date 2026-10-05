package com.hytalecolonies.interactions;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.ui.ConstructorPrefabPage;

/** Opens the constructor prefab picker (custom UI with a live 3D preview). Bound to the constructor tool's "Use" action. */
public class OpenConstructionPickerInteraction extends SimpleInstantInteraction
{
    public static final BuilderCodec<OpenConstructionPickerInteraction> CODEC =
            BuilderCodec.builder(OpenConstructionPickerInteraction.class, OpenConstructionPickerInteraction::new, SimpleInstantInteraction.CODEC)
                    .documentation("Opens the constructor prefab picker for the interacting player.")
                    .build();

    public OpenConstructionPickerInteraction() {}

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler)
    {
        Ref<EntityStore> ref = context.getEntity();
        context.getCommandBuffer().run(store -> {
            Player    player        = store.getComponent(ref, Player.getComponentType());
            PlayerRef playerRefComp = store.getComponent(ref, PlayerRef.getComponentType());
            if (player == null || playerRefComp == null)
                return;
            player.getPageManager().openCustomPage(ref, store, new ConstructorPrefabPage(playerRefComp));
        });
    }

    @Override
    protected void simulateFirstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler)
    {
        // Opening a UI page is server-authoritative only -- no client-side prediction.
    }
}
