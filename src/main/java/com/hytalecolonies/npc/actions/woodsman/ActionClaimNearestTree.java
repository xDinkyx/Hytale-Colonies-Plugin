package com.hytalecolonies.npc.actions.woodsman;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;

/** @deprecated Use {@link ActionSeekNearestTree} instead. */
@Deprecated
public class ActionClaimNearestTree extends ActionSeekNearestTree
{
    public ActionClaimNearestTree(@Nonnull BuilderActionSeekNearestTree builder, @Nonnull BuilderSupport support)
    {
        super(builder, support);
    }
}
