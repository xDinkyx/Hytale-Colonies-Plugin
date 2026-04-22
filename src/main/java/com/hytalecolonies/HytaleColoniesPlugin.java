package com.hytalecolonies;

import java.util.logging.Level;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hytalecolonies.commands.HytaleColoniesPluginCommand;
import com.hytalecolonies.components.jobs.ConstructorJobComponent;
import com.hytalecolonies.components.jobs.ConstructorWorkStationComponent;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobRunCounterComponent;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.components.jobs.MinerWorkStationComponent;
import com.hytalecolonies.components.jobs.UnemployedComponent;
import com.hytalecolonies.components.jobs.WoodsmanJobComponent;
import com.hytalecolonies.components.jobs.WoodsmanWorkStationComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hytalecolonies.components.npc.ColonistComponent;
import com.hytalecolonies.components.npc.MoveToTargetComponent;
import com.hytalecolonies.components.world.ClaimedBlockComponent;
import com.hytalecolonies.components.world.HarvestableTreeComponent;
import com.hytalecolonies.debug.DebugConfig;
import com.hytalecolonies.interactions.SpawnColonistInteraction;
import com.hytalecolonies.listeners.ConstructorBuildOrderFilter;
import com.hytalecolonies.listeners.ConstructorPrefabPageFilter;
import com.hytalecolonies.listeners.PlayerListener;
import com.hytalecolonies.npc.actions.common.BuilderActionDepositItems;
import com.hytalecolonies.npc.actions.common.BuilderActionEquipBestTool;
import com.hytalecolonies.npc.actions.common.BuilderActionFindDeliveryContainer;
import com.hytalecolonies.npc.actions.common.BuilderActionHarvestBlock;
import com.hytalecolonies.npc.actions.common.BuilderActionIncrementJobCounter;
import com.hytalecolonies.npc.actions.common.BuilderActionLogDebug;
import com.hytalecolonies.npc.actions.common.BuilderActionNavigateToWorkstation;
import com.hytalecolonies.npc.actions.common.BuilderActionNotifyBlockBroken;
import com.hytalecolonies.npc.actions.common.BuilderActionReleaseJobTarget;
import com.hytalecolonies.npc.actions.common.BuilderActionResetJobCounter;
import com.hytalecolonies.npc.actions.common.BuilderActionSetEcsJobState;
import com.hytalecolonies.npc.actions.constructor.BuilderActionPlaceConstructionBlock;
import com.hytalecolonies.npc.actions.constructor.BuilderActionRetrieveConstructionBlocks;
import com.hytalecolonies.npc.actions.constructor.BuilderActionSeekNextClearingBlock;
import com.hytalecolonies.npc.actions.miner.BuilderActionSeekNextMineBlock;
import com.hytalecolonies.npc.actions.woodsman.BuilderActionFindNextTrunkBlock;
import com.hytalecolonies.npc.actions.woodsman.BuilderActionSeekNearestTree;
import com.hytalecolonies.npc.sensors.common.BuilderSensorAtWorkstation;
import com.hytalecolonies.npc.sensors.common.BuilderSensorCollectionTimerElapsed;
import com.hytalecolonies.npc.sensors.common.BuilderSensorJobTarget;
import com.hytalecolonies.npc.sensors.common.BuilderSensorJobTargetBroken;
import com.hytalecolonies.npc.sensors.common.BuilderSensorJobTargetExists;
import com.hytalecolonies.npc.sensors.common.BuilderSensorNoWorkAvailable;
import com.hytalecolonies.npc.sensors.common.BuilderSensorRunQuotaReached;
import com.hytalecolonies.npc.sensors.woodsman.BuilderSensorHarvestableTree;
import com.hytalecolonies.systems.ColonySystem;
import com.hytalecolonies.systems.jobs.ClaimedBlockCleanupSystem;
import com.hytalecolonies.systems.jobs.ColonistCleanupSystem;
import com.hytalecolonies.systems.jobs.ColonistDeliverySystem;
import com.hytalecolonies.systems.jobs.ColonistItemPickupSystem;
import com.hytalecolonies.systems.jobs.ColonistJobSystem;
import com.hytalecolonies.systems.jobs.ConstructionOrderDispatchSystem;
import com.hytalecolonies.systems.jobs.ConstructorJobCheckSystem;
import com.hytalecolonies.systems.jobs.ConstructorWorkingSystem;
import com.hytalecolonies.systems.jobs.JobAssignmentSystems;
import com.hytalecolonies.systems.jobs.JobRegistry;
import com.hytalecolonies.systems.jobs.MinerWorkingSystem;
import com.hytalecolonies.systems.jobs.WoodsmanWorkingSystem;
import com.hytalecolonies.systems.jobs.WorkstationInitSystem;
import com.hytalecolonies.systems.npc.ColonistRemovalSystem;
import com.hytalecolonies.systems.npc.PathFindingSystem;
import com.hytalecolonies.systems.world.TreeBlockChangeEventSystem;
import com.hytalecolonies.systems.world.TreeScannerSystem;

