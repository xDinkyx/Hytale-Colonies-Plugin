package com.hytalecolonies.utils;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;


/**
 * Helpers for updating a colonist's job state in both ECS ({@link JobComponent}) and
 * the NPC role state machine so that JSON {@code "Type": "State"} sensors and
 * {@code StateTransitions} work correctly.
 */
public final class ColonistStateUtil
{
    /**
     * Capability token for {@link JobComponent#setCurrentTask}.
     * The constructor is private and the singleton is package-private, so only code
     * inside {@code com.hytalecolonies.utils} can obtain a reference -- enforcing that
     * all job-state writes go through {@link #setJobState}.
     */
    public static final class Key
    {
        private Key() {}
    }

    static final Key INSTANCE = new Key();

    private ColonistStateUtil()
    {
    }

    /**
     * Sets {@code job.jobState} and mirrors the new state into the NPC role state machine
     * so that JSON {@code "Type": "State"} sensors match and {@code StateTransitions} fire.
     *
     * <p>Safe to call from any tick context. If the entity has no {@link NPCEntity}
     * component or the role is not yet initialised, the state is still written to
     * {@link JobComponent} -- the mirror is silently skipped.
     */
    public static void setJobState(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull JobComponent job, @Nonnull JobState state)
    {
        JobState previousState = job.getCurrentTask();
        job.setCurrentTask(INSTANCE, state);

        DebugLog.fine(DebugCategory.JOB_SYSTEM,
                "[StateTransition] [%s] %s -> %s.", DebugLog.npcId(ref, store), previousState, state);

        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null)
            return;

        Role role = npc.getRole();
        if (role == null)
            return;

        role.getStateSupport().setState(ref, npcMainState(state), npcSubState(state), store);
    }

    /**
     * Maps a {@link JobState} to the NPC role main-state name.
     * Idling substates belong to main state {@code "Idling"},
     * Working substates to {@code "Working"}, others are standalone.
     */
    private static String npcMainState(JobState state) {
        return switch (state) {
            case Idling, Sleeping, TravelingToWorkstation, TravelingToHome -> "Idling";
            case Working, WaitingForWork, TravelingToWorkSite, CollectingDrops, DeliveringItems -> "Working";
            case Recharging -> "Recharging";
        };
    }

    /**
     * Maps a {@link JobState} to the NPC role substate name, or {@code null} for the default substate.
     * Bare names only (no leading dot -- the dot notation is JSON DSL only).
     */
    private static String npcSubState(JobState state) {
        return switch (state) {
            case Idling, Recharging -> null;
            case Sleeping             -> "Sleeping";
            case TravelingToWorkstation -> "TravelingToWorkstation";
            case TravelingToHome      -> "TravelingToHome";
            case Working              -> "Harvesting";
            case WaitingForWork       -> "WaitingForWork";
            case TravelingToWorkSite  -> "TravelingToWorkSite";
            case CollectingDrops      -> "CollectingDrops";
            case DeliveringItems      -> "DeliveringItems";
        };
    }
}
