package com.hytalecolonies.systems.jobs;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;
import com.hytalecolonies.components.jobs.JobType;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;

/**
 * Mapping {@link JobType} values with corresponding NPC json role names and switch role logic using native {@link RoleChangeSystem}.
 *
 * <p>
 * Update {@link #roleFor(JobType)} for new/updated json roles.
 */
public final class ColonistRoleMap
{
    /** The NPC role used by unassigned (unemployed) colonists. */
    public static final String ROLE_GENERIC = "Colonist_Jobless";

    private ColonistRoleMap() {}

    /**
     * Returns the NPC role name that should be applied to a colonist with the given job type. Falls back to {@link #ROLE_GENERIC} for unmapped types.
     */
    @Nonnull
    public static String roleFor(@Nonnull JobType jobType)
    {
        return switch (jobType)
        {
            case Miner -> "Colonist_Miner";
            case Woodsman -> "Colonist_Woodsman";
            case Constructor -> "Colonist_Constructor";
            default -> ROLE_GENERIC;
        };
    }

    /**
     * Requests a role switch for a colonist NPC. The swap is deferred to native {@link RoleChangeSystem}'s tick.
     */
    public static void switchRole(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String roleName)
    {
        NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
        if (npcEntity == null)
        {
            DebugLog.warning(DebugCategory.JOB_ASSIGNMENT,
                             "[RoleSwitch] [%s] Colonist has no NPCEntity component -- cannot switch role to '%s'.",
                             DebugLog.npcId(ref, store),
                             roleName);
            return;
        }

        Role currentRole = npcEntity.getRole();
        if (currentRole == null || currentRole.isRoleChangeRequested())
        {
            return; // Already changing or no role loaded yet.
        }

        int newRoleIndex = NPCPlugin.get().getIndex(roleName);
        if (newRoleIndex < 0)
        {
            DebugLog.warning(DebugCategory.JOB_ASSIGNMENT, "[RoleSwitch] [%s] Unknown NPC role '%s' -- cannot switch.", DebugLog.npcId(ref, store), roleName);
            return;
        }
        
        DebugLog.info(DebugCategory.JOB_ASSIGNMENT,
                      "[RoleSwitch] [%s] Switching colonist role to '%s' (index %d).",
                      DebugLog.npcId(ref, store),
                      roleName,
                      newRoleIndex);
        RoleChangeSystem.requestRoleChange(ref, currentRole, newRoleIndex, false, store);
    }
}
