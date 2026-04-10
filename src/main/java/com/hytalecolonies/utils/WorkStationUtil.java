package com.hytalecolonies.utils;

import com.hytalecolonies.components.jobs.ConstructionOrderComponent;
import com.hytalecolonies.components.jobs.ConstructorWorkStationComponent;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.MinerWorkStationComponent;
import com.hytalecolonies.components.jobs.WoodsmanWorkStationComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/** Lookup utilities for workstation block components. */
public final class WorkStationUtil {

    private WorkStationUtil() {}

    @Nullable
    public static WorkStationComponent getWorkStation(Store<EntityStore> store, Ref<EntityStore> ref) {
        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) return null;
        Vector3i pos = job.getWorkStationBlockPosition();
        if (pos == null) return null;
        return getWorkStationAt(world(store), pos);
    }

    @Nullable
    public static WorkStationComponent getWorkStationAt(World world, Vector3i position) {
        Ref<ChunkStore> ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z);
        if (ref == null) return null;
        return ref.getStore().getComponent(ref, WorkStationComponent.getComponentType());
    }

    @Nullable
    public static WoodsmanWorkStationComponent getWoodsmanWorkStation(Store<EntityStore> store,
                                                                       Ref<EntityStore> ref) {
        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) return null;
        Vector3i pos = job.getWorkStationBlockPosition();
        if (pos == null) return null;
        return getWoodsmanWorkStationAt(world(store), pos);
    }

    @Nullable
    public static WoodsmanWorkStationComponent getWoodsmanWorkStationAt(World world, Vector3i position) {
        Ref<ChunkStore> ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z);
        if (ref == null) return null;
        return ref.getStore().getComponent(ref, WoodsmanWorkStationComponent.getComponentType());
    }

    @Nullable
    public static MinerWorkStationComponent getMinerWorkStation(Store<EntityStore> store,
                                                                Ref<EntityStore> ref) {
        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) return null;
        Vector3i pos = job.getWorkStationBlockPosition();
        if (pos == null) return null;
        return getMinerWorkStationAt(world(store), pos);
    }

    @Nullable
    public static MinerWorkStationComponent getMinerWorkStationAt(World world, Vector3i position) {
        Ref<ChunkStore> ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z);
        if (ref == null) return null;
        return ref.getStore().getComponent(ref, MinerWorkStationComponent.getComponentType());
    }

    @Nullable
    public static ConstructorWorkStationComponent getConstructorWorkStation(Store<EntityStore> store,
                                                                             Ref<EntityStore> ref) {
        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) return null;
        Vector3i pos = job.getWorkStationBlockPosition();
        if (pos == null) return null;
        return getConstructorWorkStationAt(world(store), pos);
    }

    @Nullable
    public static ConstructorWorkStationComponent getConstructorWorkStationAt(World world, Vector3i position) {
        Ref<ChunkStore> ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z);
        if (ref == null) return null;
        return ref.getStore().getComponent(ref, ConstructorWorkStationComponent.getComponentType());
    }

    @Nullable
    public static ConstructionOrderComponent getConstructionOrder(Store<EntityStore> store, Ref<EntityStore> ref) {
        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) return null;
        Vector3i wsPos = job.getWorkStationBlockPosition();
        if (wsPos == null) return null;
        return getConstructionOrderForWorkstation(world(store), wsPos);
    }

    /** Returns the active {@link ConstructionOrderComponent} for the given workstation position, or null if none assigned. */
    @Nullable
    public static ConstructionOrderComponent getConstructionOrderForWorkstation(World world, Vector3i wsPos) {
        ConstructorWorkStationComponent ws = getConstructorWorkStationAt(world, wsPos);
        if (ws == null || ws.activeOrderPosition == null) return null;
        return getConstructionOrderAt(world, ws.activeOrderPosition);
    }

    @Nullable
    public static ConstructionOrderComponent getConstructionOrderAt(World world, Vector3i position) {
        Ref<ChunkStore> ref = BlockModule.getBlockEntity(world, position.x, position.y, position.z);
        if (ref == null) return null;
        return ref.getStore().getComponent(ref, ConstructionOrderComponent.getComponentType());
    }

    private static World world(Store<EntityStore> store) {
        return store.getExternalData().getWorld();
    }
}

