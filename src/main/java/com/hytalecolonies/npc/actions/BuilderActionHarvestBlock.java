package com.hytalecolonies.npc.actions;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.holder.DoubleHolder;
import com.hypixel.hytale.server.npc.asset.builder.validators.DoubleRangeValidator;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import com.hypixel.hytale.server.npc.util.expression.ExecutionContext;
import javax.annotation.Nonnull;

/**
 * Builder for the {@code "HarvestBlock"} custom NPC action.
 *
 * <p>Applies one swing of block damage per tick to the block position provided
 * by the active sensor. Block-breaking physics -- including spawn-in of drop
 * items -- are delegated to
 * {@link com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils}.
 *
 * <p>JSON usage in an NPC role template:
 * <pre>{@code
 * { "Type": "HarvestBlock", "DamageScale": 1.0 }
 * }</pre>
 *
 * <p>Registered via {@code NPCPlugin.get().registerCoreComponentType("HarvestBlock", ...)}
 * in {@link com.hytalecolonies.HytaleColoniesPlugin#setup()}.
 */
public class BuilderActionHarvestBlock extends BuilderActionBase {

    private final DoubleHolder damageScale = new DoubleHolder();

    @Nonnull
    @Override
    public String getShortDescription() {
        return "Apply one swing of block damage to the sensor's target block position.";
    }

    @Nonnull
    @Override
    public String getLongDescription() {
        return "Each tick the action is active it calls BlockHarvestUtils.performBlockDamage() "
             + "on the block at the position provided by the active sensor. "
             + "Uses the entity's currently held tool. Returns true when the block breaks.";
    }

    @Nonnull
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Experimental;
    }

    @Nonnull
    @Override
    public Builder<Action> readConfig(@Nonnull JsonElement data) {
        this.getDouble(
            data, "DamageScale", this.damageScale, 1.0,
            DoubleRangeValidator.fromExclToIncl(0.0, Double.MAX_VALUE),
            BuilderDescriptorState.Experimental,
            "Multiplier applied to the per-swing block damage (default 1.0)", null
        );
        return this;
    }

    @Nonnull
    @Override
    public Action build(@Nonnull BuilderSupport support) {
        return new ActionHarvestBlock(this, support);
    }

    public float getDamageScale(@Nonnull BuilderSupport support) {
        ExecutionContext ctx = support.getExecutionContext();
        return (float) this.damageScale.get(ctx);
    }
}
