package com.hytalecolonies;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nullable;

/** Global FIFO queue of pending construction order UUIDs (positions in {@link ConstructionOrderStore}). */
public final class ConstructionOrderQueue {

    private static final ConstructionOrderQueue INSTANCE = new ConstructionOrderQueue();

    private final Queue<UUID> pending = new ConcurrentLinkedQueue<>();

    private ConstructionOrderQueue() {}

    public static ConstructionOrderQueue get() { return INSTANCE; }

    public void enqueue(UUID id) { pending.add(id); }

    @Nullable
    public UUID poll() { return pending.poll(); }

    public boolean isEmpty() { return pending.isEmpty(); }

    public int size() { return pending.size(); }
}
