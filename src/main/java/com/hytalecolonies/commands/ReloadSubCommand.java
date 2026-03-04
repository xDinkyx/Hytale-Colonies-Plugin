package com.hytalecolonies.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import com.hytalecolonies.HytaleColoniesPlugin;

import javax.annotation.Nonnull;

/**
 * /hc reload - Reload plugin configuration
 */
public class ReloadSubCommand extends CommandBase {

    public ReloadSubCommand() {
        super("reload", "Reload plugin configuration");
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        HytaleColoniesPlugin plugin = HytaleColoniesPlugin.getInstance();

        if (plugin == null) {
            context.sendMessage(Message.raw("Error: Plugin not loaded"));
            return;
        }

        context.sendMessage(Message.raw("Reloading HytaleColonies..."));

        // TODO: Add your reload logic here
        // Example: Reload config files, refresh caches, etc.

        context.sendMessage(Message.raw("HytaleColonies reloaded successfully!"));
    }
}