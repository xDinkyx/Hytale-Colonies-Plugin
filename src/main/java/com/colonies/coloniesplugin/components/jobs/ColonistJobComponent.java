package com.colonies.coloniesplugin.components.jobs;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

public class ColonistJobComponent implements Component<EntityStore> {

    public UUID jobProviderEntityId; // Pointer to the JobProvider entity
    public String currentTask = "IDLE"; // TRAVELING, WORKING, REFUELING // ToDo: Make this an enum?

    public ColonistJobComponent() {}

    public ColonistJobComponent(UUID jobProviderEntityId) {
        this.jobProviderEntityId = jobProviderEntityId;
    }

    @Override
    public @Nullable Component<EntityStore> clone() {
        ColonistJobComponent copy = new ColonistJobComponent(this.jobProviderEntityId);
        copy.currentTask = this.currentTask;
        return copy;
    }
}
