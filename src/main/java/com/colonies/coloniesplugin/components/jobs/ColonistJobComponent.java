package com.colonies.coloniesplugin.components.jobs;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class ColonistJobComponent implements Component<EntityStore> {

    public static final BuilderCodec<ColonistJobComponent> CODEC = BuilderCodec.builder(ColonistJobComponent.class, ColonistJobComponent::new)
            .append(new KeyedCodec<>("JobProviderEntityIndex", Codec.UUID_STRING), (colonistJobComponent, s) -> colonistJobComponent.JobProviderEntityUUID = s, colonistJobComponent -> colonistJobComponent.JobProviderEntityUUID)
            .add()
            .append(new KeyedCodec<>("CurrentTask", Codec.STRING), (colonistJobComponent, s) -> colonistJobComponent.CurrentTask = s, colonistJobComponent -> colonistJobComponent.CurrentTask)
            .add()
            .build();

    public UUID JobProviderEntityUUID; // UUID of JobProvider component this colonist is currently working for.
    public String CurrentTask = "IDLE"; // TRAVELING, WORKING, REFUELING // ToDo: Make this an enum?

    public ColonistJobComponent() {}

    @Override
    public @Nullable Component<EntityStore> clone() {
        ColonistJobComponent copy = new ColonistJobComponent();
        copy.JobProviderEntityUUID = this.JobProviderEntityUUID;
        copy.CurrentTask = this.CurrentTask;
        return copy;
    }
}
