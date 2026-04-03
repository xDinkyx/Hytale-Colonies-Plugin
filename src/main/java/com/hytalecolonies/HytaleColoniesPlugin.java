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
import com.hytalecolonies.components.world.ClaimedBlockComponent;
import com.hytalecolonies.components.world.HarvestableTreeComponent;
import com.hytalecolonies.components.jobs.JobComponent;
import com.hytalecolonies.components.jobs.JobState;
import com.hytalecolonies.components.jobs.MinerJobComponent;
import com.hytalecolonies.components.jobs.UnemployedComponent;
import com.hytalecolonies.components.jobs.WoodsmanJobComponent;
import com.hytalecolonies.components.jobs.WorkStationComponent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hytalecolonies.interactions.SpawnColonistInteraction;
import com.hytalecolonies.systems.ColonySystem;
import com.hytalecolonies.systems.jobs.JobAssignmentSystems;
import com.hytalecolonies.systems.jobs.ClaimedBlockCleanupSystem;
import com.hytalecolonies.systems.jobs.ColonistCleanupSystem;
import com.hytalecolonies.systems.jobs.WorkstationInitSystem;
import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.systems.jobs.MinerWorkingSystem;
import com.hytalecolonies.systems.jobs.ColonistDeliverySystem;
import com.hytalecolonies.systems.jobs.ColonistItemPickupSystem;
import com.hytalecolonies.systems.jobs.ColonistJobSystem;
import com.hytalecolonies.systems.jobs.JobRegistry;
import com.hytalecolonies.systems.jobs.handlers.MinerHandlers;
import com.hytalecolonies.systems.jobs.handlers.SharedHandlers;
import com.hytalecolonies.npc.actions.BuilderActionNotifyBlockBroken;
import com.hytalecolonies.npc.actions.BuilderActionEquipBestTool;
import com.hytalecolonies.npc.actions.BuilderActionHarvestBlock;
import com.hytalecolonies.npc.actions.BuilderActionClaimNearestTree;
import com.hytalecolonies.npc.actions.BuilderActionSeekNearestTree;
import com.hytalecolonies.npc.actions.BuilderActionFindNextTrunkBlock;
import com.hytalecolonies.npc.actions.BuilderActionClaimNextMineBlock;
import com.hytalecolonies.npc.actions.BuilderActionSeekNextMineBlock;
import com.hytalecolonies.npc.actions.BuilderActionReleaseJobTarget;
import com.hytalecolonies.npc.actions.BuilderActionIncrementBlocksMined;
import com.hytalecolonies.npc.actions.BuilderActionResetBlocksMined;
import com.hytalecolonies.npc.actions.BuilderActionSetEcsJobState;
import com.hytalecolonies.npc.sensors.BuilderSensorHarvestableTree;
import com.hytalecolonies.npc.sensors.BuilderSensorJobTarget;
import com.hytalecolonies.npc.sensors.BuilderSensorHasTool;
import com.hytalecolonies.npc.sensors.BuilderSensorJobTargetExists;
import com.hytalecolonies.npc.sensors.BuilderSensorJobTargetBroken;
import com.hytalecolonies.npc.sensors.BuilderSensorMineQuotaReached;
import com.hytalecolonies.npc.sensors.BuilderSensorNoWorkAvailable;
import com.hytalecolonies.npc.sensors.BuilderSensorEcsJobState;

