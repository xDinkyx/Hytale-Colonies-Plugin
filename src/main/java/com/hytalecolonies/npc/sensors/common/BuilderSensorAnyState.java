package com.hytalecolonies.npc.sensors.common;

import javax.annotation.Nonnull;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderSensorBase;
import com.hypixel.hytale.server.npc.instructions.Sensor;

/**
 * Builder for {@link SensorAnyState}.
 *
 * <p>JSON example: {@code { "Type": "AnyState", "States": [".Harvesting", ".Constructing"] }}
 *
 * <p>Fires when the NPC is in ANY of the listed states (OR check).
 */
public class BuilderSensorAnyState extends BuilderSensorBase
{
    private String[] stateStrings;
    private int[][]  stateIndices;
    private boolean  ignoreMissingSetState;

    @Nonnull
    @Override
    public String getShortDescription()
    {
        return "Fires when the NPC is in any of the listed sub-states (OR check).";
    }

    @Nonnull
    @Override
    public String getLongDescription()
    {
        return "Fires when the NPC's current state matches any entry in 'States'. Equivalent to multiple State sensors joined with OR.";
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState()
    {
        return BuilderDescriptorState.WorkInProgress;
    }

    @Nonnull
    @Override
    public Builder<Sensor> readConfig(@Nonnull JsonElement data)
    {
        this.getStringArray(data,
                            "States",
                            arr
                            -> this.stateStrings = arr,
                            null,
                            null,
                            null,
                            BuilderDescriptorState.WorkInProgress,
                            "Sub-state names to check (OR logic). Prefix with '.' for sub-states of the current main state.",
                            null);

        this.getBoolean(data,
                        "IgnoreMissingSetState",
                        v
                        -> this.ignoreMissingSetState = v,
                        false,
                        BuilderDescriptorState.WorkInProgress,
                        "Override and ignore checks for matching setter action that sets this state.",
                        null);

        if (!this.isCreatingDescriptor() && this.stateStrings != null)
        {
            this.stateIndices = new int[this.stateStrings.length][2];

            for (int i = 0; i < this.stateStrings.length; i++)
            {
                final int idx      = i;
                String    stateStr = this.stateStrings[i];
                String    mainState;
                String    subState;

                if (stateStr.startsWith("."))
                {
                    // sub-state of the enclosing main state gate
                    mainState = this.stateHelper.getCurrentParentState();
                    subState  = stateStr.substring(1);
                }
                else
                {
                    int dot = stateStr.indexOf('.');
                    if (dot >= 0)
                    {
                        mainState = stateStr.substring(0, dot);
                        subState  = stateStr.substring(dot + 1);
                    }
                    else
                    {
                        // main state only -- fires on any sub-state (subIdx will be -1)
                        mainState = stateStr;
                        subState  = null;
                    }
                }

                this.registerStateSensor(mainState, subState, (main, sub) -> {
                    this.stateIndices[idx][0] = main;
                    this.stateIndices[idx][1] = sub;
                });

                if (this.ignoreMissingSetState)
                    this.registerStateSetter(mainState, subState, (m, v) -> {});
            }
        }

        return this;
    }

    @Nonnull
    @Override
    public Sensor build(@Nonnull BuilderSupport support)
    {
        return new SensorAnyState(this, support);
    }

    /** Returns the resolved [mainIdx, subIdx] pairs; subIdx == -1 means any sub-state. */
    public int[][] getStateIndices()
    {
        return this.stateIndices;
    }
}
