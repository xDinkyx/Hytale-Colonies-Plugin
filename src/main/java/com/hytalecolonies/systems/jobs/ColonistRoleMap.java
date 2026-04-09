package com.hytalecolonies.systems.jobs;

import com.hytalecolonies.components.jobs.JobType;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.systems.RoleChangeSystem;

import javax.annotation.Nonnull;

/**
 * Maps {@link JobType} values to their corresponding NPC role names and provides
 * the utility method to request a live role switch via {@link RoleChangeSystem}.
 *
 * <p>Edit the {@link #roleFor} method when adding new job types or renaming role
 * JSON files.
 */
public final class ColonistRoleMap {

    /** The NPC role used by unassigned (unemployed) colonists. */
    public static final String ROLE_GENERIC = "Colonist_Dummy";

    private ColonistRoleMap() {}

    /**
     * Returns the NPC role name that should be applied to a colonist with the
     * given job type. Falls back to {@link #ROLE_GENERIC} for unmapped types.
     */
    @Nonnull
    public static String roleFor(@Nonnull JobType jobType) {
        return switch (jobType) {
            case Miner       -> "Colonist_Miner";
            case Woodsman    -> "Colonist_Woodsman";
            case Constructor -> "Colonist_Constructor";
            default          -> ROLE_GENERIC;
        };
    }

    /**
     * Requests a live NPC role switch for the given colonist entity.
     * Safe to call from {@link com.hypixel.hytale.component.system.RefChangeSystem} callbacks --
     * the actual swap is deferred to {@link RoleChangeSystem}'s tick.
     *
     * <p>No-ops if the entity has no {@link NPCEntity} component, no role loaded yet,
     * or a role change is already in-flight.
     */
    public static void switchRole(@Nonnull Ref<EntityStore> ref,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull String roleName) {
        NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
        if (npcEntity == null) {
            DebugLog.warning(DebugCategory.JOB_ASSIGNMENT,
                    "[RoleSwitch] [%s] Colonist has no NPCEntity component -- cannot switch role to '%s'.",
                    DebugLog.npcId(ref, store), roleName);
            return;
        }
        Role currentRole = npcEntity.getRole();
        if (currentRole == null || currentRole.isRoleChangeRequested()) {
            return; // Already changing or no role loaded yet.
        }
        int newRoleIndex = NPCPlugin.get().getIndex(roleName);
        if (newRoleIndex < 0) {
            DebugLog.warning(DebugCategory.JOB_ASSIGNMENT,
                    "[RoleSwitch] [%s] Unknown NPC role '%s' -- cannot switch.",
                    DebugLog.npcId(ref, store), roleName);
            return;
        }
        DebugLog.info(DebugCategory.JOB_ASSIGNMENT,
                "[RoleSwitch] [%s] Switching colonist role to '%s' (index %d).",
                DebugLog.npcId(ref, store), roleName, newRoleIndex);
        RoleChangeSystem.requestRoleChange(ref, currentRole, newRoleIndex, false, store);
    }
}
