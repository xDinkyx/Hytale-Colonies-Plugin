package com.colonies.coloniesplugin.components.jobs;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public class WoodcutterJobComponent implements Component<EntityStore> {

    // ToDo: Expand this list with all tree trunk types and make it configurable.
    public Set<String> allowedTreeTypes = Set.of("Wood_Ash_Trunk", "Wood_Oak_Trunk", "Wood_Beech_Trunk", "Wood_Birch_Trunk", "Wood_Cedar_Trunk", "Wood_Fir_Trunk");
    public float treeSearchRadius = 15.0f;

    public WoodcutterJobComponent() {}

    public WoodcutterJobComponent(Set<String> allowedTreeTypes, float treeSearchRadius) {
        this.allowedTreeTypes = allowedTreeTypes;
        this.treeSearchRadius = treeSearchRadius;
    }

    @Override
    public @Nullable Component<EntityStore> clone() {
        return new WoodcutterJobComponent(this.allowedTreeTypes, this.treeSearchRadius);
    }
}
