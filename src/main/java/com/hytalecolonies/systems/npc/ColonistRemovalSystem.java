package com.hytalecolonies.systems.npc;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

/**
 * Logs when a colonist is removed from the world and drops all items from
 * its inventory onto the ground at its last known position.
 *
 * <p>Triggered by {@code ColonistComponent} removal, which happens both when
 * the component is explicitly removed and when the entity is deleted entirely.
 * The entity's other components (TransformComponent, NPCEntity, etc.) are still
 * accessible through the store at this point.
 */
public class ColonistRemovalSystem extends RefChangeSystem<EntityStore, ColonistComponent> {

    private final Query<EntityStore> query = Query.and(ColonistComponent.getComponentType());

    @Nonnull
    @Override
    public ComponentType<EntityStore, ColonistComponent> componentType() {
        return ColonistComponent.getComponentType();
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ColonistComponent colonist,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // No action on add.
    }

    @Override
    public void onComponentSet(
            @Nonnull Ref<EntityStore> ref,
            @Nullable ColonistComponent previous,
            @Nonnull ColonistComponent next,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // No action on set.
    }

    @Override
    public void onComponentRemoved(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull ColonistComponent colonist,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        String uuidStr = uuid != null ? uuid.getUuid().toString() : "<unknown>";

        DebugLog.info(DebugCategory.COLONIST_LIFECYCLE,
                "[ColonistRemoval] Colonist '%s' (UUID: %s) removed from world.",
                colonist.getColonistName(), uuidStr);

        dropInventory(ref, store, commandBuffer);
    }

    /**
     * Collects all items from the colonist's hotbar and storage and drops them
     * as item entities at the colonist's current position.
     */
    private void dropInventory(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            DebugLog.warning(DebugCategory.COLONIST_LIFECYCLE,
                    "[ColonistRemoval] No TransformComponent -- cannot drop inventory.");
            return;
        }

        NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
        if (npcEntity == null || npcEntity.getInventory() == null) {
            return; // No inventory to drop.
        }

        List<ItemStack> toDrop = new ArrayList<>();
        collectItems(npcEntity.getInventory().getHotbar(), toDrop);
        collectItems(npcEntity.getInventory().getStorage(), toDrop);

        if (toDrop.isEmpty()) {
            return;
        }

        Vector3d dropPosition = transform.getPosition().clone().add(0.0, 0.5, 0.0);
        Holder<EntityStore>[] drops = ItemComponent.generateItemDrops(store, toDrop, dropPosition, Vector3f.ZERO);
        commandBuffer.addEntities(drops, AddReason.SPAWN);

        DebugLog.info(DebugCategory.COLONIST_LIFECYCLE,
                "[ColonistRemoval] Dropped %d item stack(s) at %s.",
                toDrop.size(), dropPosition);
    }

    /** Removes all non-empty stacks from {@code container} and adds them to {@code out}. */
    private void collectItems(@Nullable ItemContainer container, @Nonnull List<ItemStack> out) {
        if (container == null) return;
        List<ItemStack> dropped = container.dropAllItemStacks();
        if (dropped != null) {
            out.addAll(dropped);
        }
    }

    @Override
    @Nullable
    public Query<EntityStore> getQuery() {
        return query;
    }
}
