package com.hytalecolonies.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import com.hytalecolonies.HytaleColoniesPlugin;

import javax.annotation.Nonnull;

/**
 * /colonies info - Show plugin information
 */
public class InfoSubCommand extends CommandBase {

    private final String pluginName;
    private final String pluginVersion;

    public InfoSubCommand(String pluginName, String pluginVersion) {
        super("info", "Show plugin information");
        this.setPermissionGroup(null);
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        HytaleColoniesPlugin plugin = HytaleColoniesPlugin.getInstance();

        context.sendMessage(Message.raw(""));
        context.sendMessage(Message.raw("=== " + pluginName + " Info ==="));
        context.sendMessage(Message.raw("Version: " + pluginVersion));
        context.sendMessage(Message.raw("Author: xDinkyx"));
        context.sendMessage(Message.raw("Status: " + (plugin != null ? "Running" : "Not loaded")));
        context.sendMessage(Message.raw("===================="));
    }
}