/**
 * HytaleColonies - A Hytale server plugin.
 */
public class HytaleColoniesPlugin extends JavaPlugin {

    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static HytaleColoniesPlugin instance;

    private final Config<DebugConfig> debugConfig = this.withConfig("DebugConfig", DebugConfig.CODEC);
    private final Config<ConstructionOrderStore.StoreData> constructionOrderConfig =
            this.withConfig("ConstructionOrders", ConstructionOrderStore.StoreData.CODEC);

    // ECS Component Types
    private ComponentType<EntityStore, ColonistComponent> colonistComponentType;
    private ComponentType<EntityStore, JobComponent> colonistJobComponentType;
    private ComponentType<EntityStore, UnemployedComponent> unemployedComponentType;
    private ComponentType<EntityStore, WoodsmanJobComponent> woodsmanJobComponentType;
    private ComponentType<EntityStore, MinerJobComponent> minerJobComponentType;
    private ComponentType<EntityStore, JobRunCounterComponent> jobRunCounterComponentType;
    private ComponentType<EntityStore, ConstructorJobComponent> constructorJobComponentType;
    private ComponentType<ChunkStore, WorkStationComponent> workStationComponentType;
    private ComponentType<ChunkStore, WoodsmanWorkStationComponent> woodsmanWorkStationComponentType;
    private ComponentType<ChunkStore, MinerWorkStationComponent> minerWorkStationComponentType;
    private ComponentType<ChunkStore, ConstructorWorkStationComponent> constructorWorkStationComponentType;
    private ComponentType<EntityStore, MoveToTargetComponent> moveToTargetComponentType;
    private ComponentType<ChunkStore, HarvestableTreeComponent> harvestableTreeComponentType;
    private ComponentType<EntityStore, JobTargetComponent> jobTargetComponentType;
    private ComponentType<ChunkStore, ClaimedBlockComponent> claimedBlockComponentType;

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

        constructionOrderConfig.save();
        ConstructionOrderStore.get().init(constructionOrderConfig);
        for (ConstructionOrderStore.Entry e : ConstructionOrderStore.get().all()) {
            if (ConstructionOrderStore.STATUS_IN_PROGRESS.equals(e.status)) {
                // InProgress orders are already assigned to a persisted workstation component --
                // ConstructorJobCheckSystem will resume them once the chunk loads.
                continue;
            }
            e.status = ConstructionOrderStore.STATUS_PENDING;
            ConstructionOrderQueue.get().enqueue(e.id);
        }

