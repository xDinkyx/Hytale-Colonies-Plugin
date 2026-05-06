package com.hytalecolonies.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hytalecolonies.components.jobs.ConstructorJobComponent;
import com.hytalecolonies.events.ColonistFiredEvent;
import com.hytalecolonies.events.ColonistHiredEvent;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.components.jobs.WoodsmanJobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hytalecolonies.systems.jobs.JobAssignmentSystems;
import com.hytalecolonies.utils.WorkStationUtil;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

/**
 * Management UI for a workstation block.
 * Shows job stats and the list of assigned colonists with per-colonist
 * controls.
 */
public class WorkstationInspectPage extends InteractiveCustomUIPage<WorkstationInspectPage.UIEventData> {

    public static final String LAYOUT = "hytalecolonies/WorkstationInspect.ui";
    static final int ROW_MAX = 10;

    private static final Random RANDOM = new Random();
    /** Recall scatter radius in blocks. */
    private static final double RECALL_RADIUS = 2.0;

    private final Vector3i blockPos;
    /**
     * Snapshot of assigned-colonist order used to map row index to UUID. Updated
     * each build/refresh.
     */
    private List<UUID> colonistOrder = new ArrayList<>();
    /**
     * Captured from build() so event callbacks can schedule work on the correct
     * world thread.
     */
    private Ref<EntityStore> capturedRef;
    private EventRegistration<?, ?> hiredRegistration;
    private EventRegistration<?, ?> firedRegistration;

