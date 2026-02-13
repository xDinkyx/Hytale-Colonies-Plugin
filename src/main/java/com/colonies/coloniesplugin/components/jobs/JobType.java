package com.colonies.coloniesplugin.components.jobs;

import com.hypixel.hytale.codec.codecs.EnumCodec;

public enum JobType {
    Woodsman,
    Miner,
    Farmer,
    Builder; // ToDo: Would be cool if structures need to be built by different types of builders (e.g. Carpenter for wooden structures, Mason for stone structures, etc.)

    public static final EnumCodec<JobType> CODEC = new EnumCodec<>(JobType.class);
}
