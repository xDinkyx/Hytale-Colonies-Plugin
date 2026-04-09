package com.hytalecolonies.interactions;

import com.hytalecolonies.components.jobs.ConstructionOrderComponent;
import com.hytalecolonies.components.jobs.JobType;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.builtin.buildertools.prefabeditor.PrefabEditSession;
import com.hypixel.hytale.builtin.buildertools.prefabeditor.PrefabEditSessionManager;
import com.hypixel.hytale.builtin.buildertools.prefabeditor.PrefabEditingMetadata;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Assigns a prefab from the player's active prefab edit session as a build
 * order on the nearest constructor workstation.
 *
 * <p>Mirrors {@code PrefabSelectionInteraction}: primary click selects the
 * prefab by target-block bounding-box; secondary click selects the nearest
 * prefab by XZ distance. Instead of calling {@code setSelectedPrefab}, the
 * interaction writes a {@link ConstructionOrderComponent} onto the closest
 * Constructor workstation block entity in the player's origin world.
 */
public class AssignBuildOrderInteraction extends SimpleInstantInteraction {

    private static final int WORKSTATION_SEARCH_RADIUS_XZ = 32;
    private static final int WORKSTATION_SEARCH_RADIUS_Y  = 16;

    @Nonnull
    private static final Message MSG_NOT_IN_EDIT_SESSION = Message.translation(
            "server.commands.editprefab.notInEditSession");
    @Nonnull
    private static final Message MSG_NO_TARGET_FOUND = Message.translation(
            "items.Tool_Colony_Constructor_Assign.error.noTarget");
    @Nonnull
    private static final Message MSG_NO_PREFAB_FOUND = Message.translation(
            "items.Tool_Colony_Constructor_Assign.error.noPrefab");
    @Nonnull
    private static final Message MSG_NO_WORKSTATION = Message.translation(
            "items.Tool_Colony_Constructor_Assign.error.noWorkstation");
    @Nonnull
    private static final Message MSG_ORDER_ASSIGNED = Message.translation(
            "items.Tool_Colony_Constructor_Assign.orderAssigned");

    @Nonnull
    public static final BuilderCodec<AssignBuildOrderInteraction> CODEC = BuilderCodec
            .builder(AssignBuildOrderInteraction.class, AssignBuildOrderInteraction::new,
                    SimpleInstantInteraction.CODEC)
            .documentation("Assigns the selected prefab edit-session prefab as a build order "
                    + "on the nearest Constructor workstation in the player's origin world.")
            .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type,
                            @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        assert commandBuffer != null;

