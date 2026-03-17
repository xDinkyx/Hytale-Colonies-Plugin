package com.hytalecolonies.debug;

import com.hytalecolonies.HytaleColoniesPlugin;

import java.util.logging.Level;

/**
 * Utility for category-gated logging in the Hytale Colonies plugin.
 *
 * <p>Every log call is routed through a {@link DebugCategory}. The category's
 * configured minimum level ({@link DebugCategory#getMinLevel()}) acts as a
 * threshold: the message is emitted only when {@code messageLevel >= minLevel}.
 * Setting a category to {@link Level#OFF} suppresses all its output.
 *
 * <pre>{@code
 *   // Always-visible INFO (shown by default)
 *   DebugLog.log(DebugCategory.MOVEMENT, Level.INFO, "[Movement] Arrived at %s.", target);
 *
 *   // Debug-only FINE (hidden unless category is set to FINE)
 *   DebugLog.log(DebugCategory.MOVEMENT, "[Movement] xzDist=%.2f", dist);
 * }</pre>
 */
public final class DebugLog {

    private DebugLog() {}

    /**
     * Shorthand for FINE-level debug messages.
     * Only emitted if the category's minimum level is {@link Level#FINE} or lower.
     */
    public static void log(DebugCategory category, String format, Object... args) {
        log(category, Level.FINE, format, args);
    }

    /**
     * Emits a log message at {@code level} if the category's minimum level allows it.
     *
     * @param category    the category that controls visibility
     * @param level       the severity of this message (FINE, INFO, WARNING, SEVERE)
     * @param format      printf-style format string
     * @param args        format arguments
     */
    public static void log(DebugCategory category, Level level, String format, Object... args) {
        Level min = category.getMinLevel();
        if (min == Level.OFF) return;
        if (level.intValue() >= min.intValue()) {
            HytaleColoniesPlugin.LOGGER.at(level).logVarargs(format, args);
        }
    }
}
