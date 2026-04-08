package com.hytalecolonies.components.jobs;

import com.hypixel.hytale.codec.codecs.EnumCodec;

public enum JobState {
    Idle,
    Sleeping, // Idle substate
    Working,
    WaitingForWork, // Work substate
    CollectingDrops, // Work substate
    TravelingToWorkSite, // Work substate. e.g. miner going to the next block, or woodsman going to the next trunk.
    DeliveringItems, // Work substate
    TravelingToWorkstation, // Idle substate. Going from home to workstation.
    Recharging, // Reserved: creature energy system (humans eat, robots recharge). Not yet implemented.
    TravelingToHome; // Idle substate. Going from workstation to home.

    public static final EnumCodec<JobState> CODEC = new EnumCodec<>(JobState.class);
}
