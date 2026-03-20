package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Continuously picks up nearby dropped items for every colonist
 * — mirroring how players auto-collect items.
 *
 * <p>Runs at a 0.5 s cadence (vs. the ~0.25 s player pickup throttle) so
 * players maintain a slight pickup priority over colonists when both are
 * competing for the same drop.
 * ToDo: Maybe there's a better way to handle pickup priority? Like checking if a player is near.
 *
 * <p>Full-inventory handling is automatic: {@link ItemComponent#addToItemContainer}
 * sets a short retry delay on the item entity when the container is full, so
 * the colonist will re-attempt on the next tick once space is freed.
 *
 * <h3>NPC inventory defaults</h3>
 * By default an NPC has 3 hotbar slots and 0 storage slots. The colonist base
 * template overrides that expands their inventory size.
 */
public class ColonistItemPickupSystem extends DelayedEntitySystem<EntityStore> {

    /** Radius (blocks) within which a colonist will collect dropped items. */
    public static final float PICKUP_RADIUS = 5.0f;

    private final Query<EntityStore> query = Query.and(ColonistComponent.getComponentType());

    public ColonistItemPickupSystem() {
        super(0.5f); // 0.5 s — hopefully gives players a mild pickup priority over colonists.
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        LivingEntity colonist = (LivingEntity) EntityUtils.getEntity(ref, store);
        if (colonist == null) return;

        // Pass commandBuffer alongside store: reads go through store, the entity
        // removal is deferred via commandBuffer to avoid IllegalStateException.
        pickUpNearbyItems(transform.getPosition(), colonist.getInventory().getCombinedStorageFirst(), store, commandBuffer);
    }

    /**
     * Queries the item spatial index for dropped items within {@link #PICKUP_RADIUS}
     * of {@code position} and adds each eligible one to {@code container}.
     *
     * <p>Reads use {@code store}; the entity removal is deferred through
     * {@code commandBuffer} to avoid {@link IllegalStateException} when calling
     * {@code store.removeEntity} during a tick.
     */
    private static void pickUpNearbyItems(@Nonnull com.hypixel.hytale.math.vector.Vector3d position,
                                          @Nonnull ItemContainer container,
                                          @Nonnull Store<EntityStore> store,
                                          @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        SpatialResource<Ref<EntityStore>, EntityStore> spatialResource =
                store.getResource(EntityModule.get().getItemSpatialResourceType());
        List<Ref<EntityStore>> nearbyItems = new ArrayList<>();
        spatialResource.getSpatialStructure().ordered(position, PICKUP_RADIUS, nearbyItems);

        for (Ref<EntityStore> itemRef : nearbyItems) {
            if (!itemRef.isValid()) continue;
            ItemComponent itemComponent = store.getComponent(itemRef, ItemComponent.getComponentType());
            if (itemComponent == null || !itemComponent.canPickUp()) continue;

            ItemStack itemStack = itemComponent.getItemStack();
            if (itemStack == null) continue;

            ItemStackTransaction transaction = container.addItemStack(itemStack);
            ItemStack remainder = transaction.getRemainder();
            if (remainder != null && !remainder.isEmpty()) {
                // Partial pickup — update the item entity with the remainder and throttle retries.
                itemComponent.setPickupDelay(0.25f);
                itemComponent.setItemStack(remainder);
                int pickedQty = itemStack.getQuantity() - remainder.getQuantity();
                if (pickedQty > 0) {
                    DebugLog.fine(DebugCategory.WOODSMAN_JOB, "[ItemPickup] Partially picked up %dx %s.",
                            pickedQty, itemStack.getItemId());
                }
            } else {
                // Full pickup — defer the entity removal to end-of-tick via CommandBuffer.
                commandBuffer.removeEntity(itemRef, RemoveReason.REMOVE);
                DebugLog.fine(DebugCategory.WOODSMAN_JOB, "[ItemPickup] Picked up %dx %s.",
                        itemStack.getQuantity(), itemStack.getItemId());
            }
        }
    }
}
