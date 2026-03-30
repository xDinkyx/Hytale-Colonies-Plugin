package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/** Bundles ECS references and convenience helpers passed to each {@link JobStateHandler}. */
public final class JobContext {

    public final Ref<EntityStore> colonistRef;
    public final JobComponent job;
    public final Store<EntityStore> store;
    public final CommandBuffer<EntityStore> commandBuffer;
    public final World world;

    public JobContext(Ref<EntityStore> colonistRef, JobComponent job,
                      Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        this.colonistRef = colonistRef;
        this.job = job;
        this.store = store;
        this.commandBuffer = commandBuffer;
        this.world = store.getExternalData().getWorld();
    }

    @Nullable
    public TransformComponent getTransform() {
        return store.getComponent(colonistRef, TransformComponent.getComponentType());
    }

    /** Returns the workstation's component, or {@code null} if the workstation block is gone. */
    @Nullable
    public WorkStationComponent getWorkStation() {
        Vector3i pos = job.getWorkStationBlockPosition();
        if (pos == null) return null;
        Ref<ChunkStore> wsRef = BlockModule.getBlockEntity(world, pos.x, pos.y, pos.z);
        return wsRef != null
                ? wsRef.getStore().getComponent(wsRef, WorkStationComponent.getComponentType())
                : null;
    }
}
