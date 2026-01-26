package com.colonies.coloniesplugin.components;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nullable;

public class ColonistComponent implements Component<EntityStore> {
    private String colonyId;
    private String colonistName;
    private int colonistLevel;

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
}
