package com.hytalecolonies.components.jobs;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.buildertool.config.BlockTypeListAsset;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class WoodsmanWorkStationComponent implements Component<ChunkStore> {

    private static final String DEFAULT_TREE_TYPE_LIST = "TreeWood";

    // ===== Codec =====
    public static final BuilderCodec<WoodsmanWorkStationComponent> CODEC = BuilderCodec
            .builder(WoodsmanWorkStationComponent.class, WoodsmanWorkStationComponent::new)
            .append(new KeyedCodec<>("TreeSearchRadius", Codec.FLOAT),
                    (o, v) -> o.treeSearchRadius = v,
                    o -> o.treeSearchRadius)
            .add()
            .append(new KeyedCodec<>("AllowedTreeTypes", new SetCodec<>(Codec.STRING, HashSet::new, false)),
                    (o, v) -> o.allowedTreeTypes = v,
                    o -> o.allowedTreeTypes)
            .add()
            .build();

    /** Search radius for harvestable trees. */
    public float treeSearchRadius = 64.0f;
    /** Allowed block type keys; null = lazy-load {@value DEFAULT_TREE_TYPE_LIST}. */
    public @Nullable Set<String> allowedTreeTypes = null;

    // ===== Constructors =====
    public WoodsmanWorkStationComponent() {}

    // ===== Component Type =====
    public static ComponentType<ChunkStore, WoodsmanWorkStationComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getWoodsmanWorkStationComponentType();
    }

    // ===== Clone =====
    @Override
    public @Nullable Component<ChunkStore> clone() {
        WoodsmanWorkStationComponent copy = new WoodsmanWorkStationComponent();
        copy.treeSearchRadius = this.treeSearchRadius;
        copy.allowedTreeTypes = this.allowedTreeTypes != null ? new HashSet<>(this.allowedTreeTypes) : null;
        return copy;
    }

    /** Lazily loads the default tree wood block type list if not explicitly set. */
    public Set<String> getAllowedTreeTypes() {
        if (allowedTreeTypes == null) {
            BlockTypeListAsset asset = BlockTypeListAsset.getAssetMap().getAsset(DEFAULT_TREE_TYPE_LIST);
            allowedTreeTypes = asset != null ? asset.getBlockTypeKeys() : Collections.emptySet();
        }
        return allowedTreeTypes;
    }
}
