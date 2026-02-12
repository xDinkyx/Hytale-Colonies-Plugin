package com.colonies.coloniesplugin.components.jobs;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public class ColonistJobComponent implements Component<EntityStore> {

//    public static final BuilderCodec<ColonistJobComponent> CODEC = BuilderCodec.builder(ColonistJobComponent.class, ColonistJobComponent::new)
//            .append(new KeyedCodec<>("JobProviderEntityIndex", Vector3i.CODEC), (colonistJobComponent, s) -> colonistJobComponent.jobProviderBlockPosition = s, colonistJobComponent -> colonistJobComponent.jobProviderBlockPosition)
//            .add()
//            .append(new KeyedCodec<>("CurrentTask", Codec.STRING), (colonistJobComponent, s) -> colonistJobComponent.CurrentTask = s, colonistJobComponent -> colonistJobComponent.CurrentTask)
//            .add()
//            .build();

    public @Nullable Vector3i jobProviderBlockPosition = null;
    public @Nullable String CurrentTask = null; // TRAVELING, WORKING, REFUELING // ToDo: Make this an enum?

    public ColonistJobComponent() {}

    @Override
    public @Nullable Component<EntityStore> clone() {
        ColonistJobComponent copy = new ColonistJobComponent();
        copy.jobProviderBlockPosition = this.jobProviderBlockPosition;
        copy.CurrentTask = this.CurrentTask;
        return copy;
    }

    public boolean isEmployed() {
        return jobProviderBlockPosition != null;
    }}
