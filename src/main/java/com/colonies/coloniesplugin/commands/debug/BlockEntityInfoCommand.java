package com.colonies.coloniesplugin.commands.debug;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.*;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.arguments.types.RelativeIntPosition;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.command.system.exceptions.GeneralCommandException;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;

public class BlockEntityInfoCommand extends AbstractWorldCommand {

    @Nonnull
    private static final Message MESSAGE_GENERAL_BLOCK_TARGET_NOT_IN_RANGE = Message.translation("server.general.blockTargetNotInRange");
    @Nonnull
    private static final Message MESSAGE_COMMANDS_ERRORS_PROVIDE_POSITION = Message.translation("server.commands.errors.providePosition");

    @Nonnull
    private final OptionalArg<RelativeIntPosition> positionArg = this.withOptionalArg(
            "position", "The coordinates of the block to inspect", ArgTypes.RELATIVE_BLOCK_POSITION
    );

    public BlockEntityInfoCommand() {
        super("blockentityinfo", "Logs all components on a block entity");
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
            position = TargetUtil.getTargetBlock(context.senderAsPlayerRef(), blockId -> blockId != 0, 10.0, store);
            if (position == null) {
                throw new GeneralCommandException(MESSAGE_GENERAL_BLOCK_TARGET_NOT_IN_RANGE);
            }
        }

        // 2. Get chunk reference and store
        long chunkIndex = ChunkUtil.indexChunkFromBlock(position.x, position.z);
        var chunkStore = world.getChunkStore().getStore();
        var chunkRef = world.getChunkStore().getChunkReference(chunkIndex);

        if (chunkRef == null || !chunkRef.isValid()) {
            context.sendMessage(Message.raw("Chunk not loaded at " + position));
            return;
        }

        // 3. Get block and block type
        BlockChunk blockChunk = chunkStore.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) {
            context.sendMessage(Message.raw("No BlockChunk found at " + position));
            return;
        }
        int blockId = blockChunk.getBlock(position.x, position.y, position.z);
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);

        // 4. Check for filler block
        BlockSection blockSection = blockChunk.getSectionAtBlockY(position.y);
        BlockBoundingBoxes hitbox = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        if (blockSection != null && hitbox != null && hitbox.protrudesUnitBox()) {
            int idx = ChunkUtil.indexBlock(position.x, position.y, position.z);
            int filler = blockSection.getFiller(idx);
            int fillerX = FillerBlockUtil.unpackX(filler);
            int fillerY = FillerBlockUtil.unpackY(filler);
            int fillerZ = FillerBlockUtil.unpackZ(filler);
            // Subtract the filler local coordinates from the look position to get the main block's position.
            position = Vector3i.add(position, new Vector3i(-fillerX, -fillerY, -fillerZ));
            // Re-fetch blockId and blockType for the main block
            blockId = blockChunk.getBlock(fillerX, fillerY, fillerZ);
            blockType = BlockType.getAssetMap().getAsset(blockId);
        }

        // 5. Now use 'position' to get block entity as usual
        var blockEntity = BlockModule.getBlockEntity(world, position.x, position.y, position.z);
        if (blockEntity == null) {
            context.sendMessage(Message.raw("No BlockEntity found for block " + blockId + "-" + blockType.getId() + " at " + position));
            return;
        }

        // 6. Inspect and Log Components
        Store<ChunkStore> entityChunkStore = blockEntity.getStore();
        Archetype<ChunkStore> archetype = entityChunkStore.getArchetype(blockEntity);

        StringBuilder sb = new StringBuilder();
        sb.append("BlockEntity found for block at ").append(position)
                .append(": ").append(blockType.getId())
                .append(" (Block ID: ").append(blockId).append(")\n")
                .append("--- BlockEntity Components ---\n");

        for (int i = 0; i < archetype.length(); i++) {
            var componentType = archetype.get(i);
            if (componentType != null) {
                String className = componentType.getTypeClass().getSimpleName();
                sb.append("-> ").append(className).append("\n");
            }
        }

        context.sendMessage(Message.raw(sb.toString()));
    }
}