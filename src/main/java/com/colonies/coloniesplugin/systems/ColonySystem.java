package com.colonies.coloniesplugin.systems;

import com.colonies.coloniesplugin.ColoniesPlugin;
import com.colonies.coloniesplugin.components.npc.ColonistComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class ColonySystem extends EntityTickingSystem<EntityStore> {

    private final ComponentType<EntityStore, ColonistComponent> colonistComponentType;

    public ColonySystem(ComponentType<EntityStore, ColonistComponent> colonistComponentType) {
        this.colonistComponentType = colonistComponentType;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        ColonistComponent colonist = archetypeChunk.getComponent(index, colonistComponentType);
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);

        assert colonist != null;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(this.colonistComponentType);
    }
}
