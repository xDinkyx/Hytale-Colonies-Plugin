package com.hytalecolonies.components.jobs;

import javax.annotation.Nullable;

import com.hypixel.hytale.codec.codecs.EnumCodec;

/** Colonist job state machine states. Each state belongs to a {@link Group} (NPC role main-state). */
public enum JobState {

    // Idle group
    Idle(Group.Idle, null),
    Sleeping(Group.Idle, "Sleeping"),              // reserved
    TravelingToWorkstation(Group.Idle, "TravelingToWorkstation"),
    TravelingToHome(Group.Idle, "TravelingToHome"),

    // Working group
    Working(Group.Working, "Harvesting"),
    WaitingForWork(Group.Working, "WaitingForWork"),
    TravelingToWorkSite(Group.Working, "TravelingToWorkSite"),
    CollectingDrops(Group.Working, "CollectingDrops"),
    DeliveringItems(Group.Working, "DeliveringItems"),

    // Recharging group
    Recharging(Group.Recharging, null);             // reserved

    /** Maps 1-to-1 with the NPC role main-state name. */
    public enum Group {
        Idle,
        Working,
        Recharging
    }

    public final Group group;
    /** NPC role sub-state name, or {@code null} to use the group name. */
    @Nullable public final String npcSubState;

    JobState(Group group, @Nullable String npcSubState) {
        this.group = group;
        this.npcSubState = npcSubState;
    }

    public String npcMainState() { return group.name(); }

    public static final EnumCodec<JobState> CODEC = new EnumCodec<>(JobState.class);
}
