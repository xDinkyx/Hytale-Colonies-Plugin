package com.hytalecolonies.utils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joml.Vector3f;
import org.joml.Vector3i;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.Axis;
import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentPrefabPreview;
import com.hypixel.hytale.server.core.prefab.PrefabLoadException;
import com.hypixel.hytale.server.core.prefab.PrefabStore;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hytalecolonies.ConstructionOrderStore;
import com.hytalecolonies.components.jobs.ConstructorJobComponent;
import com.hytalecolonies.components.jobs.ItemRequirement;
import com.hytalecolonies.components.jobs.JobTaskComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.debug.DebugTiming;

/** Utility methods for the Constructor colonist job. */
public final class ConstructorUtil
{
    private static final String EMPTY_BLOCK_KEY   = "Empty";
    private static final String PREFABS_PREFIX     = "Server/Prefabs/";
    private static final String JSON_SUFFIX        = ".prefab.json";
    private static final String LPF_SUFFIX         = ".lpf";

    private ConstructorUtil() {}

    /**
     * Collects all non-empty prefab base blocks and returns them sorted by Y ascending so colonists build floor-first. Each entry is
     * {@code {lx, ly, lz, blockId, rotation}}. Filler slave cells are excluded -- they are auto-created (and auto-removed) by the engine when the base block is
     * placed or cleared.
     */
    private static List<int[]> sortedPrefabBlocks(BlockSelection prefab)
    {
        List<int[]> blocks = new ArrayList<>();
        prefab.forEachBlock((lx, ly, lz, block) -> {
            if (block.filler() != 0)
                return; // slave cell -- auto-managed by the engine via the base block
            blocks.add(new int[] {lx, ly, lz, block.blockId(), block.rotation()});
        });
        blocks.sort(Comparator.comparingInt((int[] b) -> b[1]).thenComparingInt(b -> b[0]).thenComparingInt(b -> b[2]));
        return blocks;
    }

    /**
     * Returns the cached sorted block list for {@code order}, building it on first use. Callers iterate forward (Y ascending) for building, or reversed for
     * clearing.
     */
    private static List<int[]> getSortedBlocks(ConstructionOrderStore.Entry order, BlockSelection prefab)
    {
        if (order.cachedSortedBlocks == null)
            order.cachedSortedBlocks = sortedPrefabBlocks(prefab);
        return order.cachedSortedBlocks;
    }

    /**
     * Returns {@code true} if {@code worldBlockId} satisfies the {@code prefabBlockId} requirement. E.g.: a placed {@code Soil_Dirt} block that has grown into
     * a {@code Soil_Grass} is considered as equivalent.
     *
     * <p>
     * <b> TODO: Expand for all blocks that are considered the same for construction purposes. </b>
     * </p>
     */
    private static boolean isBlockEquivalent(int worldBlockId, int prefabBlockId)
    {
        if (worldBlockId == prefabBlockId)
            return true;

        BlockType expected = BlockType.getAssetMap().getAsset(prefabBlockId);
        BlockType actual   = BlockType.getAssetMap().getAsset(worldBlockId);
        if (expected == null || actual == null)
            return false;

        String eKey = expected.getId();
        String aKey = actual.getId();
        if (!eKey.startsWith("Soil_Dirt") || !aKey.startsWith("Soil_Grass"))
            return false;

        // Plain Soil_Dirt accepts any Soil_Grass* variant.
        if (eKey.equals("Soil_Dirt"))
            return true;

        // Soil_Dirt_X accepts only Soil_Grass_X (e.g. Soil_Dirt_Cold ->
        // Soil_Grass_Cold).
        String suffix = eKey.substring("Soil_Dirt".length());
        return aKey.equals("Soil_Grass" + suffix);
    }

