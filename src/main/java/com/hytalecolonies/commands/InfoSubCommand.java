package com.hytalecolonies.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import com.hytalecolonies.HytaleColoniesPlugin;

import javax.annotation.Nonnull;

/**
 * /hc info - Show plugin information
 */
public class InfoSubCommand extends CommandBase {

    public InfoSubCommand() {
        super("info", "Show plugin information");
        this.setPermissionGroup(null);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        HytaleColoniesPlugin plugin = HytaleColoniesPlugin.getInstance();

        context.sendMessage(Message.raw(""));
        context.sendMessage(Message.raw("=== HytaleColonies Info ==="));
        context.sendMessage(Message.raw("Name: HytaleColonies"));
        context.sendMessage(Message.raw("Version: 1.0.0"));
        context.sendMessage(Message.raw("Author: xDinkyx"));
        context.sendMessage(Message.raw("Status: " + (plugin != null ? "Running" : "Not loaded")));
        context.sendMessage(Message.raw("===================="));
    }
}