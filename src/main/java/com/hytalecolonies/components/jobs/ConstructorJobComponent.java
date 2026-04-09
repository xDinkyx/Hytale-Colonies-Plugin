package com.hytalecolonies.components.jobs;

import javax.annotation.Nullable;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Marker component identifying a colonist as a constructor. */
public class ConstructorJobComponent implements Component<EntityStore> {

    // ===== Codec =====
    public static final BuilderCodec<ConstructorJobComponent> CODEC = BuilderCodec
            .builder(ConstructorJobComponent.class, ConstructorJobComponent::new)
            .build();

    // ===== Constructors =====
    public ConstructorJobComponent() {}

    // ===== Component Type =====
    public static ComponentType<EntityStore, ConstructorJobComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getConstructorJobComponentType();
    }

    // ===== Cloneable =====
    @Override
    public @Nullable Component<EntityStore> clone() {
        return new ConstructorJobComponent();
    }
}
