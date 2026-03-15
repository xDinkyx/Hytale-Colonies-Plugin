package com.hytalecolonies.components.world;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

/**
 * Component representing a harvestable tree structure, identified by the
 * TreeScannerSystem and its ITreeDetector implementations.
 */
public class HarvestableTreeComponent implements Component<ChunkStore> {

    // ===== Codec =====
    public static final BuilderCodec<HarvestableTreeComponent> CODEC = BuilderCodec
            .builder(HarvestableTreeComponent.class, HarvestableTreeComponent::new)
            .append(new KeyedCodec<>("TreeTypeKey", Codec.STRING),
                    (o, v) -> o.treeTypeKey = v,
                    o -> o.treeTypeKey)
            .add()
            .append(new KeyedCodec<>("MarkedForHarvest", Codec.BOOLEAN),
                    (o, v) -> o.markedForHarvest = v,
                    o -> o.markedForHarvest)
            .add()
            .append(new KeyedCodec<>("WoodCount", Codec.INTEGER),
                    (o, v) -> o.woodCount = v,
                    o -> o.woodCount)
            .add()
            .append(new KeyedCodec<>("BasePosition", Vector3i.CODEC),
                    (o, v) -> o.basePosition = v,
                    o -> o.basePosition)
            .add()
            .build();

    // ===== Fields =====
    String treeTypeKey; // The tree wood block type.
    boolean markedForHarvest = false; // Set to true when an NPC claims this tree for harvesting.
    int woodCount; // Number of wood (trunk) blocks in this tree.
    Vector3i basePosition; // World position of the lowest wood block (the "base" of the tree).

    // ===== Constructors =====
    public HarvestableTreeComponent() {
    }

    public HarvestableTreeComponent(String treeTypeKey, int woodCount, Vector3i basePosition) {
        this.treeTypeKey = treeTypeKey;
        this.woodCount = woodCount;
        this.basePosition = basePosition;
    }

    // ===== Component Type =====
    public static ComponentType<ChunkStore, HarvestableTreeComponent> getComponentType() {
        return com.hytalecolonies.HytaleColoniesPlugin.getInstance().getHarvestableTreeComponentType();
    }

    // ===== Component Clone =====
    @Override
    public HarvestableTreeComponent clone() {
        HarvestableTreeComponent clone = new HarvestableTreeComponent();
        clone.treeTypeKey = this.treeTypeKey;
        clone.markedForHarvest = this.markedForHarvest;
        clone.woodCount = this.woodCount;
        clone.basePosition = this.basePosition;
        return clone;
    }

    // ===== Getters/Setters =====
    public String getTreeTypeKey() {
        return treeTypeKey;
    }

    public boolean isMarkedForHarvest() {
        return markedForHarvest;
    }

    public int getWoodCount() {
        return woodCount;
    }

    public Vector3i getBasePosition() {
        return basePosition;
    }

    public void markForHarvest() {
        this.markedForHarvest = true;
    }

    public void setMarkedForHarvest(boolean markedForHarvest) {
        this.markedForHarvest = markedForHarvest;
    }

    public void setWoodCount(int woodCount) {
        this.woodCount = woodCount;
    }

    public void setBasePosition(Vector3i basePosition) {
        this.basePosition = basePosition;
    }

    public void setTreeTypeKey(String treeTypeKey) {
        this.treeTypeKey = treeTypeKey;
    }

}