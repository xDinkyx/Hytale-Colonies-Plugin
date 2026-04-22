package com.hytalecolonies.npc.actions.common;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.IntHolder;
import com.hypixel.hytale.server.npc.asset.builder.holder.StringHolder;
import com.hypixel.hytale.server.npc.asset.builder.validators.IntSingleValidator;
import com.hypixel.hytale.server.npc.util.expression.ExecutionContext;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Builder for the {@code "EquipBestTool"} custom NPC action.
 *
 * <p>JSON: {@code { "Type": "EquipBestTool", "GatherType": "Woodcutting", "MinQuality": 0 }}
 */
public class BuilderActionEquipBestTool extends BuilderActionBase {

    /** Configured gather type (may be a computable expression), or {@code null} to auto-detect. */
    private final StringHolder gatherType = new StringHolder();
    private final IntHolder minQuality = new IntHolder();

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Equip the best tool for a gather type from the entity's inventory.";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Searches the entity's full inventory (hotbar first, then storage) for the best tool matching "
             + "the target block's gather type requirements and equips it. When GatherType is omitted the "
             + "required type is resolved automatically from the block at the sensor's provided position. "
             + "No-ops if the current held item is already the optimal choice.";
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull
    @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        this.getString(data, "GatherType", this.gatherType, null,
                null, BuilderDescriptorState.Experimental,
                "Gather type to look up the best tool for (e.g. Woodcutting, Mining). Omit to auto-detect from sensor block.", null);
        this.getInt(
            data, "MinQuality", this.minQuality, 0, IntSingleValidator.greaterEqual0(),
            BuilderDescriptorState.Experimental,
            "Minimum tool quality tier required (0 = any)", null
        );
        return this;
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport support) {
        return new ActionEquipBestTool(this, support);
    }

    @Nullable
    public String getGatherType(@Nonnull BuilderSupport support) {
        String val = this.gatherType.get(support.getExecutionContext());
        return (val == null || val.isEmpty()) ? null : val;
    }

    public int getMinQuality(@Nonnull BuilderSupport support) {
        ExecutionContext ctx = support.getExecutionContext();
        return this.minQuality.get(ctx);
    }
}
