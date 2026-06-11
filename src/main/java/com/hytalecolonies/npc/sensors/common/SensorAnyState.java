package com.hytalecolonies.npc.sensors.common;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

/**
 * Fires when the NPC is in ANY of the states registered by {@link BuilderSensorAnyState}.
 * Each entry is a [mainIdx, subIdx] pair; subIdx == -1 matches any sub-state.
 */
public class SensorAnyState extends SensorBase
{
    /** Resolved state pairs: each {@code int[2]} is {@code [mainIdx, subIdx]}. subIdx == -1 means any sub-state. */
    private final int[][] stateIndices;

    public SensorAnyState(@Nonnull BuilderSensorAnyState builder, @Nonnull BuilderSupport support)
    {
        super(builder);
        this.stateIndices = builder.getStateIndices();
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role, double dt, @Nonnull Store<EntityStore> store)
    {
        if (!super.matches(ref, role, dt, store))
            return false;

        StateSupport stateSupport = role.getStateSupport();
        for (int[] pair : stateIndices)
        {
            int mainIdx = pair[0];
            int subIdx  = pair[1];
            if (stateSupport.inState(mainIdx) && (subIdx == -1 || stateSupport.inSubState(subIdx)))
                return true;
        }
        return false;
    }

    @Override
    public InfoProvider getSensorInfo()
    {
        return null;
    }
}