    /**
     * Returns the cached runtime selection if available, otherwise loads from the asset-pack ZipFS. {@code prefabId} is a ZipFS-internal path, e.g.
     * {@code /Server/Prefabs/test.prefab.json}. The loaded result is stored back on {@code entry.cachedSelection} for reuse.
     */
    @Nullable
    public static BlockSelection loadPrefab(@Nullable ConstructionOrderStore.Entry order)
    {
        if (order == null)
        {
            return null; // caller should log
        }

        if (order.prefabId == null || order.prefabId.isEmpty())
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorUtil] Order %s has no prefabId stored -- cannot load prefab.", order.id);
            return null;
        }

        // Prefer the entry's own cached selection.
        if (order.cachedSelection != null)
            return order.cachedSelection;

        // Fall back to the asset-pack ZipFS path (must use the live ZipFileSystem to
        // avoid cross-filesystem issues). Always loaded raw (unrotated) -- the order's own
        // rotationDegrees (chosen by the player at placement time) is applied explicitly here so
        // there is exactly one, deterministic source of truth for rotation.
        try
        {
            Path           packRoot   = AssetModule.get().getBaseAssetPack().getRoot();
            Path           prefabPath = packRoot.getFileSystem().getPath(order.prefabId);
            BlockSelection loaded     = PrefabStore.get().getPrefab(prefabPath);
            if (order.rotationDegrees != 0)
                loaded = loaded.rotate(Axis.Y, order.rotationDegrees);
            order.cachedSelection    = loaded;
            order.cachedSortedBlocks = sortedPrefabBlocks(loaded);
            return loaded;
        }
        catch (PrefabLoadException e)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorUtil] Failed to load prefab '%s' from asset pack: %s", order.prefabId, e.getMessage());
            return null;
        }
        catch (Exception e)
        {
            DebugLog.warning(DebugCategory.CONSTRUCTOR_JOB,
                             "[ConstructorUtil] Error loading prefab '%s': %s (%s)",
                             order.prefabId,
                             e.getMessage(),
                             e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Converts a stored prefab file identifier (e.g. {@code /Server/Prefabs/Foo.prefab.json}) into the browsable key {@link PrefabStore#findBrowsablePrefabPath}
     * expects (path relative to a pack's {@code Server/Prefabs/} dir, no suffix). Shared by the placement ghost and the construction-order hologram, both of
     * which resolve prefabs through {@link PersistentPrefabPreview}, which only accepts this key form.
     */
    @Nullable
    public static String derivePrefabKey(@Nullable String prefabId)
    {
        if (prefabId == null || prefabId.isEmpty())
            return null;

        String path = prefabId.replace('\\', '/');
        int    idx  = path.indexOf(PREFABS_PREFIX);
        if (idx >= 0)
            path = path.substring(idx + PREFABS_PREFIX.length());
        else if (path.startsWith("/"))
            path = path.substring(1);

        if (path.endsWith(JSON_SUFFIX))
            path = path.substring(0, path.length() - JSON_SUFFIX.length());
        else if (path.endsWith(LPF_SUFFIX))
            path = path.substring(0, path.length() - LPF_SUFFIX.length());

        return path.isEmpty() ? null : path;
    }

    /**
     * Returns the next world position inside the prefab footprint that needs to be cleared, or {@code null} when clearing is complete. Scans top-down (Y
     * descending) so floating blocks are cleared first.
     */
    @Nullable
    public static Vector3i findNextClearingTarget(@Nullable ConstructionOrderStore.Entry order, World world, BlockSelection prefab)
    {
        if (order == null || order.buildOrigin == null)
            return null;
        Vector3i origin = order.buildOrigin;

        int emptyId = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);

        try (var _ = DebugTiming.measure("ConstructorUtil.findNextClearingTarget", 50))
        {
            List<int[]> blocks = getSortedBlocks(order, prefab);
            for (int i = blocks.size() - 1; i >= 0; i--)
            {
                int[]   b             = blocks.get(i);
                int     prefabBlockId = b[3];
                boolean isAir         = (prefabBlockId == 0 || prefabBlockId == emptyId);

                int wx = b[0] + origin.x - prefab.getAnchorX();
                int wy = b[1] + origin.y - prefab.getAnchorY();
                int wz = b[2] + origin.z - prefab.getAnchorZ();

                int worldBlock = BlockReadUtil.getBlockId(world, wx, wy, wz);

                if (isAir)
                {
                    if (worldBlock != 0)
                        return new Vector3i(wx, wy, wz);
                }
                else
                {
                    if (worldBlock != 0 && !isBlockEquivalent(worldBlock, prefabBlockId))
                        return new Vector3i(wx, wy, wz);
                }
            }
        }

        return null;
    }

    /**
     * Returns the next world position inside the prefab footprint that is currently air/empty and still needs a block placed there, or {@code null} when no
     * such position exists.
     */
    @Nullable
    public static Vector3i findNextBuildTarget(@Nullable ConstructionOrderStore.Entry order, World world, BlockSelection prefab)
    {
        if (order == null || order.buildOrigin == null)
            return null;
        Vector3i origin = order.buildOrigin;

        int emptyId = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);

        try (var _ = DebugTiming.measure("ConstructorUtil.findNextBuildTarget", 50))
        {
            for (int[] b : getSortedBlocks(order, prefab))
            {
                int prefabBlockId = b[3];
                if (prefabBlockId == 0 || prefabBlockId == emptyId)
                    continue;

                int wx = b[0] + origin.x - prefab.getAnchorX();
                int wy = b[1] + origin.y - prefab.getAnchorY();
                int wz = b[2] + origin.z - prefab.getAnchorZ();

                int worldBlock = BlockReadUtil.getBlockId(world, wx, wy, wz);
                if (isBlockEquivalent(worldBlock, prefabBlockId))
                    continue; // already correct

                // Only return air slots -- non-air blocks need clearing first.
                if (worldBlock == 0)
                    return new Vector3i(wx, wy, wz);
            }
        }

        return null;
    }

    /**
     * White = origin, red/magenta = needs clearing (magenta if a colonist has already claimed it), yellow/lime = needs building (lime if a colonist has
     * already claimed it). Used by both the developer debug overlay ({@code DebugConfig.isDrawConstructorOrders()}) and the player-facing preview toggle on
     * the constructor workstation UI ({@link com.hytalecolonies.utils.ConstructionPreviewUtil}).
     */
    public static void drawConstructionOrderOverlay(@Nullable ConstructionOrderStore.Entry order, @Nullable BlockSelection prefab, World world)
    {
        if (order == null || order.buildOrigin == null || prefab == null)
            return;
        Vector3i origin   = order.buildOrigin;
        float    drawTime = 2.0f;
        int      emptyId  = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);

        DebugUtils.addCube(world, origin.x + 0.5, origin.y + 0.5, origin.z + 0.5, DebugUtils.COLOR_WHITE, 1.4, drawTime);

        prefab.forEachBlock((lx, ly, lz, block) -> {
            int     prefabBlockId = block.blockId();
            boolean isAir         = (prefabBlockId == 0 || prefabBlockId == emptyId);

            int wx = lx + origin.x - prefab.getAnchorX();
            int wy = ly + origin.y - prefab.getAnchorY();
            int wz = lz + origin.z - prefab.getAnchorZ();

            int worldBlock = BlockReadUtil.getBlockId(world, wx, wy, wz);

            boolean needsClear = worldBlock != 0 && (isAir || !isBlockEquivalent(worldBlock, prefabBlockId));
            boolean needsBuild = !needsClear && !isAir && worldBlock == 0;
            if (!needsClear && !needsBuild)
                return;

            String   claimType = ClaimBlockUtil.peekClaimType(world, new Vector3i(wx, wy, wz));
            Vector3f color;
            if (needsClear)
                color = "Clear".equals(claimType) ? DebugUtils.COLOR_MAGENTA : DebugUtils.COLOR_RED;
            else
                color = "Build".equals(claimType) ? DebugUtils.COLOR_LIME : DebugUtils.COLOR_YELLOW;

            DebugUtils.addCube(world, wx + 0.5, wy + 0.5, wz + 0.5, color, 1.1, drawTime);
        });
    }

    /**
     * Returns how many bottom layers of {@code prefab} a {@link com.hypixel.hytale.server.core.modules.entity.component.PersistentPrefabPreview} hologram
     * should reveal. {@code full} returns {@code Integer.MAX_VALUE} (show everything); otherwise returns the number of fully-completed local-Y layers plus
     * one, so the layer currently being worked on is also visible (matches the "reveal bottom-up" guided-building use case).
     */
    public static int computeVisibleLayerCount(@Nullable ConstructionOrderStore.Entry order, @Nonnull World world, @Nullable BlockSelection prefab, boolean full)
    {
        if (full || order == null || order.buildOrigin == null || prefab == null)
            return Integer.MAX_VALUE;

        List<int[]> blocks = getSortedBlocks(order, prefab);
        if (blocks.isEmpty())
            return Integer.MAX_VALUE;

        Vector3i origin  = order.buildOrigin;
        int      emptyId = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);
        int      minLy   = blocks.get(0)[1];
        int      lastCompleteLy = minLy - 1;

        int i = 0;
        while (i < blocks.size())
        {
            int     ly        = blocks.get(i)[1];
            boolean layerDone = true;
            while (i < blocks.size() && blocks.get(i)[1] == ly)
            {
                int[]   b             = blocks.get(i++);
                int     prefabBlockId = b[3];
                boolean isAir         = (prefabBlockId == 0 || prefabBlockId == emptyId);
                int     wx            = b[0] + origin.x - prefab.getAnchorX();
                int     wy            = b[1] + origin.y - prefab.getAnchorY();
                int     wz            = b[2] + origin.z - prefab.getAnchorZ();
                int     worldBlock    = BlockReadUtil.getBlockId(world, wx, wy, wz);
                boolean correct       = isAir ? worldBlock == 0 : isBlockEquivalent(worldBlock, prefabBlockId);
                if (!correct)
                    layerDone = false;
            }
            if (!layerDone)
                break;
            lastCompleteLy = ly;
        }

        return (lastCompleteLy - minLy) + 2;
    }

    /**
     * Returns the {@code RotationTuple} index for the prefab block at the given world position, or {@code 0} if absent.
     */
    public static int getDesiredBlockRotation(@Nullable ConstructionOrderStore.Entry order, BlockSelection prefab, int wx, int wy, int wz)
    {
        if (order == null || order.buildOrigin == null)
            return 0;
        Vector3i                   origin = order.buildOrigin;
        int                        lx     = wx - origin.x + prefab.getAnchorX();
        int                        ly     = wy - origin.y + prefab.getAnchorY();
        int                        lz     = wz - origin.z + prefab.getAnchorZ();
        BlockSelection.BlockHolder bh     = prefab.getBlockHolderAtWorldPos(lx, ly, lz);
        return bh != null ? bh.rotation() : 0;
    }

    /**
     * Returns true if the base cell and every filler cell the block occupies (at the given rotation) are all air.
     */
    public static boolean areAllCellsClear(BlockType blockType, int rotation, int wx, int wy, int wz, World world)
    {
        BlockBoundingBoxes hitbox = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        if (hitbox == null)
            return BlockReadUtil.getBlockId(world, wx, wy, wz) == 0;
        return FillerBlockUtil.testFillerBlocks(hitbox.get(rotation), (fx, fy, fz) -> BlockReadUtil.getBlockId(world, wx + fx, wy + fy, wz + fz) == 0);
    }

    /**
     * Returns the block type key for the prefab position, or {@code null} if outside the footprint.
     */
    @Nullable
    public static String getDesiredBlockKey(@Nullable ConstructionOrderStore.Entry order, BlockSelection prefab, int wx, int wy, int wz)
    {
        if (order == null || order.buildOrigin == null)
            return null;
        Vector3i origin = order.buildOrigin;

        int lx = wx - origin.x + prefab.getAnchorX();
        int ly = wy - origin.y + prefab.getAnchorY();
        int lz = wz - origin.z + prefab.getAnchorZ();

        int blockId = prefab.getBlockAtWorldPos(lx, ly, lz);
        if (blockId == Integer.MIN_VALUE)
            return null;

        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        return blockType != null ? blockType.getId() : null;
    }

    /**
     * Iterates the prefab top-down, finds the first block that needs clearing and can be claimed, claims it, and returns its world position. Returns
     * {@code null} if no claimable clearing target exists (all done or all already claimed by other colonists).
     *
     * <p>
     * Must be called on the world thread (e.g. inside a {@code world.execute()} callback). The find and claim are atomic so two colonists cannot race to claim
     * the same position.
     */
    @Nullable
    public static Vector3i
    claimNextClearingTarget(@Nullable ConstructionOrderStore.Entry order, @Nonnull World world, @Nullable BlockSelection prefab, @Nonnull UUID colonistUuid)
    {
        if (order == null || order.buildOrigin == null || prefab == null)
            return null;
        Vector3i origin  = order.buildOrigin;
        int      emptyId = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);

        try (var _ = DebugTiming.measure("ConstructorUtil.claimNextClearingTarget", 50))
        {
            // Scan top-down so floating blocks are claimed and cleared before their
            // supports.
            List<int[]> blocks = getSortedBlocks(order, prefab);
            for (int i = blocks.size() - 1; i >= 0; i--)
            {
                int[]   b             = blocks.get(i);
                int     prefabBlockId = b[3];
                boolean isAir         = (prefabBlockId == 0 || prefabBlockId == emptyId);
                int     wx            = b[0] + origin.x - prefab.getAnchorX();
                int     wy            = b[1] + origin.y - prefab.getAnchorY();
                int     wz            = b[2] + origin.z - prefab.getAnchorZ();
                int     worldBlock    = BlockReadUtil.getBlockId(world, wx, wy, wz);

                boolean needsClear = isAir ? (worldBlock != 0) : (worldBlock != 0 && !isBlockEquivalent(worldBlock, prefabBlockId));
                if (!needsClear)
                    continue;

                Vector3i pos = new Vector3i(wx, wy, wz);
                if (ClaimBlockUtil.claimBlock(world, pos, colonistUuid, "Clear"))
                    return pos;
            }
        }
        return null;
    }

    /**
     * Iterates the prefab, tries to claim each block that still needs placing, and returns up to {@code maxCount} successfully claimed world positions (in
     * prefab scan order).
     *
     * <p>
     * Must be called on the world thread (e.g. inside a {@code world.execute()} callback).
     */
    @Nonnull
    public static List<Vector3i> getAndClaimBuildTargets(@Nullable ConstructionOrderStore.Entry order,
                                                         @Nonnull World                         world,
                                                         @Nullable BlockSelection               prefab,
                                                         @Nonnull UUID                          colonistUuid,
                                                         int                                    maxCount)
    {
        if (order == null || order.buildOrigin == null || prefab == null || maxCount <= 0)
        {
            return Collections.emptyList();
        }

        Vector3i       origin        = order.buildOrigin;
        int            emptyId       = BlockType.getAssetMap().getIndex(EMPTY_BLOCK_KEY);
        List<Vector3i> claimedBlocks = new ArrayList<>();

        for (int[] b : getSortedBlocks(order, prefab))
        {
            if (claimedBlocks.size() >= maxCount)
                break;

            int prefabBlockId = b[3];
            if (prefabBlockId == 0 || prefabBlockId == emptyId)
                continue;

            int wx = b[0] + origin.x - prefab.getAnchorX();
            int wy = b[1] + origin.y - prefab.getAnchorY();
            int wz = b[2] + origin.z - prefab.getAnchorZ();

            int worldBlock = BlockReadUtil.getBlockId(world, wx, wy, wz);
            if (isBlockEquivalent(worldBlock, prefabBlockId))
                continue; // already correct
            if (worldBlock != 0)
                continue; // block in the way -- needs to be cleared first

            BlockType blockType = BlockType.getAssetMap().getAsset(prefabBlockId);
            if (blockType == null)
                continue;

            int      rotation = b[4];
            Vector3i pos      = new Vector3i(wx, wy, wz);

            // We immediately claim the block before returning it, so no other colonist can claim the block.
            // If the claim fails, it was already claimed and we skip.
            if (ClaimBlockUtil.claimBlockAndFillers(world, wx, wy, wz, blockType, rotation, colonistUuid, "Build"))
            {
                claimedBlocks.add(pos);
            }
        }

        return claimedBlocks;
    }

    /**
     * Computes the blocks needed to place all {@code buildTargets}.
     */
    @Nonnull
    public static List<ItemStack>
    getBuildingBlocks(@Nullable ConstructionOrderStore.Entry order, @Nullable BlockSelection prefab, @Nonnull List<Vector3i> buildTargets)
    {
        if (order == null || prefab == null || buildTargets.isEmpty())
            return Collections.emptyList();

        Map<String, Integer> needed = new LinkedHashMap<>();
        for (Vector3i pos : buildTargets)
        {
            String blockKey = getDesiredBlockKey(order, prefab, pos.x, pos.y, pos.z);
            if (blockKey == null || blockKey.isEmpty() || EMPTY_BLOCK_KEY.equals(blockKey))
                continue;
            BlockType blockType = BlockType.getAssetMap().getAsset(blockKey);
            if (blockType == null)
                continue;
            Item item = blockType.getItem();
            if (item == null)
                continue;
            needed.merge(item.getId(), 1, Integer::sum);
        }

        List<ItemStack> result = new ArrayList<>(needed.size());
        for (Map.Entry<String, Integer> entry : needed.entrySet())
        {
            result.add(new ItemStack(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /**
     * Claims build target block locations, sets {@link JobTaskComponent} item requirements, and adds them to {@link ConstructorJobComponent#pendingBuildQueue}.
     * Returns {@code true} if at least one block was claimed.
     */
    public static boolean setupBuildRun(@Nonnull Ref<EntityStore> colonistRef,
                                        @Nonnull EntityStore      entityStore,
                                        @Nonnull World            world,
                                        @Nonnull ConstructionOrderStore.Entry order,
                                        @Nonnull BlockSelection               prefab,
                                        @Nonnull UUID                         colonistUuid,
                                        int                                   blockCount,
                                        @Nonnull String                       npcId)
    {
        // Claim blocks where colonist wants to build.
        List<Vector3i> claimedBlocks = getAndClaimBuildTargets(order, world, prefab, colonistUuid, blockCount);
        if (claimedBlocks.isEmpty())
        {
            DebugLog.info(DebugCategory.CONSTRUCTOR_JOB, "[ConstructorJob] [%s] No claimable build targets.", npcId);
            return false;
        }

        // Determine required blocks/items to build.
        List<ItemStack> buildingBlocks = getBuildingBlocks(order, prefab, claimedBlocks);
        setRequiredTaskItems(colonistRef, entityStore, buildingBlocks);

        // Update constructor build queue.
        ConstructorJobComponent constructorJob = entityStore.getStore().getComponent(colonistRef, ConstructorJobComponent.getComponentType());
        if (constructorJob != null)
        {
            constructorJob.pendingBuildQueue.clear();
            constructorJob.pendingBuildQueue.addAll(claimedBlocks);
        }

        DebugLog.info(DebugCategory.CONSTRUCTOR_JOB,
                      "[ConstructorJob] [%s] Build run: %d position(s), %d item type(s).",
                      npcId,
                      claimedBlocks.size(),
                      buildingBlocks.size());
        return true;
    }

    /**
     * Sets the {@link JobTaskComponent} item requirements for the blocks to build.
     */
    public static void setRequiredTaskItems(@Nonnull Ref<EntityStore> colonistRef, @Nonnull EntityStore entityStore, @Nonnull List<ItemStack> buildingBlocks)
    {
        ItemRequirement[] requiredBuildingBlocks =
                buildingBlocks.stream().map(s -> new ItemRequirement(s.getItemId(), s.getQuantity())).toArray(ItemRequirement[] ::new);

        JobTaskComponent taskComponent = new JobTaskComponent();
        taskComponent.requiredItems    = requiredBuildingBlocks;
        entityStore.getStore().addComponent(colonistRef, JobTaskComponent.getComponentType(), taskComponent);
    }
}
