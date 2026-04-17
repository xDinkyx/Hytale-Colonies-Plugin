package com.hytalecolonies.commands;

import javax.annotation.Nonnull;

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
 * /hc construct {@literal <name>} -- loads a server prefab into the player's clipboard so
 * the paste ghost preview appears. The actual construction order prefab ID comes
 * from the item's {@code ConstructionPrefabId} metadata, not from this command.
 */
public class ConstructSubCommand extends AbstractPlayerCommand
{

    private static final Message MSG_PREFAB_NOT_FOUND = Message.translation("commands.colonies.construct.prefabNotFound");
    private static final Message MSG_PREFAB_LOADED = Message.translation("commands.colonies.construct.prefabLoaded");

    private final RequiredArg<String> prefabNameArg =
            this.withRequiredArg("prefabName", "Name of the server prefab to load into clipboard", ArgTypes.GREEDY_STRING);

    public ConstructSubCommand()
    {
        super("construct", "Load a server prefab into clipboard for ghost preview");
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

        String prefabName = prefabNameArg.get(context);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null)
            return;

        final String finalName = prefabName;
        final PlayerRef finalPlayerRef = playerRef;

        BuilderToolsPlugin.addToQueue(player, playerRef, (r, builderState, componentAccessor) -> {
            try
            {
                var prefab = PrefabStore.get().getServerPrefab(finalName);
                builderState.load(finalName, prefab, componentAccessor);
                finalPlayerRef.sendMessage(MSG_PREFAB_LOADED.param("prefab", finalName));
            }
            catch (PrefabLoadException e)
            {
                finalPlayerRef.sendMessage(MSG_PREFAB_NOT_FOUND.param("prefab", finalName));
            }
        });
    }
}
