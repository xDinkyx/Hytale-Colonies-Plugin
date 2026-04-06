package com.hytalecolonies.npc.actions;

import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import javax.annotation.Nonnull;

/** @deprecated Use {@link ActionSeekNextMineBlock} instead. */
@Deprecated
public class ActionClaimNextMineBlock extends ActionSeekNextMineBlock {

    public ActionClaimNextMineBlock(@Nonnull BuilderActionSeekNextMineBlock builder,
                                     @Nonnull BuilderSupport support) {
        super(builder, support);
    }
}
