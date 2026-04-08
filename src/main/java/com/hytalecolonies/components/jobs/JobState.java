package com.hytalecolonies.components.jobs;

import com.hypixel.hytale.codec.codecs.EnumCodec;

public enum JobState {
    Idling,
    Sleeping, // Idling substate
    Working,
    WaitingForWork, // Work substate
    CollectingDrops, // Work substate
    TravelingToWorkSite, // Work substate. e.g. miner going to the next block, or woodsman going to the next trunk.
    DeliveringItems, // Work substate
    TravelingToWorkstation, // Idling substate. Going from home to workstation.
    Recharging, // Reserved: creature energy system (humans eat, robots recharge). Not yet implemented.
    TravelingToHome; // Idling substate. Going from workstation to home.

    public static final EnumCodec<JobState> CODEC = new EnumCodec<>(JobState.class);
}
