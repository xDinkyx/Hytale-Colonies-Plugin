package com.hytalecolonies.utils;

import java.util.function.BiConsumer;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;

/** Utility wrappers for ECS store iteration patterns. */
public final class StoreUtil {

    private StoreUtil() {
    }

    public static <ECS_TYPE> void forEachChunkMatchingQuery(@Nonnull Store<ECS_TYPE> store,
                                                             @Nonnull Query<ECS_TYPE> query,
                                                             @Nonnull BiConsumer<ArchetypeChunk<ECS_TYPE>, CommandBuffer<ECS_TYPE>> chunkConsumer) {
        store.forEachChunk(query, chunkConsumer);
    }
}