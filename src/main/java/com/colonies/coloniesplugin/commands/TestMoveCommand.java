package com.colonies.coloniesplugin.commands;

import com.colonies.coloniesplugin.components.npc.MoveToTargetComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.commands.NPCWorldCommandBase;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.jspecify.annotations.NonNull;

public class TestMoveCommand extends NPCWorldCommandBase {


    public TestMoveCommand() {
        super("testpath", "Command to test NPC pathfinding by assigning a MoveToTargetComponent with the player's current position as target.");
    }

    @Override
    protected void execute(@NonNull CommandContext commandContext,
                           @NonNull NPCEntity npcEntity,
                           @NonNull World world,
                           @NonNull Store<EntityStore> store,
                           @NonNull Ref<EntityStore> ref) {
        // Get the player reference who issued the command
        Ref<EntityStore> playerRef = commandContext.senderAsPlayerRef();
        if (playerRef == null || !playerRef.isValid()) {
            commandContext.sendMessage(Message.raw("You must be a player to use this command."));
            return;
        }

        // Get the player's current position
        var playerTransform = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTransform == null) {
            commandContext.sendMessage(Message.raw("Could not get your position."));
            return;
        }

        var targetPos = playerTransform.getPosition();

        // Assign MoveToTargetComponent to the NPC entity
        store.addComponent(ref, MoveToTargetComponent.getComponentType(), new MoveToTargetComponent(targetPos));

        commandContext.sendMessage(Message.raw("Assigned NPC to move to your position: " + targetPos));
    }
}
