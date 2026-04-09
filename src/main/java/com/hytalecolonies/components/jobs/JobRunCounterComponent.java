package com.hytalecolonies.components.jobs;

import javax.annotation.Nullable;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Persisted blocks-processed counter for a colonist's current work run. Reset to zero at the start of each new run. */
public class JobRunCounterComponent implements Component<EntityStore> {

    // ===== Codec =====
    public static final BuilderCodec<JobRunCounterComponent> CODEC = BuilderCodec
            .builder(JobRunCounterComponent.class, JobRunCounterComponent::new)
            .append(new KeyedCodec<>("Count", Codec.INTEGER),
                    (o, v) -> o.count = v,
                    o -> o.count)
            .add()
            .build();

    // ===== Persisted runtime state =====
    /** Blocks processed in the current work run. Reset by each job's working system per-run. */
    public int count = 0;

    // ===== Constructors =====
    public JobRunCounterComponent() {}
    
    public JobRunCounterComponent(int count) {
        this.count = count;
    }

    // ===== Component Type =====
    public static ComponentType<EntityStore, JobRunCounterComponent> getComponentType() {
        return HytaleColoniesPlugin.getInstance().getJobRunCounterComponentType();
    }

    // ===== Cloneable =====
    @Override
    public @Nullable Component<EntityStore> clone() {
        JobRunCounterComponent copy = new JobRunCounterComponent();
        copy.count = this.count;
        return copy;
    }
}