        registerCommands();
        registerListeners();
        registerComponents();
        registerSharedJobHandlers();
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
        jobRunCounterComponentType = getEntityStoreRegistry().registerComponent(JobRunCounterComponent.class, "JobRunCounter", JobRunCounterComponent.CODEC);
        constructorJobComponentType = getEntityStoreRegistry().registerComponent(ConstructorJobComponent.class, "ConstructorJob", ConstructorJobComponent.CODEC);
        workStationComponentType = getChunkStoreRegistry().registerComponent(WorkStationComponent.class, "WorkStation", WorkStationComponent.CODEC);
        woodsmanWorkStationComponentType = getChunkStoreRegistry().registerComponent(WoodsmanWorkStationComponent.class, "WoodsmanWorkStation", WoodsmanWorkStationComponent.CODEC);
        minerWorkStationComponentType = getChunkStoreRegistry().registerComponent(MinerWorkStationComponent.class, "MinerWorkStation", MinerWorkStationComponent.CODEC);
        constructorWorkStationComponentType = getChunkStoreRegistry().registerComponent(ConstructorWorkStationComponent.class, "ConstructorWorkStation", ConstructorWorkStationComponent.CODEC);
        moveToTargetComponentType = getEntityStoreRegistry().registerComponent(MoveToTargetComponent.class, MoveToTargetComponent::new);
        harvestableTreeComponentType = getChunkStoreRegistry().registerComponent(HarvestableTreeComponent.class, "HarvestableTree", HarvestableTreeComponent.CODEC);
        jobTargetComponentType = getEntityStoreRegistry().registerComponent(JobTargetComponent.class, "JobTarget", JobTargetComponent.CODEC);
        claimedBlockComponentType = getChunkStoreRegistry().registerComponent(ClaimedBlockComponent.class, "ClaimedBlock", ClaimedBlockComponent.CODEC);
        LOGGER.at(Level.INFO).log("[HytaleColonies] Registered ECS components");
    }

    /**
     * Registers job component types so {@link JobAssignmentSystems#fireColonist} can strip them.
     * All state transitions are now driven by NPC role JSON actions and sensors -- no Java handlers.
     */
    private void registerSharedJobHandlers() {
        JobRegistry.register(WoodsmanJobComponent.getComponentType());
        JobRegistry.register(MinerJobComponent.getComponentType());
        JobRegistry.register(ConstructorJobComponent.getComponentType());
        JobRegistry.register(JobRunCounterComponent.getComponentType());
        LOGGER.at(Level.INFO).log("[HytaleColonies] Registered job component types");
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
    public ComponentType<ChunkStore, WoodsmanWorkStationComponent> getWoodsmanWorkStationComponentType() {
        return woodsmanWorkStationComponentType;
    }
    public ComponentType<ChunkStore, MinerWorkStationComponent> getMinerWorkStationComponentType() {
        return minerWorkStationComponentType;
    }
    public ComponentType<ChunkStore, ConstructorWorkStationComponent> getConstructorWorkStationComponentType() {
        return constructorWorkStationComponentType;
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
    public ComponentType<EntityStore, JobRunCounterComponent> getJobRunCounterComponentType() {
        return jobRunCounterComponentType;
    }
    public ComponentType<EntityStore, ConstructorJobComponent> getConstructorJobComponentType() {
        return constructorJobComponentType;
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
    public ComponentType<ChunkStore, ClaimedBlockComponent> getClaimedBlockComponentType() {
        return claimedBlockComponentType;
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
            // Actions
            .registerCoreComponentType("EquipBestTool",              BuilderActionEquipBestTool::new)
            .registerCoreComponentType("HarvestBlock",               BuilderActionHarvestBlock::new)
            .registerCoreComponentType("SeekNearestTree",            BuilderActionSeekNearestTree::new)
            .registerCoreComponentType("SeekNextMineBlock",          BuilderActionSeekNextMineBlock::new)
            .registerCoreComponentType("FindNextTrunkBlock",         BuilderActionFindNextTrunkBlock::new)
            .registerCoreComponentType("ReleaseJobTarget",           BuilderActionReleaseJobTarget::new)
            .registerCoreComponentType("IncrementJobCounter",       BuilderActionIncrementJobCounter::new)
            .registerCoreComponentType("ResetJobCounter",           BuilderActionResetJobCounter::new)
            .registerCoreComponentType("SetEcsJobState",             BuilderActionSetEcsJobState::new)
            .registerCoreComponentType("NotifyBlockBroken",          BuilderActionNotifyBlockBroken::new)
            .registerCoreComponentType("PlaceConstructionBlock",     BuilderActionPlaceConstructionBlock::new)
            .registerCoreComponentType("RetrieveConstructionBlocks", BuilderActionRetrieveConstructionBlocks::new)
            .registerCoreComponentType("SeekNextClearingBlock",      BuilderActionSeekNextClearingBlock::new)
            .registerCoreComponentType("NavigateToWorkstation",      BuilderActionNavigateToWorkstation::new)
            .registerCoreComponentType("FindDeliveryContainer",      BuilderActionFindDeliveryContainer::new)
            .registerCoreComponentType("DepositItems",               BuilderActionDepositItems::new)
            // Sensors
            .registerCoreComponentType("HarvestableTree",            BuilderSensorHarvestableTree::new)
            .registerCoreComponentType("JobTarget",                  BuilderSensorJobTarget::new)
            .registerCoreComponentType("JobTargetExists",            BuilderSensorJobTargetExists::new)
            .registerCoreComponentType("JobTargetBroken",            BuilderSensorJobTargetBroken::new)
            .registerCoreComponentType("RunQuotaReached",           BuilderSensorRunQuotaReached::new)
            .registerCoreComponentType("NoWorkAvailable",            BuilderSensorNoWorkAvailable::new)
            .registerCoreComponentType("AtWorkstation",              BuilderSensorAtWorkstation::new)
            .registerCoreComponentType("CollectionTimerElapsed",     BuilderSensorCollectionTimerElapsed::new)
            // Debug utilities
            .registerCoreComponentType("LogDebug",                   BuilderActionLogDebug::new);
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
        getChunkStoreRegistry().registerSystem(new ClaimedBlockCleanupSystem());
        getChunkStoreRegistry().registerSystem(new ColonistCleanupSystem());
        getChunkStoreRegistry().registerSystem(treeScannerSystem);
        getChunkStoreRegistry().registerSystem(new WorkstationInitSystem(treeScannerSystem));
        getChunkStoreRegistry().registerSystem(new ConstructionOrderDispatchSystem());

        getEntityStoreRegistry().registerSystem(new JobAssignmentSystems.ColonistEntitySystem());
        getEntityStoreRegistry().registerSystem(new JobAssignmentSystems.JobAssignedSystem());
        getEntityStoreRegistry().registerSystem(new JobAssignmentSystems.UnemployedAssignedSystem());
        getEntityStoreRegistry().registerSystem(new PathFindingSystem());
        getEntityStoreRegistry().registerSystem(new ColonistJobSystem());
        getEntityStoreRegistry().registerSystem(new ColonistRemovalSystem());
        getEntityStoreRegistry().registerSystem(new MinerWorkingSystem());
        getEntityStoreRegistry().registerSystem(new WoodsmanWorkingSystem());
        getEntityStoreRegistry().registerSystem(new ConstructorWorkingSystem());
        getEntityStoreRegistry().registerSystem(new ConstructorJobCheckSystem());
        getEntityStoreRegistry().registerSystem(new ColonistItemPickupSystem());
        getEntityStoreRegistry().registerSystem(new ColonistDeliverySystem());
        getChunkStoreRegistry().registerSystem(new ColonistDeliverySystem.OnContainerRemoved());
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

        try {
            PacketAdapters.registerInbound(new ConstructorBuildOrderFilter());
            LOGGER.at(Level.INFO).log("[HytaleColonies] Registered ConstructorBuildOrderFilter");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[HytaleColonies] Failed to register ConstructorBuildOrderFilter");
        }

        try {
            PacketAdapters.registerInbound(new ConstructorPrefabPageFilter());
            LOGGER.at(Level.INFO).log("[HytaleColonies] Registered ConstructorPrefabPageFilter");
        } catch (Exception e) {
            LOGGER.at(Level.WARNING).withCause(e).log("[HytaleColonies] Failed to register ConstructorPrefabPageFilter");
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
        ConstructionOrderStore.reset();
        instance = null;
    }
}