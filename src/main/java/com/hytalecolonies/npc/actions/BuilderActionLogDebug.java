package com.hytalecolonies.npc.actions;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringHolder;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLogUtil;
import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Builder for the {@code "LogDebug"} NPC action.
 *
 * <p>JSON: {@code { "Type": "LogDebug", "Message": "...", "Category": "JOB_SYSTEM", "Level": "INFO" }}
 */
public class BuilderActionLogDebug extends BuilderActionBase {

    private final StringHolder message  = new StringHolder();
    private final StringHolder category = new StringHolder();
    private final StringHolder level    = new StringHolder();

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Logs a configurable message to a debug category at a configurable level.";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Useful for tracing StateTransitions and Instructions. Parameters: Message, Category (default GENERAL), Level (default INFO; use DEBUG as alias for FINE).";
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull
    @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        this.getString(data, "Message", this.message, "(no message)",
                null, BuilderDescriptorState.Experimental, "The message to log.", null);
        this.getString(data, "Category", this.category, "GENERAL",
                null, BuilderDescriptorState.Experimental,
                "DebugCategory to log to (e.g. GENERAL, WOODSMAN_JOB, MINER_JOB, JOB_SYSTEM).", null);
        this.getString(data, "Level", this.level, "INFO",
                null, BuilderDescriptorState.Experimental,
                "Log level: OFF, FINE, DEBUG (alias for FINE), INFO, WARNING, SEVERE.", null);
        return this;
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport support) {
        return new ActionLogDebug(this, support);
    }

    @Nonnull
    public String getMessage(@Nonnull BuilderSupport support) {
        return this.message.get(support.getExecutionContext());
    }

    @Nonnull
    public DebugCategory getCategory(@Nonnull BuilderSupport support) {
        return DebugLogUtil.parseCategory(this.category.get(support.getExecutionContext()));
    }

    @Nonnull
    public Level getLevel(@Nonnull BuilderSupport support) {
        return DebugLogUtil.parseLevel(this.level.get(support.getExecutionContext()));
    }
}
