package com.hytalecolonies.components.jobs;

import com.hypixel.hytale.codec.codecs.EnumCodec;

public enum JobState {
    Idling,
    Working,
    CollectingDrops,
    DeliveringItems,
    Recharging, // Reserved: creature energy system (humans eat, robots recharge). Not yet implemented.
    TravelingToJob,
    TravelingHome;

    public static final EnumCodec<JobState> CODEC = new EnumCodec<>(JobState.class);
}
