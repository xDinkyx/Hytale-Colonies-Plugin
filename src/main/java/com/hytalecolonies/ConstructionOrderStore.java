package com.hytalecolonies;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.util.Config;

/** Server-side store for construction orders, backed by a Config file. Survives server restarts. */
public final class ConstructionOrderStore
{
    // Persisted status values.
    public static final String STATUS_PENDING     = "Pending";
    public static final String STATUS_IN_PROGRESS = "InProgress";

    /** Plain data object. */
    public static final class Entry
    {
        public static final BuilderCodec<Entry> CODEC =
                BuilderCodec.builder(Entry.class, Entry::new)
                        .append(new KeyedCodec<>("Id", Codec.UUID_STRING), (o, v) -> o.id = v, o -> o.id)
                        .add()
                        .append(new KeyedCodec<>("PrefabId", Codec.STRING), (o, v) -> o.prefabId = v, o -> o.prefabId)
                        .add()
                        .append(new KeyedCodec<>("BuildOrigin", Vector3i.CODEC), (o, v) -> o.buildOrigin = v, o -> o.buildOrigin)
                        .add()
                        .append(new KeyedCodec<>("Status", Codec.STRING), (o, v) -> o.status = v, o -> o.status)
                        .add()
                        .build();

        public UUID id = UUID.randomUUID();
        public String prefabId = "";
        public Vector3i buildOrigin = null;
        public String status = STATUS_PENDING;

        /** Transient -- loaded prefab selection (rotation included). Not persisted. */
        public transient BlockSelection cachedSelection;

        /** Transient -- prefab blocks sorted Y ascending (build order); iterate in reverse for clearing. Not persisted. */
        public transient List<int[]> cachedSortedBlocks;

        public Entry()
        {
        }

        public Entry(UUID id, String prefabId, Vector3i buildOrigin)
        {
            this.id = id;
            this.prefabId = prefabId;
            this.buildOrigin = buildOrigin;
        }
    }

    public static final class StoreData
    {
        public static final BuilderCodec<StoreData> CODEC = BuilderCodec.builder(StoreData.class, StoreData::new)
                                                                    .append(new KeyedCodec<>("Orders", new ArrayCodec<>(Entry.CODEC, Entry[] ::new)),
                                                                            (o, v) -> o.orders = v != null ? v : new Entry[0], o -> o.orders)
                                                                    .add()
                                                                    .build();

        public Entry[] orders = new Entry[0];

        public StoreData()
        {
        }
    }

    // Singleton instance.
    private static final ConstructionOrderStore INSTANCE = new ConstructionOrderStore();

    // Background executor so config writes never block the world tick thread.
    private static final ExecutorService SAVE_EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ConstructionOrderStore-save");
        t.setDaemon(true);
        return t;
    });

    private final Map<UUID, Entry> orders = new ConcurrentHashMap<>();
    private Config<StoreData> config;

    private ConstructionOrderStore()
    {
    }

    public static ConstructionOrderStore get()
    {
        return INSTANCE;
    }

    /** Loads persisted orders from config. Call once from plugin setup. */
    public void init(Config<StoreData> config)
    {
        this.config = config;
        orders.clear();
        for (Entry e : config.get().orders)
        {
            orders.put(e.id, e);
        }
    }

    // Mutation API

    /** Adds an order and persists asynchronously. */
    public void add(Entry entry)
    {
        orders.put(entry.id, entry);
        save();
    }

    /** Marks an order in-progress and persists so the status survives a restart. */
    public void markInProgress(UUID id)
    {
        Entry e = orders.get(id);
        if (e != null)
        {
            e.status = STATUS_IN_PROGRESS;
            save();
        }
    }

    /** Removes an order and persists asynchronously. */
    public void remove(UUID id)
    {
        orders.remove(id);
        save();
    }

    /** Returns all known orders. */
    public Collection<Entry> all()
    {
        return orders.values();
    }

    @Nullable public Entry get(UUID id)
    {
        return orders.get(id);
    }

    /** Snapshots current state and writes to disk on a background thread (non-blocking). */
    public void save()
    {
        if (config == null)
            return;
        Entry[] snapshot = orders.values().toArray(Entry[]::new);
        SAVE_EXEC.submit(() -> {
            config.get().orders = snapshot;
            config.save();
        });
    }

    /** Clears in-memory state on plugin shutdown so re-enable is safe. */
    public static void reset()
    {
        INSTANCE.orders.clear();
        INSTANCE.config = null;
    }
}
