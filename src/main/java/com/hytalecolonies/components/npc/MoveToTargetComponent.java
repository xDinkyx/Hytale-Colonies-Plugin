package com.hytalecolonies.components.npc;

import javax.annotation.Nullable;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class MoveToTargetComponent implements Component<EntityStore> {

    public Vector3d target;

    public MoveToTargetComponent() {}

    public MoveToTargetComponent(Vector3d target) {
        this.target = target;
    }

    // ===== Component Type =====
    public static ComponentType<EntityStore, MoveToTargetComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getMoveToTargetComponentType();
    }

    @Override
    public @Nullable Component<EntityStore> clone() {
        return new MoveToTargetComponent(this.target);
    }
}
