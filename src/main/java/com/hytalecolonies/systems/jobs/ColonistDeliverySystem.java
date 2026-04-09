package com.hytalecolonies.systems.jobs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.utils.ColonistStateUtil;

/**
 * Delivery-related systems for colonist NPCs.
 *
 * <p>The main delivery flow (finding containers, navigating, depositing) is now
 * driven entirely by NPC role JSON via {@code FindDeliveryContainer} and
 * {@code DepositItems} custom actions. The outer {@link DelayedEntitySystem}
 * tick here is a no-op and kept only so that the {@link OnContainerRemoved}
 * inner class can remain nested without structural changes to the plugin.
 *
 * <p>The {@link OnContainerRemoved} inner class MUST remain active: it listens
 * for container block entities being removed from the world and clears any
 * colonist's cached {@code deliveryContainerPosition} that pointed to the
 * removed container, redirecting them home instead.
 */
public class ColonistDeliverySystem extends DelayedEntitySystem<EntityStore> {

    private final Query<EntityStore> query = Query.and(
            ColonistComponent.getComponentType(),
            JobComponent.getComponentType()
    );

    public ColonistDeliverySystem() {
        super(2.0f);
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Delivery is now fully driven by the NPC role JSON (FindDeliveryContainer + DepositItems actions).
        // This tick is intentionally empty.
    }

    // ===== Container removal =====

    /**
     * Clears the cached delivery target when a container block entity is removed.
     * Uses {@link RefSystem} because block breaks remove the entire entity, not individual components.
     */
    public static class OnContainerRemoved extends RefSystem<ChunkStore> {

        private final ComponentType<ChunkStore, ItemContainerBlock> itemContainerType = ItemContainerBlock.getComponentType();
        private final ComponentType<ChunkStore, BlockModule.BlockStateInfo> blockStateInfoType = BlockModule.BlockStateInfo.getComponentType();
        private final Query<ChunkStore> query = Query.and(itemContainerType, blockStateInfoType);

        private final Query<EntityStore> colonistQuery = Query.and(
                ColonistComponent.getComponentType(),
                JobComponent.getComponentType()
        );

        @Override
        public Query<ChunkStore> getQuery() {
            return query;
        }

        @Override
        public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason,
                                  @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {}

        @Override
        public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason reason,
                                   @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
            DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                    "[ColonistDelivery:ContainerRemoved] Fired -- reason=%s.", reason);

            if (reason == RemoveReason.UNLOAD) return;

            BlockModule.BlockStateInfo info = commandBuffer.getComponent(ref, blockStateInfoType);
            if (info == null) {
                DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                        "[ColonistDelivery:ContainerRemoved] No BlockStateInfo on removed container entity -- cannot resolve position.");
                return;
            }

            Ref<ChunkStore> chunkRef = info.getChunkRef();
            if (!chunkRef.isValid()) {
                DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                        "[ColonistDelivery:ContainerRemoved] ChunkRef is invalid -- cannot resolve position.");
                return;
            }

            BlockChunk blockChunk = commandBuffer.getComponent(chunkRef, BlockChunk.getComponentType());
            if (blockChunk == null) {
                DebugLog.warning(DebugCategory.COLONIST_DELIVERY,
                        "[ColonistDelivery:ContainerRemoved] No BlockChunk on chunkRef -- cannot resolve position.");
                return;
            }

            int idx = info.getIndex();
            int localX = ChunkUtil.xFromBlockInColumn(idx);
            int localY = ChunkUtil.yFromBlockInColumn(idx);
            int localZ = ChunkUtil.zFromBlockInColumn(idx);
            Vector3i pos = new Vector3i(
                    ChunkUtil.worldCoordFromLocalCoord(blockChunk.getX(), localX),
                    localY,
                    ChunkUtil.worldCoordFromLocalCoord(blockChunk.getZ(), localZ)
            );

            DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                    "[ColonistDelivery:ContainerRemoved] Container removed at %s -- scanning colonists.", pos);

            Store<EntityStore> entityStore = store.getExternalData().getWorld().getEntityStore().getStore();
            entityStore.forEachChunk(colonistQuery, (chunk, cb) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    JobComponent job = chunk.getComponent(i, JobComponent.getComponentType());
                    if (job == null) continue;
                    DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                            "[ColonistDelivery:ContainerRemoved] [%s] Colonist state=%s deliveryTarget=%s.",
                            DebugLog.npcId(chunk.getReferenceTo(i), entityStore), job.getCurrentTask(), job.deliveryContainerPosition);
                    if (job.getCurrentTask() != JobState.DeliveringItems) continue;
                    if (!pos.equals(job.deliveryContainerPosition)) continue;

                    Ref<EntityStore> colonistRef = chunk.getReferenceTo(i);
                    job.deliveryContainerPosition = null;
                    Vector3i wsPos = job.getWorkStationBlockPosition();
                    if (wsPos != null) {
                        cb.addComponent(colonistRef, MoveToTargetComponent.getComponentType(),
                                new MoveToTargetComponent(new Vector3d(wsPos.x + 0.5, wsPos.y, wsPos.z + 0.5)));
                    }
                    ColonistStateUtil.setJobState(colonistRef, entityStore, job, JobState.TravelingToHome);
                    DebugLog.info(DebugCategory.COLONIST_DELIVERY,
                            "[ColonistDelivery:ContainerRemoved] [%s] Redirected colonist home.",
                            DebugLog.npcId(colonistRef, entityStore));
                }
            });
        }
    }
}
