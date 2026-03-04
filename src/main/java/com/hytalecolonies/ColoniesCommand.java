package com.hytalecolonies;

import com.hytalecolonies.commands.TestMoveCommand;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

/**
 * This is a colonies command that will simply print the name of the plugin in chat when used.
 */
public class ColoniesCommand extends AbstractCommandCollection {

    public ColoniesCommand(String pluginName, String pluginVersion) {
        super("colonies", "The base command for the Colonies plugin.");
        this.addAliases("hc", "colony", "col");
        this.setPermissionGroup(GameMode.Creative);
        this.addSubCommand(new TestMoveCommand());
        this.addSubCommand(new CommandBase("version", "Displays the plugin version.") {
            @Override
            protected void executeSync(@Nonnull CommandContext ctx) {
                ctx.sendMessage(Message.raw(pluginName + " version " + pluginVersion));
            }
        });
    }
}
