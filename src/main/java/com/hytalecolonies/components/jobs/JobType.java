package com.hytalecolonies.components.jobs;

import com.hypixel.hytale.codec.codecs.EnumCodec;

public enum JobType {
    Woodsman,
    Miner,
    Farmer,
    Constructor; // ToDo: Split into separate specialist roles later. Constructor is a jack-of-all-trades for now.

    public static final EnumCodec<JobType> CODEC = new EnumCodec<>(JobType.class);
}
