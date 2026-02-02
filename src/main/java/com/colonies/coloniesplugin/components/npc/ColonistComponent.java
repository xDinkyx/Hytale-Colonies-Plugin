package com.colonies.coloniesplugin.components.npc;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nullable;
import java.util.UUID;

public class ColonistComponent implements Component<EntityStore> {

    private String colonyId;
    private String colonistName;
    private int colonistLevel;

    private UUID jobProviderEntityId = null; // Stores the JobProvider entity ID assigned to this colonist (i.e., the job they are working at).

    public ColonistComponent() {
        this.colonyId = "default_colony";
        this.colonistName = "Unnamed Colonist";
        this.colonistLevel = 1;
    }

    public ColonistComponent(String colonyId, String name, int level) {
        this.colonyId = colonyId;
        this.colonistName = name;
        this.colonistLevel = level;
    }

    public ColonistComponent(ColonistComponent other) {
        this.colonyId = other.colonyId;
        this.colonistName = other.colonistName;
        this.colonistLevel = other.colonistLevel;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new ColonistComponent(this);
    }

    public String getColonyId() {
        return colonyId;
    }

    public void setColonyId(String colonyId) {
        this.colonyId = colonyId;
    }

    public String getColonistName() {
        return colonistName;
    }

    public void setColonistName(String colonistName) {
        this.colonistName = colonistName;
    }

    public int getColonistLevel() {
        return colonistLevel;
    }

    public void setColonistLevel(int colonistLevel) {
        this.colonistLevel = colonistLevel;
    }

    public boolean isEmployed() {
        return jobProviderEntityId != null;
    }
}
