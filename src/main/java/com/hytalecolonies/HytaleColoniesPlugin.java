package com.hytalecolonies;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;

import com.hytalecolonies.commands.HytaleColoniesPluginCommand;
import com.hytalecolonies.listeners.PlayerListener;
import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.components.world.HarvestableTreeComponent;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.UnemployedComponent;
import com.hytalecolonies.components.jobs.WoodcutterJobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.interactions.SpawnColonistInteraction;
import com.hytalecolonies.systems.ColonySystem;
import com.hytalecolonies.systems.jobs.JobAssignmentSystems;
import com.hytalecolonies.systems.jobs.TreeBlockChangeEventSystem;
import com.hytalecolonies.systems.jobs.TreeScannerSystem;
import com.hytalecolonies.systems.jobs.WorkstationTreeInitSystem;
import com.hytalecolonies.systems.jobs.WoodcutterMovementSystem;
import com.hytalecolonies.systems.npc.PathFindingSystem;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * HytaleColonies - A Hytale server plugin.
 */
public class HytaleColoniesPlugin extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static HytaleColoniesPlugin instance;

    // ECS Component Types
    private ComponentType<EntityStore, ColonistComponent> colonistComponentType;
    private ComponentType<EntityStore, JobComponent> colonistJobComponentType;
    private ComponentType<EntityStore, UnemployedComponent> unemployedComponentType;
    private ComponentType<EntityStore, WoodcutterJobComponent> woodCutterJobComponentType;
    private ComponentType<ChunkStore, WorkStationComponent> workStationComponentType;
    private ComponentType<EntityStore, MoveToTargetComponent> moveToTargetComponentType;
    private ComponentType<ChunkStore, HarvestableTreeComponent> harvestableTreeComponentType;

    public HytaleColoniesPlugin(@Nonnull JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from %s version %s", this.getName(), this.getManifest().getVersion().toString());
        instance = this;
    }

    /**
     * Get the plugin instance.
     * @return The plugin instance
     */
    public static HytaleColoniesPlugin getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("[HytaleColonies] Setting up...");

        registerCommands();
        registerListeners();
        registerComponents();
        registerInteractions();
        registerSystems();

        LOGGER.at(Level.INFO).log("[HytaleColonies] Setup complete!");
    }

    /**
     * Register plugin commands.
     */
    private void registerCommands() {
        try {
            getCommandRegistry().registerCommand(new HytaleColoniesPluginCommand(this.getName(), this.getManifest().getVersion().toString()));
            LOGGER.at(Level.INFO).log("[HytaleColonies] Registered plugin commands");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[HytaleColonies] Failed to register commands");
        }
    }

    /**
     * Register ECS components.
     */
    private void registerComponents() {
        colonistComponentType = getEntityStoreRegistry().registerComponent(ColonistComponent.class, ColonistComponent::new);
        colonistJobComponentType = getEntityStoreRegistry().registerComponent(JobComponent.class, JobComponent::new);
        unemployedComponentType = getEntityStoreRegistry().registerComponent(UnemployedComponent.class, UnemployedComponent::new);
        woodCutterJobComponentType = getEntityStoreRegistry().registerComponent(WoodcutterJobComponent.class, WoodcutterJobComponent::new);
        workStationComponentType = getChunkStoreRegistry().registerComponent(WorkStationComponent.class, "WorkStation", WorkStationComponent.CODEC);
        moveToTargetComponentType = getEntityStoreRegistry().registerComponent(MoveToTargetComponent.class, MoveToTargetComponent::new);
        harvestableTreeComponentType = getChunkStoreRegistry().registerComponent(HarvestableTreeComponent.class, "HarvestableTree", HarvestableTreeComponent.CODEC);
        LOGGER.at(Level.INFO).log("[HytaleColonies] Registered ECS components");
    }

    // Accessors for ECS component types
    public ComponentType<EntityStore, ColonistComponent> getColonistComponentType() {
        return colonistComponentType;
    }
    public ComponentType<EntityStore, UnemployedComponent> getUnemployedComponentType() {
        return unemployedComponentType;
    }
    public ComponentType<ChunkStore, WorkStationComponent> getWorkStationComponentType() {
        return workStationComponentType;
    }
    public ComponentType<EntityStore, JobComponent> getJobComponentType() {
        return colonistJobComponentType;
    }
    public ComponentType<EntityStore, WoodcutterJobComponent> getWoodCutterJobComponentType() {
        return woodCutterJobComponentType;
    }
    public ComponentType<EntityStore, MoveToTargetComponent> getMoveToTargetComponentType() {
        return moveToTargetComponentType;
    }
    public ComponentType<ChunkStore, HarvestableTreeComponent> getHarvestableTreeComponentType() {
        return harvestableTreeComponentType;
    }

    /**
     * Register plugin interactions.
     */
    private void registerInteractions() {
        Interaction.CODEC.register(
            "SpawnColonist",
            SpawnColonistInteraction.class,
            SpawnColonistInteraction.CODEC
        );
        LOGGER.at(Level.INFO).log("[HytaleColonies] Registered plugin interactions");
    }

    /**
     * Register plugin systems.
     */
    private void registerSystems() {
        TreeScannerSystem treeScannerSystem = new TreeScannerSystem();

        getEntityStoreRegistry().registerSystem(new ColonySystem(colonistComponentType));
        getChunkStoreRegistry().registerSystem(new JobAssignmentSystems());
        getChunkStoreRegistry().registerSystem(new JobAssignmentSystems.WorkStationEntitySystem());
        getChunkStoreRegistry().registerSystem(treeScannerSystem);
        getChunkStoreRegistry().registerSystem(new WorkstationTreeInitSystem(treeScannerSystem));
        getEntityStoreRegistry().registerSystem(new JobAssignmentSystems.ColonistEntitySystem());
        getEntityStoreRegistry().registerSystem(new JobAssignmentSystems.JobAssignedSystem());
        getEntityStoreRegistry().registerSystem(new JobAssignmentSystems.UnemployedAssignedSystem());
        getEntityStoreRegistry().registerSystem(new PathFindingSystem());
        getEntityStoreRegistry().registerSystem(new WoodcutterMovementSystem());
        getEntityStoreRegistry().registerSystem(new TreeBlockChangeEventSystem.OnBreak(treeScannerSystem));
        getEntityStoreRegistry().registerSystem(new TreeBlockChangeEventSystem.OnPlace(treeScannerSystem));
        LOGGER.at(Level.INFO).log("[HytaleColonies] Registered plugin systems");
    }

    /**
     * Register event listeners.
     */
    private void registerListeners() {
        EventRegistry eventBus = getEventRegistry();

        try {
            new PlayerListener().register(eventBus);
            LOGGER.at(Level.INFO).log("[HytaleColonies] Registered player event listeners");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[HytaleColonies] Failed to register listeners");
        }
    }

    @Override
    protected void start() {
        LOGGER.at(Level.INFO).log("[HytaleColonies] Started!");
        LOGGER.at(Level.INFO).log("[HytaleColonies] Use /hc help for commands");
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("[HytaleColonies] Shutting down...");
        instance = null;
    }
}