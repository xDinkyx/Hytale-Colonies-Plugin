package com.hytalecolonies.components.jobs;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Added to colonist when they are unemployed. Does not contain any data, just used as a marker.
 */
public class UnemployedComponent implements Component<EntityStore> {

    public static final BuilderCodec<UnemployedComponent> CODEC = BuilderCodec.builder(UnemployedComponent.class, UnemployedComponent::new)
            .build();

    public static ComponentType<EntityStore, UnemployedComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getUnemployedComponentType();
    }

    @Override
    public @Nullable Component<EntityStore> clone() {
        return new UnemployedComponent();
    }
}
