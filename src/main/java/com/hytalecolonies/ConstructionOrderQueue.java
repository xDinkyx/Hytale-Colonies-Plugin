package com.hytalecolonies;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3i;

/** Global FIFO queue of pending construction order positions (block-entity anchors). */
public final class ConstructionOrderQueue {

    private static final ConstructionOrderQueue INSTANCE = new ConstructionOrderQueue();

    private final Queue<Vector3i> pending = new ConcurrentLinkedQueue<>();

    private ConstructionOrderQueue() {}

    public static ConstructionOrderQueue get() { return INSTANCE; }

    public void enqueue(Vector3i position) { pending.add(position.clone()); }

    @Nullable
    public Vector3i poll() { return pending.poll(); }

    public boolean isEmpty() { return pending.isEmpty(); }

    public int size() { return pending.size(); }
}
