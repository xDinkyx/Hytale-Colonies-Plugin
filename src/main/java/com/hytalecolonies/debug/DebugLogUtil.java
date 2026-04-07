package com.hytalecolonies.debug;

import java.util.logging.Level;

/**
 * Shared converters for debug configuration strings.
 *
 * <p>Centralises the string-to-{@link Level} and string-to-{@link DebugCategory} logic
 * so that {@link DebugConfig}, {@link com.hytalecolonies.npc.actions.ActionLogDebug}, and
 * any future callers all apply the same rules.
 */
public final class DebugLogUtil {

    private DebugLogUtil() {}

    /**
     * Converts a level name string to a {@link Level}.
     * Case-insensitive. Unrecognised values default to {@link Level#INFO}.
     *
     * <p>Recognised names: {@code OFF}, {@code SEVERE}, {@code WARNING}, {@code INFO},
     * {@code FINE}, {@code DEBUG} (alias for {@code FINE}).
     */
    public static Level parseLevel(String name) {
        if (name == null) return Level.INFO;
        return switch (name.toUpperCase()) {
            case "OFF"     -> Level.OFF;
            case "SEVERE"  -> Level.SEVERE;
            case "WARNING" -> Level.WARNING;
            case "FINE"    -> Level.FINE;
            case "DEBUG"   -> Level.FINE;
            default        -> Level.INFO;
        };
    }

    /**
     * Converts a category name string to a {@link DebugCategory}.
     * Case-insensitive. Unrecognised values default to {@link DebugCategory#GENERAL}.
     */
    public static DebugCategory parseCategory(String name) {
        if (name == null) return DebugCategory.GENERAL;
        try {
            return DebugCategory.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return DebugCategory.GENERAL;
        }
    }
}
