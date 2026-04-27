package com.hytalecolonies.ui;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hytalecolonies.components.jobs.ConstructorJobComponent;
import com.hytalecolonies.components.jobs.JobType;
import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.components.jobs.WoodsmanJobComponent;
import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;

/**
 * Colonist inspect page.  Shows NPC info and four inventory grids populated
 * via server-pushed Slots arrays.  Click any slot to transfer items.
 */
public class ColonistInspectPage extends InteractiveCustomUIPage<ColonistInspectPage.UIEventData> {

    public static final String LAYOUT = "hytalecolonies/ColonistInspect.ui";

    private static final String GRID_NPC_TOOLS      = "NpcTools";
    private static final String GRID_NPC_STORAGE    = "NpcStorage";
    private static final String GRID_PLAYER_STORAGE = "PlayerStorage";
    private static final String GRID_PLAYER_HOTBAR  = "PlayerHotbar";

    private static final String TAB_INVENTORY = "Inventory";
    private static final String TAB_STATS     = "Stats";

    private final UUID colonistUuid;
    private boolean showInventoryTab = true;
    @Nullable private final ContainerWindow npcToolsWindow;
    @Nullable private final ContainerWindow npcStorageWindow;

    /**
     * @param npcToolsWindow  ContainerWindow for the colonist's tool/hotbar slots, or null if unavailable.
     * @param npcStorageWindow ContainerWindow for the colonist's storage slots, or null if unavailable.
     *                         Both windows must already be registered with the player's WindowManager
     *                         when {@link #build} is called (use {@code openCustomPageWithWindows}).
     */
    public ColonistInspectPage(
            @Nonnull PlayerRef playerRef,
            @Nonnull UUID colonistUuid,
            @Nullable ContainerWindow npcToolsWindow,
            @Nullable ContainerWindow npcStorageWindow
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, UIEventData.CODEC);
        this.colonistUuid = colonistUuid;
        this.npcToolsWindow = npcToolsWindow;
        this.npcStorageWindow = npcStorageWindow;
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

        populateNpcInfo(cmd, store);
        populateAllGrids(ref, cmd, store);

        // Tell each NPC grid which registered window it belongs to so the engine
        // routes MoveItemStack packets for drag-and-drop correctly.
        // Window IDs are valid at this point because openCustomPageWithWindows
        // calls openWindows (assigning IDs) before invoking build().
        if (npcToolsWindow != null && npcToolsWindow.getId() > 0) {
            cmd.set("#NpcToolsGrid.InventorySectionId", npcToolsWindow.getId());
        }
        if (npcStorageWindow != null && npcStorageWindow.getId() > 0) {
            cmd.set("#NpcStorageGrid.InventorySectionId", npcStorageWindow.getId());
        }

