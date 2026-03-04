package com.hytalecolonies.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;

/**
 * Main command for HytaleColonies plugin.
 *
 * Usage:
 * - /hc help - Show available commands
 * - /hc info - Show plugin information
 * - /hc reload - Reload plugin configuration
 * - /hc ui - Open the plugin dashboard
 */
public class HytaleColoniesPluginCommand extends AbstractCommandCollection {

    public HytaleColoniesPluginCommand() {
        super("hc", "HytaleColonies plugin commands");

        // Add subcommands
        this.addSubCommand(new HelpSubCommand());
        this.addSubCommand(new InfoSubCommand());
        this.addSubCommand(new ReloadSubCommand());
        this.addSubCommand(new UISubCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false; // No permission required for base command
    }
}