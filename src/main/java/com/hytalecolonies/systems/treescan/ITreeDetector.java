package com.hytalecolonies.systems.treescan;

import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;

/**
 * Strategy interface for determining whether a candidate block position is
 * the base of a harvestable tree.
 *
 * <p>Swap implementations in {@link TreeScannerSystem} by changing the
 * field type — both {@link TreeDetector} (simple vertical stack) and
 * {@link TreeDetectorBFS} (flood-fill) implement this interface.
 */
public interface ITreeDetector {

    /**
     * Returns {@code true} if the block at {@code candidatePos} is the base of a
     * natural, harvestable tree.
     *
     * @param candidatePos world position of the lowest wood block to test
     * @param world        the world in which to look up adjacent blocks
     */
    boolean isTreeBase(Vector3i candidatePos, World world);
}
