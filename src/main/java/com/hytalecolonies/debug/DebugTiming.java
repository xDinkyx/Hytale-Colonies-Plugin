package com.hytalecolonies.debug;

/**
 * Lightweight try-with-resources timing utility for performance profiling.
 *
 * <p>Records wall-clock elapsed time for a named block and forwards the result
 * to {@link DebugLog} under the {@link DebugCategory#PERFORMANCE} category.
 *
 * <ul>
 *   <li>If the block finishes under the optional slow threshold, the time is
 *       logged at {@code FINE} (hidden unless the category is set to FINE).
 *   <li>If the block exceeds the slow threshold, the time is logged at
 *       {@code WARNING} — visible by default, drawing attention to slow paths.
 *   <li>If no threshold is provided the time is always logged at {@code FINE}.
 * </ul>
 *
 * <pre>{@code
 *   // Basic — logged at FINE
 *   try (var t = DebugTiming.measure("TreeScanner.detectTrees")) {
 *       confirmedTrees = detectTrees(segmentBottoms, world);
 *   }
 *
 *   // With slow-path warning threshold (ms)
 *   try (var t = DebugTiming.measure("TreeScanner.fullScan", 500)) {
 *       scanForTreeWoodBlocks(workStationPos, chunkStore, commandBuffer);
 *   }
 * }</pre>
 */
public final class DebugTiming {

    private DebugTiming() {}

    /**
     * Starts a timed block. Elapsed time is logged at {@code FINE} when the
     * {@link Timer} is closed.
     */
    public static Timer measure(String label) {
        return new Timer(label, -1L);
    }

    /**
     * Starts a timed block with a slowness threshold.
     * Elapsed time is logged at {@code WARNING} when {@code slowThresholdMs} is
     * exceeded; otherwise it is logged at {@code FINE}.
     *
     * @param slowThresholdMs milliseconds above which a WARNING is emitted
     */
    public static Timer measure(String label, long slowThresholdMs) {
        return new Timer(label, slowThresholdMs);
    }

    /** A single timed measurement — use in a try-with-resources block. */
    public static final class Timer implements AutoCloseable {

        private final String label;
        private final long slowThresholdMs;
        private final long startNanos;

        private Timer(String label, long slowThresholdMs) {
            this.label = label;
            this.slowThresholdMs = slowThresholdMs;
            this.startNanos = System.nanoTime();
        }

        @Override
        public void close() {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            if (slowThresholdMs >= 0 && elapsedMs > slowThresholdMs) {
                DebugLog.warning(DebugCategory.PERFORMANCE,
                        "[Performance] %s took %d ms (threshold: %d ms) — SLOW.",
                        label, elapsedMs, slowThresholdMs);
            } else {
                DebugLog.fine(DebugCategory.PERFORMANCE,
                        "[Performance] %s took %d ms.", label, elapsedMs);
            }
        }
    }
}
