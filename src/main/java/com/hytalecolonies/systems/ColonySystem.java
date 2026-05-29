package com.hytalecolonies.systems;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hytalecolonies.components.npc.ColonistComponent;

public class ColonySystem extends EntityTickingSystem<EntityStore>
{
    private final ComponentType<EntityStore, ColonistComponent> colonistComponentType;

    public ColonySystem(ComponentType<EntityStore, ColonistComponent> colonistComponentType)
    {
        this.colonistComponentType = colonistComponentType;
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer)
    {
        ColonistComponent colonist = archetypeChunk.getComponent(index, colonistComponentType);
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);

        assert colonist != null;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery()
    {
        return Query.and(this.colonistComponentType);
    }
}
