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
import com.hytalecolonies.debug.DebugConfig;
import com.hytalecolonies.listeners.PlayerListener;
import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.components.world.HarvestableTreeComponent;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.components.jobs.UnemployedComponent;
import com.hytalecolonies.components.jobs.WoodsmanJobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hytalecolonies.interactions.SpawnColonistInteraction;
import com.hytalecolonies.systems.ColonySystem;
import com.hytalecolonies.systems.jobs.JobAssignmentSystems;
import com.hytalecolonies.systems.jobs.WorkstationInitSystem;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.systems.jobs.ColonistDeliverySystem;
import com.hytalecolonies.systems.jobs.ColonistItemPickupSystem;
import com.hytalecolonies.systems.jobs.ColonistMovementSystem;
import com.hytalecolonies.npc.actions.BuilderActionEquipBestTool;
import com.hytalecolonies.npc.actions.BuilderActionHarvestBlock;
import com.hytalecolonies.npc.sensors.BuilderSensorHarvestableTree;
import com.hytalecolonies.npc.sensors.BuilderSensorJobTarget;
import com.hytalecolonies.systems.jobs.MinerJobSystem;
import com.hytalecolonies.systems.jobs.WoodsmanJobSystem;
import com.hytalecolonies.systems.npc.PathFindingSystem;
import com.hytalecolonies.systems.treescan.TreeBlockChangeEventSystem;
import com.hytalecolonies.systems.treescan.TreeScannerSystem;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import com.hypixel.hytale.server.core.util.Config;

/**
 * HytaleColonies - A Hytale server plugin.
 */
public class HytaleColoniesPlugin extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static HytaleColoniesPlugin instance;

    private final Config<DebugConfig> debugConfig = this.withConfig("DebugConfig", DebugConfig.CODEC);

    // ECS Component Types
    private ComponentType<EntityStore, ColonistComponent> colonistComponentType;
    private ComponentType<EntityStore, JobComponent> colonistJobComponentType;
    private ComponentType<EntityStore, UnemployedComponent> unemployedComponentType;
    private ComponentType<EntityStore, WoodsmanJobComponent> woodsmanJobComponentType;
    private ComponentType<EntityStore, MinerJobComponent> minerJobComponentType;
    private ComponentType<ChunkStore, WorkStationComponent> workStationComponentType;
    private ComponentType<EntityStore, MoveToTargetComponent> moveToTargetComponentType;
    private ComponentType<ChunkStore, HarvestableTreeComponent> harvestableTreeComponentType;
    private ComponentType<EntityStore, JobTargetComponent> jobTargetComponentType;

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

    public Config<DebugConfig> getDebugConfig() {
        return debugConfig;
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("[HytaleColonies] Setting up...");

        // Allow all levels through the underlying logger so DebugCategory gates control filtering.
        LOGGER.setLevel(Level.ALL);

        debugConfig.save();
        debugConfig.get().applyToCategories();

        registerCommands();
        registerListeners();
        registerComponents();
        registerInteractions();
        registerNpcComponentTypes();
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
        colonistComponentType = getEntityStoreRegistry().registerComponent(ColonistComponent.class, "Colonist", ColonistComponent.CODEC);
        colonistJobComponentType = getEntityStoreRegistry().registerComponent(JobComponent.class, "ColonistJob", JobComponent.CODEC);
        unemployedComponentType = getEntityStoreRegistry().registerComponent(UnemployedComponent.class, "Unemployed", UnemployedComponent.CODEC);
        woodsmanJobComponentType = getEntityStoreRegistry().registerComponent(WoodsmanJobComponent.class, "WoodsmanJob", WoodsmanJobComponent.CODEC);
        minerJobComponentType = getEntityStoreRegistry().registerComponent(MinerJobComponent.class, "MinerJob", MinerJobComponent.CODEC);
        workStationComponentType = getChunkStoreRegistry().registerComponent(WorkStationComponent.class, "WorkStation", WorkStationComponent.CODEC);
        moveToTargetComponentType = getEntityStoreRegistry().registerComponent(MoveToTargetComponent.class, MoveToTargetComponent::new);
        harvestableTreeComponentType = getChunkStoreRegistry().registerComponent(HarvestableTreeComponent.class, "HarvestableTree", HarvestableTreeComponent.CODEC);
        jobTargetComponentType = getEntityStoreRegistry().registerComponent(JobTargetComponent.class, "JobTarget", JobTargetComponent.CODEC);
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
    public ComponentType<EntityStore, WoodsmanJobComponent> getWoodsmanJobComponentType() {
        return woodsmanJobComponentType;
    }
    public ComponentType<EntityStore, MinerJobComponent> getMinerJobComponentType() {
        return minerJobComponentType;
    }
    public ComponentType<EntityStore, MoveToTargetComponent> getMoveToTargetComponentType() {
        return moveToTargetComponentType;
    }
    public ComponentType<ChunkStore, HarvestableTreeComponent> getHarvestableTreeComponentType() {
        return harvestableTreeComponentType;
    }
    public ComponentType<EntityStore, JobTargetComponent> getJobTargetComponentType() {
        return jobTargetComponentType;
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
     * Register custom NPC component types (sensors, actions, filters) into the NPC system.
     * These become available as JSON types in NPC role templates.
     */
    private void registerNpcComponentTypes() {
        NPCPlugin.get()
            .registerCoreComponentType("EquipBestTool",      BuilderActionEquipBestTool::new)
            .registerCoreComponentType("HarvestableTree",    BuilderSensorHarvestableTree::new)
            .registerCoreComponentType("HarvestBlock",       BuilderActionHarvestBlock::new)
            .registerCoreComponentType("JobTarget",          BuilderSensorJobTarget::new);
        LOGGER.at(Level.INFO).log("[HytaleColonies] Registered NPC component types");
    }

    /**
     * Register plugin systems.
     */
    private void registerSystems() {
        TreeScannerSystem treeScannerSystem = new TreeScannerSystem();

        getEntityStoreRegistry().registerSystem(new ColonySystem(colonistComponentType));
        getChunkStoreRegistry().registerSystem(new JobAssignmentSystems());
        getChunkStoreRegistry().registerSystem(new JobAssignmentSystems.WorkStationEntitySystem());
        getChunkStoreRegistry().registerSystem(new JobAssignmentSystems.StaleMarkCleanupSystem());
        getChunkStoreRegistry().registerSystem(treeScannerSystem);
        getChunkStoreRegistry().registerSystem(new WorkstationInitSystem(treeScannerSystem));
        getEntityStoreRegistry().registerSystem(new JobAssignmentSystems.ColonistEntitySystem());
        getEntityStoreRegistry().registerSystem(new JobAssignmentSystems.JobAssignedSystem());
        getEntityStoreRegistry().registerSystem(new JobAssignmentSystems.UnemployedAssignedSystem());
        getEntityStoreRegistry().registerSystem(new PathFindingSystem());
        getEntityStoreRegistry().registerSystem(new ColonistMovementSystem());
        getEntityStoreRegistry().registerSystem(new ColonistItemPickupSystem());
        getEntityStoreRegistry().registerSystem(new ColonistDeliverySystem());
        getEntityStoreRegistry().registerSystem(new WoodsmanJobSystem());
        getEntityStoreRegistry().registerSystem(new MinerJobSystem());
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