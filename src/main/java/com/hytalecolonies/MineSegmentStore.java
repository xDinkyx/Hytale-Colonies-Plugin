package com.hytalecolonies;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.util.Config;

/**
 * Server-side store for mine segment excavation orders per workstation.
 * Analogous to {@link ConstructionOrderStore} but for the Miner job.
 * Persisted so progress survives server restarts.
 */
public final class MineSegmentStore
{
    public static final String STATUS_PENDING     = "Pending";
    public static final String STATUS_IN_PROGRESS = "InProgress";
    public static final String STATUS_COMPLETE    = "Complete";

    // ===== Entry =====

    public static final class Entry
    {
        public static final BuilderCodec<Entry> CODEC =
                BuilderCodec.builder(Entry.class, Entry::new)
                        .append(new KeyedCodec<>("Id", Codec.UUID_STRING),       (o, v) -> o.id = v,             o -> o.id)
                        .add()
                        .append(new KeyedCodec<>("WorkstationKey", Codec.STRING), (o, v) -> o.workstationKey = v, o -> o.workstationKey)
                        .add()
                        .append(new KeyedCodec<>("SegmentType", Codec.STRING),   (o, v) -> o.segmentType = v,    o -> o.segmentType)
                        .add()
                        .append(new KeyedCodec<>("PrefabId", Codec.STRING),      (o, v) -> o.prefabId = v,       o -> o.prefabId)
                        .add()
                        .append(new KeyedCodec<>("Origin", Vector3i.CODEC),      (o, v) -> o.origin = v,         o -> o.origin)
                        .add()
                        .append(new KeyedCodec<>("SequenceIndex", Codec.INTEGER), (o, v) -> o.sequenceIndex = v, o -> o.sequenceIndex)
                        .add()
                        .append(new KeyedCodec<>("Status", Codec.STRING),        (o, v) -> o.status = v,         o -> o.status)
                        .add()
                        .build();

        public UUID    id            = UUID.randomUUID();
        public String  workstationKey = "";
        public String  segmentType   = "";
        public String  prefabId      = "";
        @Nullable
        public Vector3i origin       = null;
        public int     sequenceIndex = 0;
        public String  status        = STATUS_PENDING;

        /** Transient -- loaded prefab selection. Not persisted. */
        public transient BlockSelection cachedSelection;
        /** Transient -- prefab blocks sorted Y descending (clearing order). Not persisted. */
        public transient java.util.List<int[]> cachedSortedBlocks;

        public Entry() {}

        public Entry(String workstationKey, String segmentType, String prefabId,
                     @Nullable Vector3i origin, int sequenceIndex)
        {
            this.workstationKey = workstationKey;
            this.segmentType    = segmentType;
            this.prefabId       = prefabId;
            this.origin         = origin;
            this.sequenceIndex  = sequenceIndex;
        }
    }

    // ===== StoreData =====

    public static final class StoreData
    {
        public static final BuilderCodec<StoreData> CODEC =
                BuilderCodec.builder(StoreData.class, StoreData::new)
                        .append(new KeyedCodec<>("Segments", new ArrayCodec<>(Entry.CODEC, Entry[]::new)),
                                (o, v) -> o.segments = v != null ? v : new Entry[0],
                                o -> o.segments)
                        .add()
                        .build();

        public Entry[] segments = new Entry[0];

        public StoreData() {}
    }

    // ===== Singleton =====

    private static final MineSegmentStore INSTANCE = new MineSegmentStore();

    private static final ExecutorService SAVE_EXEC = Executors.newSingleThreadExecutor(r ->
    {
        Thread t = new Thread(r, "MineSegmentStore-save");
        t.setDaemon(true);
        return t;
    });

    private final Map<UUID, Entry> segments = new ConcurrentHashMap<>();
    private Config<StoreData> config;

    private MineSegmentStore() {}

    public static MineSegmentStore get()
    {
        return INSTANCE;
    }

    /** Builds the workstation key from a block position. */
    public static String keyFor(Vector3i pos)
    {
        return pos.x + "," + pos.y + "," + pos.z;
    }

    /** Builds the workstation key from x/y/z coordinates. */
    public static String keyFor(int x, int y, int z)
    {
        return x + "," + y + "," + z;
    }

    // ===== Lifecycle =====

    public void init(Config<StoreData> config)
    {
        this.config = config;
        segments.clear();
        for (Entry e : config.get().segments)
        {
            segments.put(e.id, e);
        }
    }

    // ===== Query API =====

    /** Returns all segments for a workstation, sorted by {@link Entry#sequenceIndex} ascending. */
    public List<Entry> getSegmentsForWorkstation(String workstationKey)
    {
        return segments.values().stream()
                .filter(e -> workstationKey.equals(e.workstationKey))
                .sorted(Comparator.comparingInt(e -> e.sequenceIndex))
                .collect(Collectors.toList());
    }

    /**
     * Returns the active (Pending or InProgress) segment for the workstation
     * with the lowest sequence index, or {@code null} if all segments are complete.
     */
    @Nullable
    public Entry getActiveSegment(String workstationKey)
    {
        return segments.values().stream()
                .filter(e -> workstationKey.equals(e.workstationKey)
                        && !STATUS_COMPLETE.equals(e.status))
                .min(Comparator.comparingInt(e -> e.sequenceIndex))
                .orElse(null);
    }

    /** Returns the highest sequence index used by this workstation, or -1 if none. */
    public int getMaxSequenceIndex(String workstationKey)
    {
        return segments.values().stream()
                .filter(e -> workstationKey.equals(e.workstationKey))
                .mapToInt(e -> e.sequenceIndex)
                .max()
                .orElse(-1);
    }

    @Nullable
    public Entry get(UUID id)
    {
        return segments.get(id);
    }

    public Collection<Entry> all()
    {
        return segments.values();
    }

    // ===== Mutation API =====

    public void add(Entry entry)
    {
        segments.put(entry.id, entry);
        save();
    }

    public void markInProgress(UUID id)
    {
        Entry e = segments.get(id);
        if (e != null)
        {
            e.status = STATUS_IN_PROGRESS;
            save();
        }
    }

    public void markComplete(UUID id)
    {
        Entry e = segments.get(id);
        if (e != null)
        {
            e.status = STATUS_COMPLETE;
            save();
        }
    }

    public void remove(UUID id)
    {
        segments.remove(id);
        save();
    }

    // ===== Persistence =====

    public void save()
    {
        if (config == null)
            return;
        Entry[] snapshot = segments.values().toArray(Entry[]::new);
        SAVE_EXEC.submit(() ->
        {
            config.get().segments = snapshot;
            config.save();
        });
    }

    public static void reset()
    {
        INSTANCE.segments.clear();
        INSTANCE.config = null;
    }
}
