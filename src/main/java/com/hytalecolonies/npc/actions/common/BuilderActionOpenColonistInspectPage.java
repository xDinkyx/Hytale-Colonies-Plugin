package com.hytalecolonies.npc.actions.common;

import javax.annotation.Nonnull;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

/** Builder for the {@code "OpenColonistInspectPage"} NPC action. No configuration. */
public class BuilderActionOpenColonistInspectPage extends BuilderActionBase {

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Opens the colonist inspect page for the interacting player.";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Calls player.getPageManager().openCustomPage() with the colonist's UUID. "
                + "Must be inside an InteractionInstruction HasInteracted block.";
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
        return new ActionOpenColonistInspectPage(this, support);
    }
}
