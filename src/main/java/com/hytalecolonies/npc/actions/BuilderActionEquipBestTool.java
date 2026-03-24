package com.hytalecolonies.npc.actions;

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

/**
 * Builder for the custom {@code "EquipBestTool"} NPC action.
 *
 * <p>JSON usage in an NPC role template:
 * <pre>{@code
 * { "Type": "EquipBestTool", "GatherType": "Woodcutting", "MinQuality": 0 }
 * }</pre>
 *
 * <p>Registered via {@code NPCPlugin.get().registerCoreComponentType("EquipBestTool", ...)}
 * in {@link com.hytalecolonies.HytaleColoniesPlugin#setup()}.
 */
public class BuilderActionEquipBestTool extends BuilderActionBase {

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
             + "the given GatherType at the given MinQuality tier and equips it. No-ops if the current held item "
             + "is already the optimal choice.";
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull
    @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        this.requireString(
            data, "GatherType", this.gatherType, null,
            BuilderDescriptorState.Experimental,
            "The gather type to find a tool for (e.g. Woodcutting, Mining)", null
        );
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

    @Nonnull
    public String getGatherType(@Nonnull BuilderSupport support) {
        ExecutionContext ctx = support.getExecutionContext();
        return this.gatherType.get(ctx);
    }

    public int getMinQuality(@Nonnull BuilderSupport support) {
        ExecutionContext ctx = support.getExecutionContext();
        return this.minQuality.get(ctx);
    }
}
