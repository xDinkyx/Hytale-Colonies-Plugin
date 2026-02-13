package com.colonies.coloniesplugin.components.npc;

// Imports
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nullable;

/**
 * Component for colonist NPCs.
 */
public class ColonistComponent implements Component<EntityStore> {

    // ===== Codec =====
    public static final BuilderCodec<ColonistComponent> CODEC = BuilderCodec.builder(ColonistComponent.class, ColonistComponent::new)
            .append(new KeyedCodec<>("ColonyId", Codec.STRING),
                    (c, v) -> c.colonyId = v,
                    c -> c.colonyId)
            .add()
            .append(new KeyedCodec<>("ColonistName", Codec.STRING),
                    (c, v) -> c.colonistName = v,
                    c -> c.colonistName)
            .add()
            .append(new KeyedCodec<>("ColonistLevel", Codec.INTEGER),
                    (c, v) -> c.colonistLevel = v,
                    c -> c.colonistLevel)
            .add()
            .build();

    // ===== Fields =====
    private String colonyId;
    private String colonistName;
    private int colonistLevel;

    // ===== Constructors =====
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

    // ===== Component Clone =====
    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new ColonistComponent(this);
    }

    // ===== Getters and Setters =====
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
}
