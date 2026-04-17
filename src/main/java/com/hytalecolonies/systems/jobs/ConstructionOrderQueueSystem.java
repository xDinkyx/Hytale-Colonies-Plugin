package com.hytalecolonies.systems.jobs;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hytalecolonies.ConstructionOrderQueue;
import com.hytalecolonies.components.jobs.ConstructionOrderComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;

/** Populates {@link ConstructionOrderQueue} on new order creation and on world-load restoration. */
public class ConstructionOrderQueueSystem extends RefChangeSystem<ChunkStore, ConstructionOrderComponent> {

    @Override
    public ComponentType<ChunkStore, ConstructionOrderComponent> componentType() {
        return ConstructionOrderComponent.getComponentType();
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return ConstructionOrderComponent.getComponentType();
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull ConstructionOrderComponent component,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        if (!"Pending".equals(component.status) || component.buildOrigin == null) return;
        ConstructionOrderQueue.get().enqueue(component.buildOrigin);
        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                "[ConstructionOrderRestore] Enqueued order '%s' at %s (status=%s, queue size: %d).",
                component.prefabId, component.buildOrigin, component.status,
                ConstructionOrderQueue.get().size());
    }

    @Override
    public void onComponentSet(
            @Nonnull Ref<ChunkStore> ref,
            @Nullable ConstructionOrderComponent oldComponent,
            @Nonnull ConstructionOrderComponent newComponent,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        if (!"Pending".equals(newComponent.status) || newComponent.buildOrigin == null) return;
        ConstructionOrderQueue.get().enqueue(newComponent.buildOrigin);
        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                "[ConstructionOrderRestore] Enqueued replacement order '%s' at %s (queue size: %d).",
                newComponent.prefabId, newComponent.buildOrigin,
                ConstructionOrderQueue.get().size());
    }

    @Override
    public void onComponentRemoved(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull ConstructionOrderComponent component,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {}
}