        cmd.set("#TabInventoryContent.Visible", true);
        cmd.set("#TabStatsContent.Visible", false);
        // Disabled state = "selected" visual for the active tab
        cmd.set("#TabInventory.Disabled", true);
        cmd.set("#TabStats.Disabled", false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabInventory",
                new EventData().append("Action", "tab").append("Tab", TAB_INVENTORY), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabStats",
                new EventData().append("Action", "tab").append("Tab", TAB_STATS), false);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                new EventData().append("Action", "close"), false);

        evt.addEventBinding(CustomUIEventBindingType.SlotClicking, "#NpcToolsGrid",
                new EventData().append("Action", "slot").append("Grid", GRID_NPC_TOOLS)
                        .append("@SlotIndex", "#NpcToolsGrid.SelectedSlotIndex"), false);
        evt.addEventBinding(CustomUIEventBindingType.SlotClicking, "#NpcStorageGrid",
                new EventData().append("Action", "slot").append("Grid", GRID_NPC_STORAGE)
                        .append("@SlotIndex", "#NpcStorageGrid.SelectedSlotIndex"), false);
        evt.addEventBinding(CustomUIEventBindingType.SlotClicking, "#PlayerStorageGrid",
                new EventData().append("Action", "slot").append("Grid", GRID_PLAYER_STORAGE)
                        .append("@SlotIndex", "#PlayerStorageGrid.SelectedSlotIndex"), false);
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

        if (!"close".equals(data.action)) {
            Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(colonistUuid);
            if (npcRef == null || !npcRef.isValid()) {
                this.close();
                return;
            }
        }

        switch (data.action) {
            case "close":
                this.close();
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
        cmd.set("#TabInventory.Disabled", showInventoryTab);
        cmd.set("#TabStats.Disabled", !showInventoryTab);

        if (!showInventoryTab) {
            populateStatsTab(cmd, store);
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
                    "[ColonistInspect] Colonist %s not found.", colonistUuid);
            this.close();
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

        UICommandBuilder cmd = new UICommandBuilder();
        populateAllGrids(ref, cmd, store);
        sendUpdate(cmd, false);
    }

    private void transferFromNpcToPlayer(@Nonnull String grid, int slotIndex, @Nonnull NPCEntity npc, @Nonnull Player player) {
        ItemContainer source = GRID_NPC_TOOLS.equals(grid)
                ? npc.getInventory().getHotbar()
                : npc.getInventory().getStorage();
        if (source == null) return;

        ItemStack item = source.getItemStack((short) slotIndex);
        if (ItemStack.isEmpty(item)) return;

        source.removeItemStackFromSlot((short) slotIndex);
        ItemStack remainder = addToContainer(player.getInventory().getStorage(), item);
        if (!ItemStack.isEmpty(remainder)) {
            remainder = addToContainer(player.getInventory().getHotbar(), remainder);
        }
        if (!ItemStack.isEmpty(remainder)) {
            source.addItemStackToSlot((short) slotIndex, remainder);
        }
    }

    private void transferFromPlayerToNpc(@Nonnull String grid, int slotIndex, @Nonnull NPCEntity npc, @Nonnull Player player) {
        ItemContainer source = GRID_PLAYER_HOTBAR.equals(grid)
                ? player.getInventory().getHotbar()
                : player.getInventory().getStorage();
        if (source == null) return;

        ItemStack item = source.getItemStack((short) slotIndex);
        if (ItemStack.isEmpty(item)) return;

        source.removeItemStackFromSlot((short) slotIndex);
        ItemStack remainder = addToContainer(npc.getInventory().getStorage(), item);
        if (!ItemStack.isEmpty(remainder)) {
            remainder = addToContainer(npc.getInventory().getHotbar(), remainder);
        }
        if (!ItemStack.isEmpty(remainder)) {
            source.addItemStackToSlot((short) slotIndex, remainder);
        }
    }

    @Nullable
    private static ItemStack addToContainer(@Nullable ItemContainer container, @Nonnull ItemStack stack) {
        if (container == null) return stack;
        return container.addItemStack(stack).getRemainder();
    }

    // -------------------------------------------------------------------------
    // Grid population
    // -------------------------------------------------------------------------

    private void populateAllGrids(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull Store<EntityStore> store) {
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
    // NPC info labels
    // -------------------------------------------------------------------------

    private void populateNpcInfo(@Nonnull UICommandBuilder cmd, @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(colonistUuid);
        if (npcRef == null || !npcRef.isValid()) return;

        ColonistComponent colonist = store.getComponent(npcRef, ColonistComponent.getComponentType());
        if (colonist == null) return;

        cmd.set("#ColonistName.Text", colonist.getColonistName());
        cmd.set("#ColonistLevel.Text", "Level " + colonist.getColonistLevel());
        cmd.set("#ColonyId.Text", "Colony: " + colonist.getColonyId());
        cmd.set("#ColonistJob.Text", formatJobName(resolveJobType(npcRef, store)));
    }

    private void populateStatsTab(@Nonnull UICommandBuilder cmd, @Nonnull Store<EntityStore> store) {
        Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(colonistUuid);
        if (npcRef == null || !npcRef.isValid()) return;

        ColonistComponent colonist = store.getComponent(npcRef, ColonistComponent.getComponentType());
        if (colonist == null) return;

        cmd.set("#StatsLevelDetail.Text", "Level: " + colonist.getColonistLevel());
        cmd.set("#StatsJobDetail.Text", "Job: " + formatJobName(resolveJobType(npcRef, store)));
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
    // Helpers
    // -------------------------------------------------------------------------

    @Nullable
    private NPCEntity getNpcEntity(@Nonnull Store<EntityStore> store) {
        Ref<EntityStore> npcRef = store.getExternalData().getRefFromUUID(colonistUuid);
        if (npcRef == null || !npcRef.isValid()) return null;
        return store.getComponent(npcRef, NPCEntity.getComponentType());
    }

    // -------------------------------------------------------------------------
    // Lifecycle callbacks
    // -------------------------------------------------------------------------

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            WindowManager windowManager = player.getWindowManager();
            if (npcToolsWindow != null && npcToolsWindow.getId() > 0) {
                windowManager.closeWindow(ref, npcToolsWindow.getId(), store);
            }
            if (npcStorageWindow != null && npcStorageWindow.getId() > 0) {
                windowManager.closeWindow(ref, npcStorageWindow.getId(), store);
            }
        }
        DebugLog.info(DebugCategory.COLONIST_LIFECYCLE,
                "[ColonistInspectPage] Dismissed for colonist %s.", colonistUuid);
    }

    // -------------------------------------------------------------------------
    // Event data
    // -------------------------------------------------------------------------

    public static class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>("Action",     Codec.STRING),  (d, v) -> d.action    = v, d -> d.action).add()
                .append(new KeyedCodec<>("Tab",        Codec.STRING),  (d, v) -> d.tab       = v, d -> d.tab).add()
                .append(new KeyedCodec<>("Grid",       Codec.STRING),  (d, v) -> d.grid      = v, d -> d.grid).add()
                .append(new KeyedCodec<>("@SlotIndex", Codec.INTEGER), (d, v) -> d.slotIndex = v, d -> d.slotIndex).add()
                .build();

        @Nullable String action;
        @Nullable String tab;
        @Nullable String grid;
        int slotIndex = -1;
    }
}