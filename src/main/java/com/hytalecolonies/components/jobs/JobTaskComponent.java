package com.hytalecolonies.components.jobs;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.HytaleColoniesPlugin;

/** Contains data about the current task a colonist is performing. */
public class JobTaskComponent implements Component<EntityStore>
{
    public static final BuilderCodec<JobTaskComponent> CODEC =
            BuilderCodec.builder(JobTaskComponent.class, JobTaskComponent::new)
                    .append(new KeyedCodec<>("RequiredItems", ItemRequirement.ARRAY_CODEC), (o, v) -> o.requiredItems = v, o -> o.requiredItems)
                    .add()
                    .build();

    public ItemRequirement[] requiredItems = new ItemRequirement[0];

    public JobTaskComponent() {}

    public static ComponentType<EntityStore, JobTaskComponent> getComponentType()
    {
        return HytaleColoniesPlugin.getInstance().getJobTaskComponentType();
    }

    @Override
    public Component<EntityStore> clone()
    {
        JobTaskComponent copy = new JobTaskComponent();
        copy.requiredItems = this.requiredItems.clone();
        return copy;
    }
}
