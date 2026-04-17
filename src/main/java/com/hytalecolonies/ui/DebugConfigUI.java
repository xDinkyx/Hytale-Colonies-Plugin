package com.hytalecolonies.ui;

import javax.annotation.Nonnull;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugConfig;

/**
 * Interactive UI page for adjusting per-category debug log levels and
 * debug visualization toggles at runtime.
 *
 * <p>Each category row shows a cycle {@code TextButton} -- clicking it advances
 * the level through FINE -> INFO -> WARNING -> SEVERE -> OFF -> FINE.
 * Two toggle buttons control debug drawing for colonist paths and tree detection.
 */
public class DebugConfigUI extends InteractiveCustomUIPage<DebugConfigUI.UIEventData> {

    public static final String LAYOUT = "hytalecolonies/DebugConfig.ui";

    private static final String[] LEVEL_CYCLE = { "FINE", "INFO", "WARNING", "SEVERE", "OFF" };

    private final Config<DebugConfig> debugConfig;

    public DebugConfigUI(@Nonnull PlayerRef playerRef, @Nonnull Config<DebugConfig> debugConfig) {
        super(playerRef, CustomPageLifetime.CanDismiss, UIEventData.CODEC);
        this.debugConfig = debugConfig;
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder cmd,
            @Nonnull UIEventBuilder evt,
            @Nonnull Store<EntityStore> store
    ) {
        cmd.append(LAYOUT);

        DebugConfig config = debugConfig.get();

        // Set initial button texts and bind cycle events for each log-level category
        for (DebugCategory category : DebugCategory.values()) {
            String buttonId = buttonId(category);
            cmd.set("#" + buttonId + ".Text", levelLabel(config.getLevelNameForCategory(category)));

            evt.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#" + buttonId,
                new EventData()
                    .append("Action", "cycle_level")
                    .append("Category", category.name()),
                false
            );
        }

        // Set initial draw toggle button texts
        cmd.set("#DrawColonistPathsButton.Text", config.isDrawColonistPaths() ? "ON" : "OFF");
        cmd.set("#DrawTreeDetectionButton.Text",  config.isDrawTreeDetection()  ? "ON" : "OFF");
        cmd.set("#DrawConstructorOrdersButton.Text", config.isDrawConstructorOrders() ? "ON" : "OFF");

        // Bind draw toggle events
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#DrawColonistPathsButton",
            new EventData()
                .append("Action", "toggle_draw")
                .append("DrawTarget", "colonist_paths"),
            false
        );
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#DrawTreeDetectionButton",
            new EventData()
                .append("Action", "toggle_draw")
                .append("DrawTarget", "tree_detection"),
            false
        );
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#DrawConstructorOrdersButton",
            new EventData()
                .append("Action", "toggle_draw")
                .append("DrawTarget", "constructor_orders"),
            false
        );

        // Bind close button
        evt.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CloseButton",
            new EventData().append("Action", "close"),
            false
        );
    }

    @Override
    public void handleDataEvent(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull UIEventData data
    ) {
        if (data.action == null) return;
        DebugConfig config = debugConfig.get();

        switch (data.action) {
            case "cycle_level": {
                if (data.category == null) return;
                DebugCategory category;
                try {
                    category = DebugCategory.valueOf(data.category);
                } catch (IllegalArgumentException e) {
                    return;
                }
                String nextName = nextLevel(config.getLevelNameForCategory(category));
                config.setLevelForCategory(category, DebugConfig.parseLevelName(nextName));
                debugConfig.save();

                UICommandBuilder update = new UICommandBuilder();
                update.set("#" + buttonId(category) + ".Text", levelLabel(nextName));
                this.sendUpdate(update, false);
                break;
            }
            case "toggle_draw": {
                if (data.drawTarget == null) return;
                UICommandBuilder update = new UICommandBuilder();
                if ("colonist_paths".equals(data.drawTarget)) {
                    boolean newValue = !config.isDrawColonistPaths();
                    config.setDrawColonistPaths(newValue);
                    update.set("#DrawColonistPathsButton.Text", newValue ? "ON" : "OFF");
                } else if ("tree_detection".equals(data.drawTarget)) {
                    boolean newValue = !config.isDrawTreeDetection();
                    config.setDrawTreeDetection(newValue);
                    update.set("#DrawTreeDetectionButton.Text", newValue ? "ON" : "OFF");
                } else if ("constructor_orders".equals(data.drawTarget)) {
                    boolean newValue = !config.isDrawConstructorOrders();
                    config.setDrawConstructorOrders(newValue);
                    update.set("#DrawConstructorOrdersButton.Text", newValue ? "ON" : "OFF");
                }
                debugConfig.save();
                this.sendUpdate(update, false);
                break;
            }
            case "close":
                this.close();
                break;
        }
    }

    /** Maps a DebugCategory to its TextButton element ID in the .ui file. */
    private static String buttonId(DebugCategory category) {
        String[] parts = category.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
        }
        sb.append("Button");
        return sb.toString();
    }

    /** Returns the next level name in the cycle order: FINE -> INFO -> WARNING -> SEVERE -> OFF -> FINE. */
    private static String nextLevel(String currentName) {
        for (int i = 0; i < LEVEL_CYCLE.length; i++) {
            if (LEVEL_CYCLE[i].equalsIgnoreCase(currentName)) {
                return LEVEL_CYCLE[(i + 1) % LEVEL_CYCLE.length];
            }
        }
        return "INFO";
    }

    /** Returns the display label for a level name. */
    private static String levelLabel(String name) {
        switch (name.toUpperCase()) {
            case "FINE":    return "FINE (debug)";
            case "INFO":    return "INFO";
            case "WARNING": return "WARNING";
            case "SEVERE":  return "SEVERE";
            case "OFF":     return "OFF";
            default:        return name;
        }
    }

    public static class UIEventData {
        public static final BuilderCodec<UIEventData> CODEC = BuilderCodec.builder(
                UIEventData.class, UIEventData::new
        )
        .append(new KeyedCodec<>("Action",     Codec.STRING), (e, v) -> e.action     = v, e -> e.action)
        .add()
        .append(new KeyedCodec<>("Category",   Codec.STRING), (e, v) -> e.category   = v, e -> e.category)
        .add()
        .append(new KeyedCodec<>("DrawTarget", Codec.STRING), (e, v) -> e.drawTarget = v, e -> e.drawTarget)
        .add()
        .build();

        private String action;
        private String category;
        private String drawTarget;

        public UIEventData() {}
    }
}

