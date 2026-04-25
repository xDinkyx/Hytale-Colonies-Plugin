package com.hytalecolonies.ui;

import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobType;
import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.components.jobs.WoodsmanJobComponent;
import com.hytalecolonies.components.jobs.ConstructorJobComponent;
import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Colonist inspect page: shows the colonist's inventory, equipment (tool slots),
 * and a placeholder stats/skills view. Items can be transferred between the
 * colonist and the player by clicking slots.
 */
public class ColonistInspectPage extends InteractiveCustomUIPage<ColonistInspectPage.UIEventData> {

    public static final String LAYOUT = "hytalecolonies/ColonistInspect.ui";

    private static final String GRID_NPC_TOOLS   = "NpcTools";
    private static final String GRID_NPC_STORAGE = "NpcStorage";
    private static final String GRID_PLAYER_STORAGE = "PlayerStorage";
    private static final String GRID_PLAYER_HOTBAR  = "PlayerHotbar";

    private static final String TAB_INVENTORY = "Inventory";
    private static final String TAB_STATS     = "Stats";

    private final UUID colonistUuid;
    private boolean showInventoryTab = true;

    public ColonistInspectPage(@Nonnull PlayerRef playerRef, @Nonnull UUID colonistUuid) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.colonistUuid = colonistUuid;
    }

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append(LAYOUT);

        // Populate initial content
        populateNpcInfo(ref, cmd, store);
        populateAllGrids(ref, cmd, store);

        // Tab buttons
        cmd.set("#TabInventoryContent.Visible", true);
        cmd.set("#TabStatsContent.Visible", false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabInventory",
                new EventData().append("Action", "tab").append("Tab", TAB_INVENTORY), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabStats",
                new EventData().append("Action", "tab").append("Tab", TAB_STATS), false);

        // Close button
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                new EventData().append("Action", "close"), false);

        // NPC tool slot clicks
        evt.addEventBinding(CustomUIEventBindingType.SlotClicking, "#NpcToolsGrid",
                new EventData().append("Action", "slot").append("Grid", GRID_NPC_TOOLS)
                        .append("@SlotIndex", "#NpcToolsGrid.SelectedSlotIndex"), false);

        // NPC storage slot clicks
        evt.addEventBinding(CustomUIEventBindingType.SlotClicking, "#NpcStorageGrid",
                new EventData().append("Action", "slot").append("Grid", GRID_NPC_STORAGE)
                        .append("@SlotIndex", "#NpcStorageGrid.SelectedSlotIndex"), false);

        // Player storage slot clicks
        evt.addEventBinding(CustomUIEventBindingType.SlotClicking, "#PlayerStorageGrid",
                new EventData().append("Action", "slot").append("Grid", GRID_PLAYER_STORAGE)
                        .append("@SlotIndex", "#PlayerStorageGrid.SelectedSlotIndex"), false);

        // Player hotbar slot clicks
        evt.addEventBinding(CustomUIEventBindingType.SlotClicking, "#PlayerHotbarGrid",
                new EventData().append("Action", "slot").append("Grid", GRID_PLAYER_HOTBAR)
                        .append("@SlotIndex", "#PlayerHotbarGrid.SelectedSlotIndex"), false);
    }

    // -------------------------------------------------------------------------
    // Event handling
    // -------------------------------------------------------------------------

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull UIEventData data
    ) {
        if (data.action == null) {
            sendUpdate(null, false);
            return;
        }

        switch (data.action) {
            case "close":
                close(ref, store);
                return;

            case "tab":
                handleTabSwitch(data.tab, ref, store);
                break;

            case "slot":
                handleSlotClick(data.grid, data.slotIndex, ref, store);
                break;

            default:
                break;
        }

        sendUpdate(null, false);
    }

    // -------------------------------------------------------------------------
    // Tab switching
    // -------------------------------------------------------------------------

    private void handleTabSwitch(@Nullable String tab, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (tab == null) return;
        showInventoryTab = TAB_INVENTORY.equals(tab);

        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#TabInventoryContent.Visible", showInventoryTab);
        cmd.set("#TabStatsContent.Visible", !showInventoryTab);

        if (!showInventoryTab) {
            populateStatsTab(ref, cmd, store);
        }

        sendUpdate(cmd, false);
    }

    // -------------------------------------------------------------------------
    // Slot click: transfer item between colonist and player
    // -------------------------------------------------------------------------

    private void handleSlotClick(@Nullable String grid, int slotIndex, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (grid == null || slotIndex < 0) return;

        NPCEntity npc = getNpcEntity(store);
        if (npc == null) {
            DebugLog.warning(DebugCategory.COLONIST_LIFECYCLE,
                    "[ColonistInspect] Colonist %s not found in store.", colonistUuid);
            close(ref, store);
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        boolean fromNpc = GRID_NPC_TOOLS.equals(grid) || GRID_NPC_STORAGE.equals(grid);

        if (fromNpc) {
            transferFromNpcToPlayer(grid, slotIndex, npc, player);
        } else {
            transferFromPlayerToNpc(grid, slotIndex, npc, player);
        }

        // Refresh all grids to reflect the updated state
        UICommandBuilder cmd = new UICommandBuilder();
        populateAllGrids(ref, cmd, store);
        sendUpdate(cmd, false);
    }

    /** Moves the item at [slotIndex] in the NPC grid to the player's first available slot. */
    private void transferFromNpcToPlayer(
            @Nonnull String grid, int slotIndex,
            @Nonnull NPCEntity npc, @Nonnull Player player
    ) {
        ItemContainer source = GRID_NPC_TOOLS.equals(grid)
                ? npc.getInventory().getHotbar()
                : npc.getInventory().getStorage();
        if (source == null) return;

        ItemStack item = source.getItemStack((short) slotIndex);
        if (ItemStack.isEmpty(item)) return;

        // Try player storage first, fall back to hotbar
        ItemContainer playerStorage = player.getInventory().getStorage();
        ItemContainer playerHotbar  = player.getInventory().getHotbar();

        source.removeItemStackFromSlot((short) slotIndex);
        ItemStack remainder = addToContainer(playerStorage, item);
        if (!ItemStack.isEmpty(remainder)) {
            remainder = addToContainer(playerHotbar, remainder);
        }
        if (!ItemStack.isEmpty(remainder)) {
            // Player is full — return item to original slot
            source.addItemStackToSlot((short) slotIndex, remainder);
        }
    }

    /** Moves the item at [slotIndex] in the player grid to the NPC's first available slot. */
    private void transferFromPlayerToNpc(
            @Nonnull String grid, int slotIndex,
            @Nonnull NPCEntity npc, @Nonnull Player player
    ) {
        ItemContainer source = GRID_PLAYER_HOTBAR.equals(grid)
                ? player.getInventory().getHotbar()
                : player.getInventory().getStorage();
        if (source == null) return;

        ItemStack item = source.getItemStack((short) slotIndex);
        if (ItemStack.isEmpty(item)) return;

        ItemContainer npcStorage = npc.getInventory().getStorage();
        ItemContainer npcHotbar  = npc.getInventory().getHotbar();

        source.removeItemStackFromSlot((short) slotIndex);
        ItemStack remainder = addToContainer(npcStorage, item);
        if (!ItemStack.isEmpty(remainder)) {
            remainder = addToContainer(npcHotbar, remainder);
        }
        if (!ItemStack.isEmpty(remainder)) {
            // NPC is full — return item to original slot
            source.addItemStackToSlot((short) slotIndex, remainder);
        }
    }

    /** Adds the stack to the container and returns any remainder. */
    @Nullable
    private static ItemStack addToContainer(@Nullable ItemContainer container, @Nonnull ItemStack stack) {
        if (container == null) return stack;
        return container.addItemStack(stack).getRemainder();
    }

    // -------------------------------------------------------------------------
    // Populate NPC info labels
    // -------------------------------------------------------------------------

    private void populateNpcInfo(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull Store<EntityStore> store
    ) {
        Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(colonistUuid);
        if (npcRef == null || !npcRef.isValid()) return;

        ColonistComponent colonist = store.getComponent(npcRef, ColonistComponent.getComponentType());
        if (colonist == null) return;

        cmd.set("#ColonistName.Text", colonist.getColonistName());
        cmd.set("#ColonistLevel.Text", "Level " + colonist.getColonistLevel());
        cmd.set("#ColonyId.Text", "Colony: " + colonist.getColonyId());

        JobComponent job = store.getComponent(npcRef, JobComponent.getComponentType());
        String jobName = job != null ? formatJobName(resolveJobType(npcRef, store)) : "Unemployed";
        cmd.set("#ColonistJob.Text", jobName);
    }

    private void populateStatsTab(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull Store<EntityStore> store
    ) {
        Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(colonistUuid);
        if (npcRef == null || !npcRef.isValid()) return;

        ColonistComponent colonist = store.getComponent(npcRef, ColonistComponent.getComponentType());
        if (colonist == null) return;

        cmd.set("#StatsLevelDetail.Text", "Level: " + colonist.getColonistLevel());

        JobComponent job = store.getComponent(npcRef, JobComponent.getComponentType());
        cmd.set("#StatsJobDetail.Text", "Job: " + (job != null ? formatJobName(resolveJobType(npcRef, store)) : "Unemployed"));
    }

    @Nullable
    private static JobType resolveJobType(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        if (store.getComponent(npcRef, WoodsmanJobComponent.getComponentType()) != null) return JobType.Woodsman;
        if (store.getComponent(npcRef, MinerJobComponent.getComponentType()) != null) return JobType.Miner;
        if (store.getComponent(npcRef, ConstructorJobComponent.getComponentType()) != null) return JobType.Constructor;
        return null;
    }

    @Nonnull
    private static String formatJobName(@Nullable JobType jobType) {
        if (jobType == null) return "Unemployed";
        return jobType.name();
    }

    // -------------------------------------------------------------------------
    // Populate item grids
    // -------------------------------------------------------------------------

    private void populateAllGrids(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull Store<EntityStore> store
    ) {
        NPCEntity npc = getNpcEntity(store);
        if (npc != null) {
            cmd.set("#NpcToolsGrid.Slots",   buildSlots(npc.getInventory().getHotbar()));
            cmd.set("#NpcStorageGrid.Slots", buildSlots(npc.getInventory().getStorage()));
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            cmd.set("#PlayerStorageGrid.Slots", buildSlots(player.getInventory().getStorage()));
            cmd.set("#PlayerHotbarGrid.Slots",  buildSlots(player.getInventory().getHotbar()));
        }
    }

    @Nonnull
    private static ItemGridSlot[] buildSlots(@Nullable ItemContainer container) {
        if (container == null) return new ItemGridSlot[0];
        short capacity = container.getCapacity();
        ItemGridSlot[] slots = new ItemGridSlot[capacity];
        for (short i = 0; i < capacity; i++) {
            ItemStack stack = container.getItemStack(i);
            slots[i] = ItemStack.isEmpty(stack) ? new ItemGridSlot() : new ItemGridSlot(stack);
        }
        return slots;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Nullable
    private NPCEntity getNpcEntity(@Nonnull Store<EntityStore> store) {
        Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(colonistUuid);
        if (npcRef == null || !npcRef.isValid()) return null;
        return store.getComponent(npcRef, NPCEntity.getComponentType());
    }

    private void close(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    // -------------------------------------------------------------------------
    // Event data
    // -------------------------------------------------------------------------

    public static class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("Tab",    Codec.STRING), (d, v) -> d.tab = v,    d -> d.tab).add()
                .append(new KeyedCodec<>("Grid",   Codec.STRING), (d, v) -> d.grid = v,   d -> d.grid).add()
                .append(new KeyedCodec<>("@SlotIndex", Codec.INTEGER), (d, v) -> d.slotIndex = v, d -> d.slotIndex).add()
                .build();

        @Nullable String action;
        @Nullable String tab;
        @Nullable String grid;
        int slotIndex = -1;
    }
}
