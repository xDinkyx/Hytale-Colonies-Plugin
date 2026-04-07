package com.hytalecolonies.debug;

import com.hytalecolonies.HytaleColoniesPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Utility for category-gated logging in the Hytale Colonies plugin.
 *
 * <p>Each method corresponds to a specific log level. The category's configured
 * minimum level ({@link DebugCategory#getMinLevel()}) acts as a threshold:
 * messages below it are silently discarded. Setting a category to
 * {@link Level#OFF} suppresses all its output.
 *
 * <pre>{@code
 *   DebugLog.fine(DebugCategory.MOVEMENT, "[Movement] xzDist=%.2f", dist);   // verbose, hidden by default
 *   DebugLog.info(DebugCategory.MOVEMENT, "[Movement] Arrived at %s.", target);
 *   DebugLog.warning(DebugCategory.MOVEMENT, "[Movement] Nav target is null.");
 *   // runtime-configurable level (e.g. from JSON): use DebugLogUtil.parseLevel("DEBUG") -- DEBUG is an alias for FINE
 *   DebugLog.log(DebugCategory.GENERAL, level, "[LogDebug] [%s] %s", npcId, msg);
 * }</pre>
 */
public final class DebugLog {

    private DebugLog() {}

    /**
     * Returns the UUID string of the NPC entity for use in log messages, or {@code "?"}
     * if no {@link UUIDComponent} is present.
     */
    public static String npcId(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent comp = store.getComponent(ref, UUIDComponent.getComponentType());
        return comp != null ? comp.getUuid().toString() : "?";
    }

    /** Verbose debug message -- only visible when category is set to FINE or DEBUG. Alias: {@link #debug}. */
    public static void fine(DebugCategory category, String format, Object... args) {
        emit(category, Level.FINE, format, args);
    }

    /** Alias for {@link #fine} -- matches the {@code "DEBUG"} level name accepted by {@link DebugLogUtil#parseLevel}. */
    public static void debug(DebugCategory category, String format, Object... args) {
        emit(category, Level.FINE, format, args);
    }

    /** Informational message -- visible at the default INFO threshold. */
    public static void info(DebugCategory category, String format, Object... args) {
        emit(category, Level.INFO, format, args);
    }

    /** Warning message -- always visible unless the category is OFF. */
    public static void warning(DebugCategory category, String format, Object... args) {
        emit(category, Level.WARNING, format, args);
    }

    /** Severe/error message -- always visible unless the category is OFF. */
    public static void severe(DebugCategory category, String format, Object... args) {
        emit(category, Level.SEVERE, format, args);
    }

    /** Logs at the given {@link Level}. The level may be obtained via {@link DebugLogUtil#parseLevel} (supports {@code "DEBUG"} as alias for FINE). */
    public static void log(DebugCategory category, Level level, String format, Object... args) {
        emit(category, level, format, args);
    }

    private static void emit(DebugCategory category, Level level, String format, Object... args) {
        Level min = category.getMinLevel();
        if (min == Level.OFF) return;
        if (level.intValue() >= min.intValue()) {
            HytaleColoniesPlugin.LOGGER.at(level).logVarargs(format, args);
        }
    }
}
