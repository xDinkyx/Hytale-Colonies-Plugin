package com.hytalecolonies.events;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.math.vector.Vector3i;

/** Fired when a colonist is removed from a workstation (fired/unassigned). */
public class ColonistFiredEvent implements IEvent<Vector3i> {

    private final UUID colonistUuid;
    private final Vector3i workstationPos;

    public ColonistFiredEvent(@Nonnull UUID colonistUuid, @Nonnull Vector3i workstationPos) {
        this.colonistUuid = colonistUuid;
        this.workstationPos = workstationPos;
    }

    public UUID getColonistUuid() {
        return colonistUuid;
    }

    public Vector3i getWorkstationPos() {
        return workstationPos;
    }

    @Nonnull
    @Override
    public String toString() {
        return "ColonistFiredEvent{colonist=" + colonistUuid + ", pos=" + workstationPos + "}";
    }
}
