package com.hytalecolonies.events;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.event.IEvent;
import com.hypixel.hytale.math.vector.Vector3i;

/** Fired when a colonist is assigned to a workstation. */
public class ColonistHiredEvent implements IEvent<Vector3i>
{
    private final UUID colonistUuid;
    private final Vector3i workstationPos;

    public ColonistHiredEvent(@Nonnull UUID colonistUuid, @Nonnull Vector3i workstationPos)
    {
        this.colonistUuid = colonistUuid;
        this.workstationPos = workstationPos;
    }

    public UUID getColonistUuid()
    {
        return colonistUuid;
    }

    public Vector3i getWorkstationPos()
    {
        return workstationPos;
    }

    @Nonnull
    @Override
    public String toString()
    {
        return "ColonistHiredEvent{colonist=" + colonistUuid + ", pos=" + workstationPos + "}";
    }
}
