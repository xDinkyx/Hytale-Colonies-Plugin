package com.hytalecolonies.npc.actions;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import java.util.logging.Level;

/**
 * Logs a configurable message to a configurable {@link DebugCategory} at a configurable {@link Level}.
 * Category and level are resolved at construction time from JSON parameters.
 */
public class ActionLogDebug extends ActionBase {

    private final String message;
    private final DebugCategory category;
    private final Level level;

    public ActionLogDebug(@Nonnull BuilderActionLogDebug builder,
                          @Nonnull BuilderSupport support) {
        super(builder);
        this.message  = builder.getMessage(support);
        this.category = builder.getCategory(support);
        this.level    = builder.getLevel(support);
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           @Nullable InfoProvider sensorInfo, double dt,
                           @Nonnull Store<EntityStore> store) {
        DebugLog.log(category, level,
                "[LogDebug] [%s] %s", DebugLog.npcId(ref, store), message);
        return true;
    }
}
