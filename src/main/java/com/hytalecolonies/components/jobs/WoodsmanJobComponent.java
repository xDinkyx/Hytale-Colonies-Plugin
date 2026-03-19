package com.hytalecolonies.components.jobs;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.asset.type.buildertool.config.BlockTypeListAsset;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class WoodsmanJobComponent implements Component<EntityStore> {

    // ===== Codec =====
    public static final BuilderCodec<WoodsmanJobComponent> CODEC = BuilderCodec
            .builder(WoodsmanJobComponent.class, WoodsmanJobComponent::new)
            .append(new KeyedCodec<>("AllowedTreeTypes", new SetCodec<>(Codec.STRING, HashSet::new, false)),
                    (o, v) -> o.allowedTreeTypes = v,
                    o -> o.allowedTreeTypes)
            .add()
            .append(new KeyedCodec<>("TreeSearchRadius", Codec.FLOAT),
                    (o, v) -> o.treeSearchRadius = v,
                    o -> o.treeSearchRadius)
            .add()
            .build();

    // ===== Fields =====
    private static final String TREE_WOOD_LIST_ID   = "TreeWood";

    public Set<String> allowedTreeTypes = getTreeWoodKeys();
    public float treeSearchRadius = 64.0f;

    // Transient runtime state — not persisted, reset on server restart.
    public @Nullable Vector3i targetTreePosition = null;
    public @Nullable Vector3i lastKnownPosition = null;
    public int stuckTicks = 0;

    // ===== Constructors =====
    public WoodsmanJobComponent() {}

    public WoodsmanJobComponent(Set<String> allowedTreeTypes, float treeSearchRadius) {
        this.allowedTreeTypes = allowedTreeTypes;
        this.treeSearchRadius = treeSearchRadius;
    }

    // ===== Component Type =====
    public static ComponentType<EntityStore, WoodsmanJobComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getWoodsmanJobComponentType();
    }

    // ===== Cloneable =====
    @Override
    public @Nullable Component<EntityStore> clone() {
        return new WoodsmanJobComponent(this.allowedTreeTypes, this.treeSearchRadius);
    }

    // -------------------------------------------------------------------------
    // Asset key set helpers
    // -------------------------------------------------------------------------

    private Set<String> getTreeWoodKeys() {
        if (allowedTreeTypes == null) {
            BlockTypeListAsset asset = BlockTypeListAsset.getAssetMap().getAsset(TREE_WOOD_LIST_ID);
            allowedTreeTypes = asset != null ? asset.getBlockTypeKeys() : Collections.emptySet();
        }
        return allowedTreeTypes;
    }


}
