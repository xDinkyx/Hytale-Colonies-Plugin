package com.hytalecolonies.utils;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemToolSpec;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.component.Ref;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Shared inventory utilities for colonist job systems.
 *
 * <h3>Suitability: power threshold and quality tier</h3>
 * <p>A tool's spec for a gather type must pass two checks (mirroring
 * {@code BlockHarvestUtils.getSpecPowerDamageBlock}):
 * <ol>
 *   <li><strong>Power &gt; unarmed baseline</strong> — the spec must beat the power in
 *       {@code Server/Item/Unarmed/Gathering/&lt;GatherType&gt;.json}, so equipping
 *       the tool is actually an improvement.  A crude hatchet's {@code Soils: 0.05}
 *       loses to unarmed {@code Soils: 0.10} and is therefore excluded.</li>
 *   <li><strong>Quality &ge; required quality</strong> — the block's
 *       {@code Breaking.Quality} sets a minimum tier.  A tier-3 Rocks block requires
 *       an iron pickaxe (quality 3); a crude pickaxe (quality 0) cannot break it
 *       even though it technically has a {@code Rocks} spec.</li>
 * </ol>
 * <p>Both checks are purely data-driven — no magic numbers here.
 */
public final class ColonistToolUtil {

    private ColonistToolUtil() {}

