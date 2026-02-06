package com.colonies.coloniesplugin.commands;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.RelativeIntPosition;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.command.system.exceptions.GeneralCommandException;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;

public class DebugBlockCommand extends AbstractWorldCommand {

    @Nonnull
    private static final Message MESSAGE_GENERAL_BLOCK_TARGET_NOT_IN_RANGE = Message.translation("server.general.blockTargetNotInRange");
    @Nonnull
    private static final Message MESSAGE_COMMANDS_ERRORS_PROVIDE_POSITION = Message.translation("server.commands.errors.providePosition");

    @Nonnull
    private final OptionalArg<RelativeIntPosition> positionArg = this.withOptionalArg(
            "position", "The coordinates of the block to inspect", ArgTypes.RELATIVE_BLOCK_POSITION
    );

    public DebugBlockCommand() {
        super("logcomponents", "Logs all components on a block entity");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext context,
            @Nonnull World world,
            @Nonnull Store<EntityStore> store) {
        Vector3i position;

        // 1. Resolve Position (Argument or Look-at)
        if (this.positionArg.provided(context)) {
            position = this.positionArg.get(context).getBlockPosition(context, store);
        } else {
            if (!context.isPlayer()) {
                throw new GeneralCommandException(MESSAGE_COMMANDS_ERRORS_PROVIDE_POSITION);
            }

            Ref<EntityStore> playerRef = context.senderAsPlayerRef();
            // Using 10.0 range as per the BlockSpawnerSetCommand reference
            position = TargetUtil.getTargetBlock(playerRef, 10.0, store);

            if (position == null) {
                throw new GeneralCommandException(MESSAGE_GENERAL_BLOCK_TARGET_NOT_IN_RANGE);
            }
        }

        // 2. Locate the Block Entity
        WorldChunk chunk = world.getChunk(ChunkUtil.indexChunkFromBlock(position.x, position.z));
        Ref<ChunkStore> blockRef = chunk.getBlockComponentEntity(position.x, position.y, position.z);

        if (blockRef == null) {
            context.sendMessage(Message.raw("No BlockEntity found at " + position.toString()));
            return;
        }

        // 3. Inspect and Log Components
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        Archetype<ChunkStore> archetype = chunkStore.getArchetype(blockRef);

        context.sendMessage(Message.raw("--- BlockEntity Components at " + position.toString() + " ---"));

        for (int i = 0; i < archetype.length(); i++) {
            ComponentType<ChunkStore, ? extends Component<ChunkStore>> componentType =
                    (ComponentType<ChunkStore, ? extends Component<ChunkStore>>) archetype.get(i);

            if (componentType != null) {
                Object componentInstance = chunkStore.getComponent(blockRef, componentType);
                String className = componentType.getTypeClass().getSimpleName();
                String data = (componentInstance != null) ? componentInstance.toString() : "null";

                context.sendMessage(Message.raw("-> " + className + ": " + data));
            }
        }
    }
}