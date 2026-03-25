package com.hytalecolonies.debug;

import java.util.logging.Level;

/**
 * Debug log categories for the Hytale Colonies plugin.
 * Each category has an independent minimum log level. Messages below the threshold
 * are silently discarded, keeping the console clean while still allowing fine-grained
 * debug output per subsystem when needed.
 *
 * <p>Default threshold: {@link Level#INFO} — INFO/WARNING/SEVERE are visible,
 * FINE (debug) messages are hidden until explicitly enabled.
 */
public enum DebugCategory {

    MOVEMENT("Movement"),
    JOB_ASSIGNMENT("Job Assignment"),
    WOODSMAN_JOB("Woodsman Job"),
    MINER_JOB("Miner Job"),
    TREE_SCANNER("Tree Scanner"),
    COLONIST_DELIVERY("Colonist Delivery"),
    PERFORMANCE("Performance");

    private final String displayName;
    private volatile Level minLevel = Level.INFO;

    DebugCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Level getMinLevel() {
        return minLevel;
    }

    public void setMinLevel(Level minLevel) {
        this.minLevel = minLevel;
    }
}
