package com.hytalecolonies.commands;

import javax.annotation.Nonnull;

import com.hytalecolonies.listeners.ConstructorBuildOrderFilter;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.prefab.PrefabLoadException;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * /hc construct <name> -- selects a server prefab as the active build plan for
 * the
 * Colony Constructor Assign tool. Loads the prefab into the player's clipboard
 * so the
 * paste ghost appears, and records the name for the packet filter to use when
 * the
 * player confirms placement.
 */
public class ConstructSubCommand extends AbstractPlayerCommand {

    private static final Message MSG_PREFAB_NOT_FOUND = Message.translation(
            "commands.colonies.construct.prefabNotFound");
    private static final Message MSG_PREFAB_LOADED = Message.translation(
            "commands.colonies.construct.prefabLoaded");

    private final RequiredArg<String> prefabNameArg = this.withRequiredArg(
            "prefabName", "Name of the server prefab to queue as a colony build order", ArgTypes.GREEDY_STRING);

    public ConstructSubCommand() {
        super("construct", "Select a server prefab as the active colony build plan");
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
            @Nonnull World world) {

        String prefabName = prefabNameArg.get(context);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null)
            return;

        final String finalName = prefabName;
        final PlayerRef finalPlayerRef = playerRef;

        BuilderToolsPlugin.addToQueue(player, playerRef, (r, builderState, componentAccessor) -> {
            try {
                var prefab = PrefabStore.get().getServerPrefab(finalName);
                builderState.load(finalName, prefab, componentAccessor);
                ConstructorBuildOrderFilter.setPrefabForPlayer(finalPlayerRef.getUuid(), finalName);
                finalPlayerRef.sendMessage(MSG_PREFAB_LOADED.param("prefab", finalName));
            } catch (PrefabLoadException e) {
                finalPlayerRef.sendMessage(MSG_PREFAB_NOT_FOUND.param("prefab", finalName));
            }
        });
    }
}
