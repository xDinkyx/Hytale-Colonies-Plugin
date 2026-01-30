package com.colonies.coloniesplugin;

import com.colonies.coloniesplugin.components.ColonistComponent;
import com.colonies.coloniesplugin.interactions.SpawnColonistInteraction;
import com.colonies.coloniesplugin.systems.ColonySystem;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.interactions.SpawnNPCInteraction;

import javax.annotation.Nonnull;

public class ColoniesPlugin extends JavaPlugin {
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static ColoniesPlugin instance;

    private ComponentType<EntityStore, ColonistComponent> colonistComponent;

    public ColoniesPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from %s version %s", this.getName(), this.getManifest().getVersion().toString());
        instance = this;
    }

    public static ColoniesPlugin getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        // Commands
        this.getCommandRegistry().registerCommand(new ColoniesCommand(this.getName(), this.getManifest().getVersion().toString()));

        // Components
        this.colonistComponent = this.getEntityStoreRegistry().registerComponent(ColonistComponent.class, ColonistComponent::new);

        // Interactions
        Interaction.CODEC.register("SpawnColonist", SpawnColonistInteraction.class, SpawnColonistInteraction.CODEC);

        // Systems
        this.getEntityStoreRegistry().registerSystem(new ColonySystem(this.colonistComponent));
    }

    public ComponentType<EntityStore, ColonistComponent> getColonistComponent() {
        return colonistComponent;
    }
}
