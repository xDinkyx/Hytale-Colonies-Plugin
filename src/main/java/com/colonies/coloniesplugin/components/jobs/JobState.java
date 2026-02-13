package com.colonies.coloniesplugin.components.jobs;

import com.hypixel.hytale.codec.codecs.EnumCodec;

public enum JobState {
    Idle,
    Working,
    Recharging, // This is different depending on the creature's energy system. For example a human needs to eat, but a robot needs to recharge.
    TravelingToJob,
    TravelingHome,
    Sleeping;

    public static final EnumCodec<JobState> CODEC = new EnumCodec<>(JobState.class);
}
