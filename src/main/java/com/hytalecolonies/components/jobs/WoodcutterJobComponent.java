package com.hytalecolonies.components.jobs;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.set.SetCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

public class WoodcutterJobComponent implements Component<EntityStore> {

    // ===== Codec =====
    public static final BuilderCodec<WoodcutterJobComponent> CODEC = BuilderCodec
            .builder(WoodcutterJobComponent.class, WoodcutterJobComponent::new)
            .append(new KeyedCodec<>("AllowedTreeTypes", new SetCodec<>(Codec.STRING, HashSet::new, false)),
                    (o, v) -> o.allowedTreeTypes = v,
                    o -> o.allowedTreeTypes)
            .add()
            .append(new KeyedCodec<>("TreeSearchRadius", Codec.FLOAT),
                    (o, v) -> o.treeSearchRadius = v,
                    o -> o.treeSearchRadius)
            .add()
            .append(new KeyedCodec<>("TargetTreePosition", Vector3i.CODEC),
                    (o, v) -> o.targetTreePosition = v,
                    o -> o.targetTreePosition)
            .add()
            .build();

    // ToDo: Expand this list with all tree trunk types and make it configurable.
    public Set<String> allowedTreeTypes = Set.of("Wood_Ash_Trunk", "Wood_Oak_Trunk", "Wood_Beech_Trunk", "Wood_Birch_Trunk", "Wood_Cedar_Trunk", "Wood_Fir_Trunk");
    public float treeSearchRadius = 64.0f;

    /** The base position of the tree this colonist has claimed. Persisted so restart recovery can unmark the tree. */
    public @Nullable Vector3i targetTreePosition = null;

    /** Last recorded position while traveling, used for stuck detection. Transient. */
    public @Nullable Vector3i lastKnownPosition = null;
    /** How many consecutive ticks the position has been unchanged while traveling. Transient. */
    public int stuckTicks = 0;

    public WoodcutterJobComponent() {}

    public WoodcutterJobComponent(Set<String> allowedTreeTypes, float treeSearchRadius) {
        this.allowedTreeTypes = allowedTreeTypes;
        this.treeSearchRadius = treeSearchRadius;
    }

    // ===== Component Type =====
    public static ComponentType<EntityStore, WoodcutterJobComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getWoodCutterJobComponentType();
    }

    @Override
    public @Nullable Component<EntityStore> clone() {
        WoodcutterJobComponent copy = new WoodcutterJobComponent(this.allowedTreeTypes, this.treeSearchRadius);
        copy.targetTreePosition = this.targetTreePosition;
        copy.lastKnownPosition = this.lastKnownPosition;
        copy.stuckTicks = this.stuckTicks;
        return copy;
    }
}
