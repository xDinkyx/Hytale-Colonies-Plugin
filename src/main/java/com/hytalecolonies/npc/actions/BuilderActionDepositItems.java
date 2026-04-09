package com.hytalecolonies.npc.actions;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;

/**
 * Builder for the {@code "DepositItems"} custom NPC action.
 *
 * <p>Deposits all non-tool items from the colonist's inventory into the delivery
 * container set by {@code FindDeliveryContainer}. Clears the delivery target on
 * completion. No configuration parameters are required.
 *
 * <p>JSON usage (always pair with {@code SetEcsJobState} after):
 * <pre>{@code
 * {
 *   "Sensor": { "Type": "JobTarget", "Range": 3.0 },
 *   "Actions": [
 *     { "Type": "DepositItems" },
 *     { "Type": "SetEcsJobState", "JobState": "TravelingToHome" }
 *   ]
 * }
 * }</pre>
 *
 * <p>Registered via {@code NPCPlugin.get().registerCoreComponentType("DepositItems", ...)}
 * in {@link com.hytalecolonies.HytaleColoniesPlugin#setup()}.
 */
public class BuilderActionDepositItems extends BuilderActionBase {

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Deposits all non-tool items into the delivery container and clears the delivery target.";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Moves non-tool items from the colonist's storage to the item-container block at "
             + "deliveryContainerPosition. Clears the delivery target and job target on completion. "
             + "Does not change the ECS job state -- pair with SetEcsJobState in the same Action list.";
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull
    @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        return this;
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport support) {
        return new ActionDepositItems(this, support);
    }
}