    public WorkstationInspectPage(@Nonnull PlayerRef playerRef, @Nonnull Vector3i blockPos) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, UIEventData.CODEC);
        this.blockPos = new Vector3i(blockPos.x, blockPos.y, blockPos.z);
    }

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store) {
        this.capturedRef = ref;
        // Re-register in case build() is called more than once.
        unregisterEventListeners();
        hiredRegistration = HytaleServer.get().getEventBus().register(
                ColonistHiredEvent.class, blockPos,
                e -> scheduleRefresh());
        firedRegistration = HytaleServer.get().getEventBus().register(
                ColonistFiredEvent.class, blockPos,
                e -> scheduleRefresh());
        cmd.append(LAYOUT);
        populatePage(cmd, evt, store);
    }

    // -------------------------------------------------------------------------
    // Event handling
    // -------------------------------------------------------------------------

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull UIEventData data) {
        if (data.action == null) {
            sendUpdate(null, false);
            return;
        }

        switch (data.action) {
            case "close":
                this.close();
                return;

            case "fire":
                handleFire(data.getIndex(), ref, store);
                break;

            case "inspect":
                // Opens a separate page -- no sendUpdate needed after
                handleInspect(data.getIndex(), ref, store);
                return;

            case "recall":
                handleRecall(store);
                break;

            default:
                break;
        }

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        populatePage(cmd, evt, store);
        sendUpdate(cmd, evt, false);
    }

    @Override
    public void onDismiss(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store) {
        unregisterEventListeners();
    }

    private void scheduleRefresh() {
        if (capturedRef == null || !capturedRef.isValid())
            return;
        capturedRef.getStore().getExternalData().getWorld().execute(this::refreshPage);
    }

    private void unregisterEventListeners() {
        if (hiredRegistration != null) {
            hiredRegistration.unregister();
            hiredRegistration = null;
        }
        if (firedRegistration != null) {
            firedRegistration.unregister();
            firedRegistration = null;
        }
    }

    // -------------------------------------------------------------------------
    // Page population
    // -------------------------------------------------------------------------

    private void populatePage(
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        WorkStationComponent ws = WorkStationUtil.getWorkStationAt(world, blockPos);

        if (ws == null) {
            cmd.set("#StationTitle.Text", "Workstation");
            cmd.set("#StationStats.Text", "(removed)");
            cmd.set("#EmptyLabel.Visible", true);
            hideAllRows(cmd);
            bindClose(evt);
            return;
        }

        String jobLabel = ws.getJobType() != null ? ws.getJobType().name() : "Unknown";
        int assigned = ws.getAssignedColonists().size();
        cmd.set("#StationTitle.Text", jobLabel + " Workstation");
        cmd.set("#StationStats.Text",
                "Workers: " + assigned + " / " + ws.getMaxWorkers()
                        + "   Blocks/run: " + ws.blocksPerRun);

        colonistOrder = new ArrayList<>(ws.getAssignedColonists());
        boolean empty = colonistOrder.isEmpty();
        cmd.set("#EmptyLabel.Visible", empty);

        for (int i = 0; i < ROW_MAX; i++) {
            if (i < colonistOrder.size()) {
                populateRow(i, colonistOrder.get(i), cmd, evt, store);
            } else {
                hideRow(i, cmd);
            }
        }

        bindClose(evt);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#RecallButton",
                new EventData().append("Action", "recall"), false);
    }

    private void populateRow(int i, UUID uuid, UICommandBuilder cmd, UIEventBuilder evt, Store<EntityStore> store) {
        String name = "Colonist";
        String state = "";

        Ref<EntityStore> colonistRef = store.getExternalData().getRefFromUUID(uuid);
        if (colonistRef != null && colonistRef.isValid()) {
            ColonistComponent cc = store.getComponent(colonistRef, ColonistComponent.getComponentType());
            if (cc != null) {
                name = cc.getColonistName();
            }
            JobComponent jc = store.getComponent(colonistRef, JobComponent.getComponentType());
            if (jc != null) {
                JobState js = jc.getCurrentTask();
                state = js != null ? js.name() : "";
            }
        } else {
            state = "offline";
        }

        cmd.set("#Row" + i + ".Visible", true);
        cmd.set("#Row" + i + "Name.Text", name);
        cmd.set("#Row" + i + "State.Text", state);

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#Row" + i + "Inspect",
                new EventData().append("Action", "inspect").append("Index", String.valueOf(i)), false);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#Row" + i + "Fire",
                new EventData().append("Action", "fire").append("Index", String.valueOf(i)), false);
    }

    private void hideRow(int i, UICommandBuilder cmd) {
        cmd.set("#Row" + i + ".Visible", false);
    }

    private void hideAllRows(UICommandBuilder cmd) {
        for (int i = 0; i < ROW_MAX; i++) {
            hideRow(i, cmd);
        }
    }

    private void bindClose(UIEventBuilder evt) {
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                new EventData().append("Action", "close"), false);
    }

    private void refreshPage() {
        if (capturedRef == null || !capturedRef.isValid())
            return;
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder evt = new UIEventBuilder();
        populatePage(cmd, evt, capturedRef.getStore());
        sendUpdate(cmd, evt, false);
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private void handleFire(int index, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (index < 0 || index >= colonistOrder.size())
            return;
        UUID uuid = colonistOrder.get(index);

        World world = store.getExternalData().getWorld();
        WorkStationComponent ws = WorkStationUtil.getWorkStationAt(world, blockPos);
        if (ws == null)
            return;

        ws.removeAssignedColonist(uuid);

        Ref<EntityStore> colonistRef = store.getExternalData().getRefFromUUID(uuid);
        if (colonistRef != null && colonistRef.isValid()) {
            JobAssignmentSystems.fireColonist(colonistRef, store);
            DebugLog.info(DebugCategory.JOB_ASSIGNMENT,
                    "[WorkstationUI] Player fired colonist %s from %s workstation.", uuid, blockPos);
        }
    }

    private void handleInspect(int index, Ref<EntityStore> ref, Store<EntityStore> store) {
        if (index < 0 || index >= colonistOrder.size())
            return;
        UUID uuid = colonistOrder.get(index);

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRefComp = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRefComp == null)
            return;

        Ref<EntityStore> colonistRef = store.getExternalData().getRefFromUUID(uuid);
        if (colonistRef == null || !colonistRef.isValid())
            return;

        NPCEntity npc = store.getComponent(colonistRef, NPCEntity.getComponentType());
        if (npc == null)
            return;

        ItemContainer storage = npc.getInventory().getStorage();
        if (storage == null)
            return;

        // Build HUD overlay before opening the bench page
        ColonistInfoHud hud = new ColonistInfoHud(playerRefComp);
        ColonistComponent cc = store.getComponent(colonistRef, ColonistComponent.getComponentType());
        hud.setData(
                cc != null ? cc.getColonistName() : "Colonist",
                colonistJob(colonistRef, store),
                "Level " + (cc != null ? cc.getColonistLevel() : 1),
                "Colony: " + (cc != null ? cc.getColonyId() : ""));
        player.getHudManager().setCustomHud(playerRefComp, hud);

        ContainerWindow storageWindow = new ContainerWindow(storage);
        storageWindow.registerCloseEvent(e -> player.getHudManager().setCustomHud(playerRefComp, null));
        player.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, storageWindow);
    }

    private void handleRecall(Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        WorkStationComponent ws = WorkStationUtil.getWorkStationAt(world, blockPos);
        if (ws == null)
            return;

        int count = 0;
        for (UUID uuid : ws.getAssignedColonists()) {
            Ref<EntityStore> colonistRef = store.getExternalData().getRefFromUUID(uuid);
            if (colonistRef == null || !colonistRef.isValid())
                continue;

            double ox = (RANDOM.nextDouble() * 2.0 - 1.0) * RECALL_RADIUS;
            double oz = (RANDOM.nextDouble() * 2.0 - 1.0) * RECALL_RADIUS;
            Vector3d pos = new Vector3d(blockPos.x + 0.5 + ox, blockPos.y + 1.0, blockPos.z + 0.5 + oz);
            store.addComponent(colonistRef, Teleport.getComponentType(),
                    new Teleport(world, pos, new Vector3f(0, 0, 0)));
            count++;
        }

        DebugLog.info(DebugCategory.JOB_ASSIGNMENT,
                "[WorkstationUI] Recalled %d colonist(s) to %s.", count, blockPos);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String colonistJob(Ref<EntityStore> colonistRef, Store<EntityStore> store) {
        if (store.getComponent(colonistRef, WoodsmanJobComponent.getComponentType()) != null)
            return "Woodsman";
        if (store.getComponent(colonistRef, MinerJobComponent.getComponentType()) != null)
            return "Miner";
        if (store.getComponent(colonistRef, ConstructorJobComponent.getComponentType()) != null)
            return "Constructor";
        return "Unemployed";
    }

    // -------------------------------------------------------------------------
    // Event data
    // -------------------------------------------------------------------------

    public static class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec
                .builder(UIEventData.class, UIEventData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING),
                        (e, s) -> e.action = s, e -> e.action)
                .add()
                .append(new KeyedCodec<>("Index", Codec.STRING),
                        (e, s) -> e.index = s, e -> e.index)
                .add()
                .build();

        @Nullable
        String action;
        @Nullable
        String index;

        int getIndex() {
            if (index == null)
                return -1;
            try {
                return Integer.parseInt(index);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }
}
