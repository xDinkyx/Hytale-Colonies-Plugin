package com.hytalecolonies.npc.actions.common;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringHolder;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;

/**
 * Builder for {@code "NavigateTo"} -- dispatches one-shot navigation via {@code MoveToTargetComponent}.
 *
 * <p>JSON: {@code { "Type": "NavigateTo", "Target": "Workstation" }}
 *
 * <p>Supported {@code Target} values:
 * <ul>
 *   <li>{@code "Workstation"} -- navigates to the workstation from {@code JobComponent} and re-anchors the leash.</li>
 *   <li>{@code "JobTarget"} -- navigates to {@code JobTargetComponent.targetPosition} (the currently claimed work block).</li>
 * </ul>
 */
public class BuilderActionNavigateTo extends BuilderActionBase {

    private final StringHolder target = new StringHolder();

    @Nonnull @Override public String getShortDescription() { return "Dispatches navigation toward the named target using MoveToTargetComponent."; }
    @Nonnull @Override public String getLongDescription() { return getShortDescription(); }
    @Nonnull @Override public BuilderDescriptorState getBuilderDescriptorState() { return BuilderDescriptorState.WorkInProgress; }

    @Nonnull
    @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        this.getString(data, "Target", this.target, "Workstation",
                null,
                BuilderDescriptorState.WorkInProgress,
                "Navigation target type. Supported: Workstation, JobTarget.", null);
        return this;
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport support) {
        return new ActionNavigateTo(this, support);
    }

    @Nonnull
    public String getTarget(@Nonnull BuilderSupport support) {
        return this.target.get(support.getExecutionContext());
    }
}
