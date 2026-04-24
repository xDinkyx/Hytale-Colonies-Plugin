package com.hytalecolonies.systems.npc;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

/**
 * Fires when a {@link MoveToTargetComponent} is added to an NPC entity.
 * Writes the target position to the NPC role's "NavTarget" stored position slot
 * (slot 0). The colonist role JSON has a flat ReadPosition + Seek instruction
 * that activates automatically when this slot is set, driving A* pathfinding.
 * The sensor becomes inactive again once the NPC reaches within MinRange of the target.
 */
public class PathFindingSystem extends RefChangeSystem<EntityStore, MoveToTargetComponent> {

    /**
     * Slot index for the "NavTarget" stored position in the colonist role JSON.
     * Corresponds to the first named position slot used in Template_Colonist.json.
     */
    private static final int NAV_TARGET_SLOT = 0;

    @Override
    public ComponentType<EntityStore, MoveToTargetComponent> componentType() {
        return MoveToTargetComponent.getComponentType();
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull MoveToTargetComponent component,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        // Remove the trigger component immediately -- this is a one-shot navigation request.
        commandBuffer.removeComponent(ref, MoveToTargetComponent.getComponentType());

        NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
        if (npcEntity == null) {
            DebugLog.warning(DebugCategory.MOVEMENT, "PathFindingSystem: [%s] entity has no NPCEntity component, cannot navigate.",
                    DebugLog.npcId(ref, store));
            return;
        }

        Role role = npcEntity.getRole();
        if (role == null) {
            DebugLog.warning(DebugCategory.MOVEMENT, "PathFindingSystem: [%s] NPC role is null, cannot navigate.",
                    DebugLog.npcId(ref, store));
            return;
        }

        // Write the target to the "NavTarget" stored position slot (slot 0).
        // The ReadPosition sensor in Template_Colonist.json checks this slot
        // every tick and activates the Seek body motion while the NPC is outside MinRange.
        try {
            role.getMarkedEntitySupport().getStoredPosition(NAV_TARGET_SLOT).assign(component.target);
        } catch (NullPointerException e) {
            DebugLog.warning(DebugCategory.MOVEMENT,
                    "PathFindingSystem: [%s] role has no stored position slot %d -- is Colonist_Miner.json loaded correctly?",
                    DebugLog.npcId(ref, store), NAV_TARGET_SLOT);
            return;
        }

        // Debug visualization -- blue = NPC position, red = target, green line = intent.
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform != null && HytaleColoniesPlugin.getInstance().getDebugConfig().get().isDrawColonistPaths()) {
            showDebugPath(
                    store.getExternalData().getWorld(),
                    transform.getTransform().getPosition(),
                    component.target);
        }
    }

    @Override
    public void onComponentSet(
            @Nonnull Ref<EntityStore> ref,
            @Nullable MoveToTargetComponent oldComponent,
            @Nonnull MoveToTargetComponent newComponent,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Update the navigation target if the component is replaced with a new destination.
        onComponentAdded(ref, newComponent, store, commandBuffer);
    }

    @Override
    public void onComponentRemoved(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull MoveToTargetComponent component,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return Query.and(MoveToTargetComponent.getComponentType());
    }

    private void showDebugPath(@Nonnull World world, @Nonnull Vector3d from, @Nonnull Vector3d to) {
        DebugUtils.addSphere(world, from, DebugUtils.COLOR_BLUE, 0.5, 20.0f);
        DebugUtils.addSphere(world, to, DebugUtils.COLOR_RED, 0.5, 20.0f);
        DebugUtils.addLine(world, from, to, DebugUtils.COLOR_GREEN, 0.1, 20.0f, 0);
    }
}