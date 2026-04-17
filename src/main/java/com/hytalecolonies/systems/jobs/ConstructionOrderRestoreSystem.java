package com.hytalecolonies.systems.jobs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hytalecolonies.ConstructionOrderQueue;
import com.hytalecolonies.components.jobs.ConstructionOrderComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;

/**
 * Re-enqueues "Pending" construction orders when world chunks are loaded on server start.
 * {@link com.hytalecolonies.systems.jobs.ConstructionOrderQueueSystem} handles new orders via
 * {@code putComponent}; this system covers the world-load path where {@code RefChangeSystem}
 * does not fire.
 */
public class ConstructionOrderRestoreSystem extends RefSystem<ChunkStore> {

    @Nullable
    @Override
    public Query<ChunkStore> getQuery() {
        return ConstructionOrderComponent.getComponentType();
    }

    @Override
    public void onEntityAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        if (reason != AddReason.LOAD) return;
        ConstructionOrderComponent order = store.getComponent(ref, ConstructionOrderComponent.getComponentType());
        if (order == null || !"Pending".equals(order.status) || order.buildOrigin == null) return;
        ConstructionOrderQueue.get().enqueue(order.buildOrigin);
        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                "[ConstructionOrderRestore] Re-enqueued pending order '%s' at %s on world load (queue size: %d).",
                order.prefabId, order.buildOrigin, ConstructionOrderQueue.get().size());
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {}
}
