package com.hytalecolonies.commands;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hytalecolonies.commands.debug.BlockEntityInfoCommand;
import com.hytalecolonies.commands.debug.DebugGuiCommand;
/**
 * Main command for the Colonies plugin.
 *
 * Usage:
 * - /hc help     - Show available commands
 * - /hc info     - Show plugin information
 * - /hc reload   - Reload plugin configuration
 * - /hc ui       - Open the plugin dashboard
 * - /hc testpath - Test NPC pathfinding
 *
 * Aliases: /hc, /colony, /col
 */
public class HytaleColoniesPluginCommand extends AbstractCommandCollection {

    public HytaleColoniesPluginCommand(String pluginName, String pluginVersion) {
        super("hc", "HytaleColonies plugin commands");
        this.addAliases("colony", "col");

        this.addSubCommand(new HelpSubCommand());
        this.addSubCommand(new InfoSubCommand(pluginName, pluginVersion));
        this.addSubCommand(new ReloadSubCommand());
        this.addSubCommand(new UISubCommand());
        this.addSubCommand(new BlockEntityInfoCommand());
        this.addSubCommand(new TestMoveCommand());
        this.addSubCommand(new ConstructSubCommand());
        this.addSubCommand(new ConstructorListSubCommand());
        this.addSubCommand(new DebugGuiCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
}