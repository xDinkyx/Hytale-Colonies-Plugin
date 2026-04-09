package com.hytalecolonies.npc.actions.constructor;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;

/** Builder for the {@code "SeekNextClearingBlock"} NPC action. No configuration. */
public class BuilderActionSeekNextClearingBlock extends BuilderActionBase {

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Scans for the next block to clear from the active construction prefab.";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Claims the first unclaimed solid block that the prefab expects to be air, and "
                + "sets the NPC nav target toward it. Sets workAvailable=false when clearing is complete.";
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
        return new ActionSeekNextClearingBlock(this, support);
    }
}
