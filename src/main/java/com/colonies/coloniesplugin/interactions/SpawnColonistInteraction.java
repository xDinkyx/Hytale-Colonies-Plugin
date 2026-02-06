package com.colonies.coloniesplugin.interactions;

import com.colonies.coloniesplugin.ColoniesPlugin;
import com.colonies.coloniesplugin.components.npc.ColonistComponent;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.validators.NPCRoleValidator;
import it.unimi.dsi.fastutil.Pair;

import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// Ideally this would extend SpawnNPCInteraction and override the spawn logic,
// but since that's currently not possible we duplicate the code.
public class SpawnColonistInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final BuilderCodec<SpawnColonistInteraction> CODEC = BuilderCodec.builder(
                    SpawnColonistInteraction.class, SpawnColonistInteraction::new, SimpleBlockInteraction.CODEC
            )
            .documentation("Spawns an NPC as a colonist on the block that is being interacted with.")
            .<String>append(
                    new KeyedCodec<>("EntityId", Codec.STRING),
                    (SpawnColonistInteraction, s) -> SpawnColonistInteraction.entityId = s,
                    SpawnColonistInteraction -> SpawnColonistInteraction.entityId
            )
            .documentation("The ID of the entity asset to spawn.")
            .addValidator(NPCRoleValidator.INSTANCE) // ToDo: Create a Colonist role validator?
            .add()
            .<Vector3d>append(
                    new KeyedCodec<>("SpawnOffset", Vector3d.CODEC),
                    (SpawnColonistInteraction, s) -> SpawnColonistInteraction.spawnOffset.assign(s),
                    SpawnColonistInteraction -> SpawnColonistInteraction.spawnOffset
            )
            .documentation("The offset to apply to the spawn position of the NPC, relative to the block's rotation and center.")
            .add()
            .<Float>append(
                    new KeyedCodec<>("SpawnYawOffset", Codec.FLOAT),
                    (SpawnColonistInteraction, f) -> SpawnColonistInteraction.spawnYawOffset = f,
                    SpawnColonistInteraction -> SpawnColonistInteraction.spawnYawOffset
            )
            .documentation("The yaw rotation offset in radians to apply to the NPC rotation, relative to the block's yaw.")
            .add()
            .build();
    protected String entityId;
    @Nonnull
    protected Vector3d spawnOffset = new Vector3d();
    protected float spawnYawOffset;

    private void spawnColonist(@Nonnull Store<EntityStore> store, @Nonnull Vector3i targetBlock) {
        World world = store.getExternalData().getWorld();
        SpawnColonistInteraction.SpawnData spawnData = this.computeSpawnData(world, targetBlock);

        // Spawn the NPC
        Pair<Ref<EntityStore>, INonPlayerCharacter> result = NPCPlugin.get().spawnNPC(store, this.entityId, null, spawnData.position(), spawnData.rotation());

        Ref<EntityStore> npcRef = result.first();
        NPCEntity npcComponent = store.getComponent(npcRef, Objects.requireNonNull(NPCEntity.getComponentType()));

        // Add the ColonistComponent to the spawned NPC
        ColonistComponent colonistComponent = new ColonistComponent("DefaultColonyId", "Colonist_Name", 1);
        store.addComponent(npcRef, ColoniesPlugin.getInstance().getColonistComponentType(), colonistComponent);
    }

    @Nonnull
    private SpawnColonistInteraction.SpawnData computeSpawnData(@Nonnull World world, @Nonnull Vector3i targetBlock) {

        long chunkIndex = ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z);
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);

        if (chunkRef != null && chunkRef.isValid()) {
            WorldChunk worldChunkComponent = chunkStore.getStore().getComponent(chunkRef, WorldChunk.getComponentType());

            assert worldChunkComponent != null;

            BlockType blockType = worldChunkComponent.getBlockType(targetBlock.x, targetBlock.y, targetBlock.z);
            if (blockType == null) {
                return new SpawnColonistInteraction.SpawnData(this.spawnOffset.clone().add(targetBlock).add(0.5, 0.5, 0.5), Vector3f.ZERO);
            }
            else {
                BlockChunk blockChunkComponent = chunkStore.getStore().getComponent(chunkRef, BlockChunk.getComponentType());
                if (blockChunkComponent == null) {
                    return new SpawnColonistInteraction.SpawnData(this.spawnOffset.clone().add(targetBlock).add(0.5, 0.5, 0.5), Vector3f.ZERO);
                } else {
                    BlockSection section = blockChunkComponent.getSectionAtBlockY(targetBlock.y);
                    int rotationIndex = section.getRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z);
                    RotationTuple rotationTuple = RotationTuple.get(rotationIndex);
                    Vector3d position = rotationTuple.rotate(this.spawnOffset);
                    Vector3d blockCenter = new Vector3d();
                    blockType.getBlockCenter(rotationIndex, blockCenter);
                    position.add(blockCenter).add(targetBlock);
                    Vector3f rotation = new Vector3f(0.0F, (float) (rotationTuple.yaw().getRadians() + Math.toRadians(this.spawnYawOffset)), 0.0F);
                    return new SpawnColonistInteraction.SpawnData(position, rotation);
                }
            }
        } else {
            return new SpawnColonistInteraction.SpawnData(this.spawnOffset.clone().add(targetBlock).add(0.5, 0.5, 0.5), Vector3f.ZERO);
        }
    }

    @Override
    protected void interactWithBlock(
            @Nonnull World world,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InteractionType type,
            @Nonnull InteractionContext context,
            @Nullable ItemStack itemInHand,
            @Nonnull Vector3i targetBlock,
            @Nonnull CooldownHandler cooldownHandler
    ) {
        commandBuffer.run(store -> this.spawnColonist(world.getEntityStore().getStore(), targetBlock));
    }

    @Override
    protected void simulateInteractWithBlock(
            @Nonnull InteractionType type, @Nonnull InteractionContext context, @Nullable ItemStack itemInHand, @Nonnull World world, @Nonnull Vector3i targetBlock
    ) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();

        assert commandBuffer != null;

        commandBuffer.run(store -> this.spawnColonist(world.getEntityStore().getStore(), targetBlock));
    }

    private record SpawnData(@Nonnull Vector3d position, @Nonnull Vector3f rotation) {
    }
}