        Ref<EntityStore> ref = context.getEntity();
        Player playerComponent = commandBuffer.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) return;

        if (type != InteractionType.Primary && type != InteractionType.Secondary) return;

        UUIDComponent uuidComponent = commandBuffer.getComponent(ref, UUIDComponent.getComponentType());
        assert uuidComponent != null;
        UUID playerUuid = uuidComponent.getUuid();

        // Must be in a prefab edit session.
        PrefabEditSessionManager sessionManager = BuilderToolsPlugin.get().getPrefabEditSessionManager();
        PrefabEditSession session = sessionManager.getPrefabEditSession(playerUuid);
        if (session == null) {
            playerComponent.sendMessage(MSG_NOT_IN_EDIT_SESSION);
            return;
        }

        // Find the prefab metadata.
        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        assert transform != null;
        Vector3d playerPos = transform.getPosition();

        PrefabEditingMetadata metadata = null;

        if (type == InteractionType.Secondary) {
            // Find nearest prefab by XZ distance to player.
            Vector3d playerXZ = new Vector3d(playerPos.x, 0.0, playerPos.z);
            double minDist = Double.MAX_VALUE;
            for (PrefabEditingMetadata m : session.getLoadedPrefabMetadata().values()) {
                Vector3d center = new Vector3d(
                        (m.getMaxPoint().x + m.getMinPoint().x) / 2.0,
                        0.0,
                        (m.getMaxPoint().z + m.getMinPoint().z) / 2.0);
                double dist = center.distanceTo(playerXZ);
                if (dist < minDist) {
                    minDist = dist;
                    metadata = m;
                }
            }
        } else {
            // Primary: find prefab whose bounding box contains the target block.
            Vector3i targetLocation = getTargetLocation(ref, commandBuffer);
            if (targetLocation == null) {
                playerComponent.sendMessage(MSG_NO_TARGET_FOUND);
                return;
            }
            for (PrefabEditingMetadata m : session.getLoadedPrefabMetadata().values()) {
                if (m.isLocationWithinPrefabBoundingBox(targetLocation)) {
                    metadata = m;
                    break;
                }
            }
        }

        if (metadata == null) {
            playerComponent.sendMessage(MSG_NO_PREFAB_FOUND);
            return;
        }

        // Derive the prefab ID: path relative to the server prefabs directory.
        Path serverPrefabsPath = PrefabStore.get().getServerPrefabsPath();
        String prefabId = serverPrefabsPath
                .relativize(metadata.getPrefabPath().toAbsolutePath().normalize())
                .toString();

        // Resolve the origin world (the world the player came from before entering the editor).
        UUID originWorldUuid = session.getWorldArrivedFrom();
        World mainWorld = (originWorldUuid != null)
                ? Universe.get().getWorld(originWorldUuid)
                : commandBuffer.getExternalData().getWorld();
        if (mainWorld == null) {
            mainWorld = commandBuffer.getExternalData().getWorld();
        }

        // Use the player's last known position in the origin world as the search centre.
        Transform arrivedFrom = session.getTransformArrivedFrom();
        final int searchX = (arrivedFrom != null) ? (int) arrivedFrom.getPosition().x : (int) playerPos.x;
        final int searchY = (arrivedFrom != null) ? (int) arrivedFrom.getPosition().y : (int) playerPos.y;
        final int searchZ = (arrivedFrom != null) ? (int) arrivedFrom.getPosition().z : (int) playerPos.z;

        final String finalPrefabId = prefabId;
        final World finalMainWorld = mainWorld;

        // Schedule workstation scan and assignment on the main-world thread.
        finalMainWorld.execute(() -> {
            Store<ChunkStore> chunkStore = finalMainWorld.getChunkStore().getStore();

            Vector3i wsPos = findNearestConstructorWorkstation(finalMainWorld, chunkStore, searchX, searchY, searchZ);
            if (wsPos == null) {
                DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                        "[AssignBuildOrder] No constructor workstation found within %d blocks of %d,%d,%d.",
                        WORKSTATION_SEARCH_RADIUS_XZ, searchX, searchY, searchZ);
                return;
            }

            Ref<ChunkStore> wsBlockRef = BlockModule.getBlockEntity(finalMainWorld, wsPos.x, wsPos.y, wsPos.z);
            if (wsBlockRef == null || !wsBlockRef.isValid()) return;

            WorkStationComponent ws = chunkStore.getComponent(wsBlockRef, WorkStationComponent.getComponentType());
            if (ws == null) return;

            // Point the workstation's active order origin at itself so ConstructorUtil
            // can retrieve the ConstructionOrderComponent from this block entity.
            ws.activeConstructionOrderOrigin = wsPos.clone();

            chunkStore.addComponent(wsBlockRef, ConstructionOrderComponent.getComponentType(),
                    new ConstructionOrderComponent(finalPrefabId, wsPos));

            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                    "[AssignBuildOrder] Prefab '%s' assigned as build order to workstation at %s.",
                    finalPrefabId, wsPos);
        });

        // Confirmation message is sent on the entity thread before the world.execute runs.
        playerComponent.sendMessage(MSG_ORDER_ASSIGNED);
    }

    /** Scans a bounding box around {@code (cx, cy, cz)} for the nearest Constructor workstation. */
    @Nullable
    private static Vector3i findNearestConstructorWorkstation(
            @Nonnull World world,
            @Nonnull Store<ChunkStore> chunkStore,
            int cx, int cy, int cz) {

        Vector3i nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int dx = -WORKSTATION_SEARCH_RADIUS_XZ; dx <= WORKSTATION_SEARCH_RADIUS_XZ; dx++) {
            for (int dz = -WORKSTATION_SEARCH_RADIUS_XZ; dz <= WORKSTATION_SEARCH_RADIUS_XZ; dz++) {
                for (int dy = -WORKSTATION_SEARCH_RADIUS_Y; dy <= WORKSTATION_SEARCH_RADIUS_Y; dy++) {
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, x, y, z);
                    if (blockRef == null || !blockRef.isValid()) continue;
                    WorkStationComponent ws = chunkStore.getComponent(
                            blockRef, WorkStationComponent.getComponentType());
                    if (ws == null || ws.getJobType() != JobType.Constructor) continue;
                    double distSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
                    if (distSq < nearestDistSq) {
                        nearestDistSq = distSq;
                        nearest = new Vector3i(x, y, z);
                    }
                }
            }
        }
        return nearest;
    }

    @Nullable
    private static Vector3i getTargetLocation(@Nonnull Ref<EntityStore> ref,
                                              @Nonnull ComponentAccessor<EntityStore> componentAccessor) {
        Vector3i targetBlock = TargetUtil.getTargetBlock(ref, 200.0, componentAccessor);
        if (targetBlock != null) {
            return targetBlock;
        }
        Ref<EntityStore> targetEntity = TargetUtil.getTargetEntity(ref, 50.0f, componentAccessor);
        if (targetEntity != null && targetEntity.isValid()) {
            TransformComponent tc = componentAccessor.getComponent(targetEntity, TransformComponent.getComponentType());
            if (tc != null) {
                Vector3d pos = tc.getPosition();
                return new Vector3i((int) pos.x, (int) pos.y, (int) pos.z);
            }
        }
        return null;
    }
}
