package com.colonies.coloniesplugin;

import com.colonies.coloniesplugin.commands.debug.BlockEntityInfoCommand;
import com.colonies.coloniesplugin.components.jobs.JobComponent;
import com.colonies.coloniesplugin.components.jobs.WorkStationComponent;
import com.colonies.coloniesplugin.components.jobs.WoodcutterJobComponent;
import com.colonies.coloniesplugin.components.npc.ColonistComponent;
import com.colonies.coloniesplugin.interactions.SpawnColonistInteraction;
import com.colonies.coloniesplugin.systems.ColonySystem;
import com.colonies.coloniesplugin.systems.jobs.JobAssignmentSystem;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class ColoniesPlugin extends JavaPlugin {
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static ColoniesPlugin instance;

    private ComponentType<EntityStore, ColonistComponent> colonistComponentType;
    private ComponentType<ChunkStore, WorkStationComponent> workStationComponentType;
    private ComponentType<EntityStore, JobComponent> colonistJobComponentType;
    private ComponentType<EntityStore, WoodcutterJobComponent> woodCutterJobComponentType;

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
        this.getCommandRegistry().registerCommand(new BlockEntityInfoCommand());

        // Components
        this.colonistComponentType = this.getEntityStoreRegistry().registerComponent(ColonistComponent.class, ColonistComponent::new);
        this.colonistJobComponentType = this.getEntityStoreRegistry().registerComponent(JobComponent.class, JobComponent::new);
        this.woodCutterJobComponentType = this.getEntityStoreRegistry().registerComponent(WoodcutterJobComponent.class, WoodcutterJobComponent::new);
        this.workStationComponentType = this.getChunkStoreRegistry().registerComponent(WorkStationComponent.class, "WorkStation", WorkStationComponent.CODEC);

        // Interactions
        Interaction.CODEC.register("SpawnColonist", SpawnColonistInteraction.class, SpawnColonistInteraction.CODEC);

        // Systems
        this.getEntityStoreRegistry().registerSystem(new ColonySystem(this.colonistComponentType));
        this.getChunkStoreRegistry().registerSystem(new JobAssignmentSystem());
    }

    public ComponentType<EntityStore, ColonistComponent> getColonistComponentType() {
        return colonistComponentType;
    }

    public ComponentType<ChunkStore, WorkStationComponent> getWorkStationComponentType() {
        return workStationComponentType;
    }

    public ComponentType<EntityStore, JobComponent> getColonistJobComponentType() {
        return colonistJobComponentType;
    }

    public ComponentType<EntityStore, WoodcutterJobComponent> getWoodCutterJobComponentType() {
        return woodCutterJobComponentType;
    }
}

