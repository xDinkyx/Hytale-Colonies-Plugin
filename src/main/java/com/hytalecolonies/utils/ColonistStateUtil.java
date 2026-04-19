package com.hytalecolonies.utils;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;
import com.hytalecolonies.components.jobs.ConstructorJobComponent;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;


/** Writes {@link JobState} to {@link JobComponent} and mirrors it into the NPC role state machine. */
public final class ColonistStateUtil
{
    /** Capability token -- package-private so only this package can call {@link JobComponent#setCurrentTask}. */
    public static final class Key
    {
        private Key() {}
    }

    static final Key INSTANCE = new Key();

    private ColonistStateUtil()
    {
    }

    /** Sets the job state on {@code job} and mirrors it into the NPC role state machine. Mirror is skipped if the NPC has no role yet. */
    public static void setJobState(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull JobComponent job, @Nonnull JobState state)
    {
        JobState previousState = job.getCurrentTask();

        // When leaving WorkingConstructing, release any remaining pre-claimed build blocks.
        if (previousState == JobState.WorkingConstructing && state != JobState.WorkingConstructing)
        {
            ConstructorJobComponent constructorJob = store.getComponent(ref, ConstructorJobComponent.getComponentType());
            if (constructorJob != null && !constructorJob.pendingBuildQueue.isEmpty())
            {
                List<Vector3i> toUnclaim = new ArrayList<>(constructorJob.pendingBuildQueue);
                constructorJob.pendingBuildQueue.clear();
                World world = store.getExternalData().getWorld();
                world.execute(() -> {
                    for (Vector3i pos : toUnclaim)
                        ClaimBlockUtil.unclaimBlock(world, pos);
                });
            }
        }

        job.setCurrentTask(INSTANCE, state);

        DebugLog.fine(DebugCategory.JOB_SYSTEM,
                "[StateTransition] [%s] %s -> %s.", DebugLog.npcId(ref, store), previousState, state);

        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null)
            return;

        Role role = npc.getRole();
        if (role == null)
            return;

        role.getStateSupport().setState(ref, state.npcMainState(), state.npcSubState, store);
    }
}