    // -------------------------------------------------------------------------
    // Block queries
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link BlockBreakingDropType} config for the block at {@code pos},
     * or {@code null} if the block has no breaking config.
     */
    @Nullable
    public static BlockBreakingDropType getBreakingConfig(@Nonnull World world, @Nonnull Ref<ChunkStore> chunkRef,
                                                          @Nonnull Vector3i pos) {
        WorldChunk worldChunk = world.getChunkStore().getStore().getComponent(chunkRef, WorldChunk.getComponentType());
        if (worldChunk == null) return null;
        int blockId = worldChunk.getBlock(pos.x, pos.y, pos.z);
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType == null) return null;
        BlockGathering gathering = blockType.getGathering();
        if (gathering == null) return null;
        return gathering.getBreaking();
    }

    /**
     * Returns the {@code GatherType} string that the block at {@code pos} requires
     * to be broken, or {@code null} if the block has no breaking config.
     */
    @Nullable
    public static String getRequiredGatherType(@Nonnull World world, @Nonnull Ref<ChunkStore> chunkRef,
                                               @Nonnull Vector3i pos) {
        BlockBreakingDropType breaking = getBreakingConfig(world, chunkRef, pos);
        return breaking != null ? breaking.getGatherType() : null;
    }

    // -------------------------------------------------------------------------
    // Tool classification
    // -------------------------------------------------------------------------

    /**
     * Returns the unarmed baseline power for {@code gatherType} from the
     * {@code Server/Item/Unarmed/Gathering/} assets (e.g. 0.03 for {@code Woods}).
     * Returns {@code 0} if no unarmed spec exists for the gather type.
     */
    public static float unarmedPowerFor(@Nonnull String gatherType) {
        ItemToolSpec unarmedSpec = ItemToolSpec.getAssetMap().getAsset(gatherType);
        return unarmedSpec != null ? unarmedSpec.getPower() : 0f;
    }

    /**
     * Returns the power value of {@code tool}'s spec for {@code gatherType}, or
     * {@code 0} if the tool has no spec for it.
     */
    public static float powerForGatherType(@Nullable ItemTool tool, @Nonnull String gatherType) {
        if (tool == null || tool.getSpecs() == null) return 0f;
        for (ItemToolSpec spec : tool.getSpecs()) {
            if (gatherType.equals(spec.getGatherType())) return spec.getPower();
        }
        return 0f;
    }

    /**
     * Returns {@code true} if {@code tool} can break a block with {@code gatherType}
     * at the given {@code requiredQuality} tier.  Mirrors the logic in
     * {@code BlockHarvestUtils.getSpecPowerDamageBlock}:
     * <ol>
     *   <li>The spec's power must exceed the unarmed baseline (so the tool is an
     *       improvement over bare hands).</li>
     *   <li>The spec's quality must be &ge; {@code requiredQuality} (tier gate).</li>
     * </ol>
     *
     * @param requiredQuality the {@code Breaking.Quality} value from the block's JSON
     *                        (0 = no tier requirement).
     */
    public static boolean toolSupportsGatherType(@Nullable ItemTool tool,
                                                 @Nonnull String gatherType,
                                                 int requiredQuality) {
        if (tool == null || tool.getSpecs() == null) return false;
        for (ItemToolSpec spec : tool.getSpecs()) {
            if (gatherType.equals(spec.getGatherType())) {
                return spec.getPower() > unarmedPowerFor(gatherType)
                        && spec.getQuality() >= requiredQuality;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Full-inventory tool search
    // -------------------------------------------------------------------------

    /**
     * Describes where the best tool was found in the colonist's inventory.
     *
     * @param container  The {@link ItemContainer} the tool currently lives in
     *                   ({@code hotbar} or {@code storage/backpack}).
     * @param slot       The slot index within that container.
     * @param power      The tool's power for the requested gather type.
     * @param inHotbar   {@code true} when {@code container} is the hotbar —
     *                   the tool can be equipped with a simple slot switch.
     */
    public record ToolMatch(
            @Nonnull ItemContainer container,
            short slot,
            float power,
            boolean inHotbar
    ) {}

    /**
     * Searches the colonist's entire inventory (hotbar → storage → backpack) for
     * the <em>best</em> tool for the block described by {@code breaking} and returns
     * a {@link ToolMatch}, or {@code null} if no suitable tool exists.
     *
     * <p>"Suitable" means the spec passes both the power-threshold and quality-tier
     * checks.  Among suitable tools the one with the highest power wins.
     * Hotbar is preferred over storage when power is equal.
     */
    @Nullable
    public static ToolMatch findBestToolInInventory(@Nonnull Inventory inventory,
                                                    @Nonnull BlockBreakingDropType breaking) {
        String gatherType = breaking.getGatherType();
        int requiredQuality = breaking.getQuality();
        if (gatherType == null) return null;

        ToolMatch best = null;
        best = betterMatch(best, inventory.getHotbar(),  gatherType, requiredQuality, true);
        best = betterMatch(best, inventory.getStorage(), gatherType, requiredQuality, false);
        best = betterMatch(best, inventory.getBackpack(),gatherType, requiredQuality, false);
        return best;
    }

    /**
     * Returns {@code true} if the colonist has any suitable tool for the given
     * block's breaking requirements anywhere in their inventory.
     */
    public static boolean hasToolForBlock(@Nonnull Inventory inventory,
                                          @Nonnull BlockBreakingDropType breaking) {
        return findBestToolInInventory(inventory, breaking) != null;
    }

    /**
     * Ensures the best available tool for the given block's breaking requirements is
     * the colonist's active hotbar item. No-ops if the current item in hand is already
     * the best.
     *
     * @return {@code true} if a suitable tool was equipped (or was already in hand),
     *         {@code false} if no suitable tool exists in the inventory.
     */
    public static boolean equipBestToolForBlock(@Nonnull Inventory inventory,
                                                @Nonnull BlockBreakingDropType breaking) {
        ToolMatch match = findBestToolInInventory(inventory, breaking);
        if (match == null) return false;

        ItemTool heldTool = null;
        ItemStack heldItem = inventory.getItemInHand();
        if (heldItem != null && heldItem.getItem() != null) heldTool = heldItem.getItem().getTool();
        float heldPower = powerForGatherType(heldTool, breaking.getGatherType());

        // Already holding the best (or equally good) tool — nothing to do.
        if (heldPower >= match.power()) return true;

        if (match.inHotbar()) {
            inventory.setActiveHotbarSlot((byte) match.slot());
        } else {
            ItemContainer hotbar = inventory.getHotbar();
            if (hotbar == null) return false;
            byte activeSlot = inventory.getActiveHotbarSlot();
            match.container().moveItemStackFromSlotToSlot(match.slot(), 1, hotbar, (short) activeSlot);
            inventory.setActiveHotbarSlot(activeSlot);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Scans {@code container} and returns the better of {@code current} and the best slot found. */
    @Nullable
    private static ToolMatch betterMatch(@Nullable ToolMatch current, @Nullable ItemContainer container,
                                         @Nonnull String gatherType, int requiredQuality, boolean inHotbar) {
        if (container == null) return current;
        float baseline = unarmedPowerFor(gatherType);
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) continue;
            Item item = stack.getItem();
            if (item == null || item.getTool() == null) continue;
            for (ItemToolSpec spec : item.getTool().getSpecs()) {
                if (!gatherType.equals(spec.getGatherType())) continue;
                // Mirror BlockHarvestUtils.getSpecPowerDamageBlock: power > unarmed AND quality >= required.
                if (spec.getPower() > baseline && spec.getQuality() >= requiredQuality) {
                    if (current == null || spec.getPower() > current.power()) {
                        current = new ToolMatch(container, slot, spec.getPower(), inHotbar);
                    }
                }
                break; // only one spec per gather type per item
            }
        }
        return current;
    }
}
