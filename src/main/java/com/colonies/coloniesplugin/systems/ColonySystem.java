package com.colonies.coloniesplugin.systems;

import com.colonies.coloniesplugin.components.ColonistComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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

        // Log the colonist's name and level each tick
        System.out.println("Ticking Colonist: " + colonist.getColonistName() + " | Level: " + colonist.getColonistLevel());
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(this.colonistComponentType);
    }
}
