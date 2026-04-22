package com.hytalecolonies.commands;

import javax.annotation.Nonnull;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.ui.ConstructorPrefabPage;


/** /hc list -- opens the constructor prefab picker. */
public class ConstructorListSubCommand extends AbstractPlayerCommand
{

    public ConstructorListSubCommand()
    {
        super("list", "Open the constructor prefab picker");
        this.setPermissionGroup(null);
    }

    @Override protected boolean canGeneratePermission()
    {
        return false;
    }

    @Override
    protected void execute(@Nonnull CommandContext context,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull Ref<EntityStore> ref,
                           @Nonnull PlayerRef playerRef,
                           @Nonnull World world)
    {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null)
            return;

        BuilderToolsPlugin.BuilderState builderState = BuilderToolsPlugin.getState(player, playerRef);
        player.getPageManager().openCustomPage(ref, store, new ConstructorPrefabPage(playerRef, builderState));
    }
}