import com.hytalecolonies.systems.npc.ColonistRemovalSystem;
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
        workStationComponentType = getChunkStoreRegistry().registerComponent(WorkStationComponent.class, "WorkStation", WorkStationComponent.CODEC);
        moveToTargetComponentType = getEntityStoreRegistry().registerComponent(MoveToTargetComponent.class, MoveToTargetComponent::new);
        harvestableTreeComponentType = getChunkStoreRegistry().registerComponent(HarvestableTreeComponent.class, "HarvestableTree", HarvestableTreeComponent.CODEC);
        jobTargetComponentType = getEntityStoreRegistry().registerComponent(JobTargetComponent.class, "JobTarget", JobTargetComponent.CODEC);
        claimedBlockComponentType = getChunkStoreRegistry().registerComponent(ClaimedBlockComponent.class, "ClaimedBlock", ClaimedBlockComponent.CODEC);
        LOGGER.at(Level.INFO).log("[HytaleColonies] Registered ECS components");
    }

    /**
     * Registers the shared ECS job handlers (CollectingDrops and TravelingHome).
     * Job-specific Idling and Working states are now handled by the NPC role JSON.
     */
    private void registerSharedJobHandlers() {
        // Keep job types in JobRegistry so JobAssignmentSystems.fireColonist() can strip them.
        JobRegistry.register(WoodsmanJobComponent.getComponentType());
        JobRegistry.register(MinerJobComponent.getComponentType());
        // Only shared ECS phases remain — job-specific Idling/Working are JSON-driven.
        ColonistJobSystem.registerShared(JobState.CollectingDrops, SharedHandlers.COLLECTING_DROPS);
        ColonistJobSystem.registerShared(JobState.TravelingToJob,   SharedHandlers.TRAVELING_TO_JOB);
        ColonistJobSystem.registerShared(JobState.TravelingHome,    SharedHandlers.TRAVELING_HOME);
        // Miner-specific Idling handler: scans for mine targets and transitions to TravelingToJob.
        // Guards on MinerJobComponent internally, so it is safe to register as a shared handler.
        ColonistJobSystem.registerShared(JobState.Idling, MinerHandlers.IDLE);
        LOGGER.at(Level.INFO).log("[HytaleColonies] Registered shared job handlers");
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
            // Existing types
            .registerCoreComponentType("EquipBestTool",         BuilderActionEquipBestTool::new)
            .registerCoreComponentType("HarvestableTree",       BuilderSensorHarvestableTree::new)
            .registerCoreComponentType("HarvestBlock",          BuilderActionHarvestBlock::new)
            .registerCoreComponentType("JobTarget",             BuilderSensorJobTarget::new)
            // New sensors
            .registerCoreComponentType("HasTool",               BuilderSensorHasTool::new)
            .registerCoreComponentType("JobTargetExists",       BuilderSensorJobTargetExists::new)
            .registerCoreComponentType("JobTargetBroken",       BuilderSensorJobTargetBroken::new)
            .registerCoreComponentType("MineQuotaReached",      BuilderSensorMineQuotaReached::new)
            .registerCoreComponentType("EcsJobState",           BuilderSensorEcsJobState::new)
            .registerCoreComponentType("NoWorkAvailable",       BuilderSensorNoWorkAvailable::new)
            // New actions
            .registerCoreComponentType("SeekNearestTree",       BuilderActionSeekNearestTree::new)
            .registerCoreComponentType("SeekNextMineBlock",     BuilderActionSeekNextMineBlock::new)
            .registerCoreComponentType("ClaimNearestTree",      BuilderActionClaimNearestTree::new)
            .registerCoreComponentType("FindNextTrunkBlock",    BuilderActionFindNextTrunkBlock::new)
            .registerCoreComponentType("ClaimNextMineBlock",    BuilderActionClaimNextMineBlock::new)
            .registerCoreComponentType("ReleaseJobTarget",      BuilderActionReleaseJobTarget::new)
            .registerCoreComponentType("IncrementBlocksMined",  BuilderActionIncrementBlocksMined::new)
            .registerCoreComponentType("ResetBlocksMined",      BuilderActionResetBlocksMined::new)
            .registerCoreComponentType("SetEcsJobState",        BuilderActionSetEcsJobState::new)
            .registerCoreComponentType("NotifyBlockBroken",      BuilderActionNotifyBlockBroken::new);
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
        getEntityStoreRegistry().registerSystem(new JobAssignmentSystems.ColonistEntitySystem());
        getEntityStoreRegistry().registerSystem(new JobAssignmentSystems.JobAssignedSystem());
        getEntityStoreRegistry().registerSystem(new JobAssignmentSystems.UnemployedAssignedSystem());
        getEntityStoreRegistry().registerSystem(new PathFindingSystem());
        getEntityStoreRegistry().registerSystem(new ColonistJobSystem());
        getEntityStoreRegistry().registerSystem(new ColonistRemovalSystem());
        getEntityStoreRegistry().registerSystem(new MinerWorkingSystem());
        getEntityStoreRegistry().registerSystem(new ColonistItemPickupSystem());
        getEntityStoreRegistry().registerSystem(new ColonistDeliverySystem());
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