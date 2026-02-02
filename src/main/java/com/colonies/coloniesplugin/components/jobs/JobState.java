package com.colonies.coloniesplugin.components.jobs;

public enum JobState {
    IDLE,
    WORKING,
    RECHARGING, // This is different depending on the creature's energy system. For example a human needs to eat, but a robot needs to recharge.
    TRAVELING_TO_JOB,
    TRAVELING_HOME,
    SLEEPING
}
