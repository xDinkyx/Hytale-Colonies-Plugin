package com.hytalecolonies.commands.debug;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hytalecolonies.HytaleColoniesPlugin;
import com.hytalecolonies.debug.DebugConfig;
import com.hytalecolonies.ui.DebugConfigUI;

import javax.annotation.Nonnull;

/**
 * /hc debuggui — Opens the Debug Configuration UI for adjusting per-category log levels.
 */
public class DebugGuiCommand extends AbstractPlayerCommand {

    public DebugGuiCommand() {
        super("debuggui", "Open the debug configuration panel");
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Error: Could not get Player component."));
            return;
        }

        Config<DebugConfig> debugConfig = HytaleColoniesPlugin.getInstance().getDebugConfig();
        DebugConfigUI page = new DebugConfigUI(playerRef, debugConfig);
        player.getPageManager().openCustomPage(ref, store, page);
    }
}
