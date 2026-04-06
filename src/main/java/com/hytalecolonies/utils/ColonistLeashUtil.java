package com.hytalecolonies.utils;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

/**
 * Helpers for updating an NPC's leash point so that {@code WanderInCircle}
 * (which reads {@link NPCEntity#getLeashPoint()}) constrains wander to the
 * correct area instead of the NPC's spawn position.
 */
public final class ColonistLeashUtil
{
    private ColonistLeashUtil()
    {
    }

    /**
     * Sets the NPC's leash point to the given world position. No-ops if the
     * entity has no {@link NPCEntity} component.
     */
    public static void setLeash(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Vector3d position)
    {
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null)
        {
            return;
        }
        npc.getLeashPoint().assign(position);
    }

    /**
     * Sets the NPC's leash point to the centre of {@code blockPos} (x+0.5, y,
     * z+0.5), which is the canonical standing position in front of a block.
     */
    public static void setLeashToBlockCenter(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Vector3i blockPos)
    {
        setLeash(ref, store, new Vector3d(blockPos.x + 0.5, blockPos.y, blockPos.z + 0.5));
    }
}
