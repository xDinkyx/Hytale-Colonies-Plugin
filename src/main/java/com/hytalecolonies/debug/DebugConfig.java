package com.hytalecolonies.debug;

import java.util.logging.Level;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Persistent per-category debug log levels and draw toggles.
 */
public class DebugConfig {

    public static final BuilderCodec<DebugConfig> CODEC =
        BuilderCodec.builder(DebugConfig.class, DebugConfig::new)
            .append(new KeyedCodec<>("GeneralLevel", Codec.STRING),
                    (c, v) -> c.generalLevel = v, c -> c.generalLevel)
            .add()
            .append(new KeyedCodec<>("MovementLevel", Codec.STRING),
                    (c, v) -> c.movementLevel = v, c -> c.movementLevel)
            .add()
            .append(new KeyedCodec<>("JobSystemLevel", Codec.STRING),
                    (c, v) -> c.jobSystemLevel = v, c -> c.jobSystemLevel)
            .add()
            .append(new KeyedCodec<>("JobAssignmentLevel", Codec.STRING),
                    (c, v) -> c.jobAssignmentLevel = v, c -> c.jobAssignmentLevel)
            .add()
            .append(new KeyedCodec<>("WoodsmanJobLevel", Codec.STRING),
                    (c, v) -> c.woodsmanJobLevel = v, c -> c.woodsmanJobLevel)
            .add()
            .append(new KeyedCodec<>("MinerJobLevel", Codec.STRING),
                    (c, v) -> c.minerJobLevel = v, c -> c.minerJobLevel)
            .add()
            .append(new KeyedCodec<>("ConstructorJobLevel", Codec.STRING),
                    (c, v) -> c.constructorJobLevel = v, c -> c.constructorJobLevel)
            .add()
            .append(new KeyedCodec<>("TreeScannerLevel", Codec.STRING),
                    (c, v) -> c.treeScannerLevel = v, c -> c.treeScannerLevel)
            .add()
            .append(new KeyedCodec<>("ColonistDeliveryLevel", Codec.STRING),
                    (c, v) -> c.colonistDeliveryLevel = v, c -> c.colonistDeliveryLevel)
            .add()
            .append(new KeyedCodec<>("ClaimSystemLevel", Codec.STRING),
                    (c, v) -> c.claimSystemLevel = v, c -> c.claimSystemLevel)
            .add()
            .append(new KeyedCodec<>("ColonistLifecycleLevel", Codec.STRING),
                    (c, v) -> c.colonistLifecycleLevel = v, c -> c.colonistLifecycleLevel)
            .add()
            .append(new KeyedCodec<>("DrawColonistPaths", Codec.BOOLEAN),
                    (c, v) -> c.drawColonistPaths = v, c -> c.drawColonistPaths)
            .add()
            .append(new KeyedCodec<>("DrawTreeDetection", Codec.BOOLEAN),
                    (c, v) -> c.drawTreeDetection = v, c -> c.drawTreeDetection)
            .add()
            .append(new KeyedCodec<>("DrawConstructorOrders", Codec.BOOLEAN),
                    (c, v) -> c.drawConstructorOrders = v, c -> c.drawConstructorOrders)
            .add()
            .append(new KeyedCodec<>("PerformanceLevel", Codec.STRING),
                    (c, v) -> c.performanceLevel = v, c -> c.performanceLevel)
            .add()
            .build();

    private String generalLevel         = "INFO";
    private String movementLevel       = "INFO";
    private String jobSystemLevel      = "INFO";
    private String jobAssignmentLevel  = "INFO";
    private String woodsmanJobLevel    = "INFO";
    private String minerJobLevel       = "INFO";
    private String constructorJobLevel = "INFO";
    private String treeScannerLevel    = "INFO";
    private String colonistDeliveryLevel = "INFO";
    private String claimSystemLevel      = "INFO";
    private String colonistLifecycleLevel = "INFO";
    private String performanceLevel      = "WARNING";
    private boolean drawColonistPaths = false;
    private boolean drawTreeDetection = false;
    private boolean drawConstructorOrders = false;

    public DebugConfig() {}

    public void applyToCategories() {
        DebugCategory.GENERAL.setMinLevel(DebugLogUtil.parseLevel(generalLevel));
        DebugCategory.MOVEMENT.setMinLevel(DebugLogUtil.parseLevel(movementLevel));
        DebugCategory.JOB_SYSTEM.setMinLevel(DebugLogUtil.parseLevel(jobSystemLevel));
        DebugCategory.JOB_ASSIGNMENT.setMinLevel(DebugLogUtil.parseLevel(jobAssignmentLevel));
        DebugCategory.WOODSMAN_JOB.setMinLevel(DebugLogUtil.parseLevel(woodsmanJobLevel));
        DebugCategory.MINER_JOB.setMinLevel(DebugLogUtil.parseLevel(minerJobLevel));
        DebugCategory.CONSTRUCTOR_JOB.setMinLevel(DebugLogUtil.parseLevel(constructorJobLevel));
        DebugCategory.TREE_SCANNER.setMinLevel(DebugLogUtil.parseLevel(treeScannerLevel));
        DebugCategory.COLONIST_DELIVERY.setMinLevel(DebugLogUtil.parseLevel(colonistDeliveryLevel));
        DebugCategory.CLAIM_SYSTEM.setMinLevel(DebugLogUtil.parseLevel(claimSystemLevel));
        DebugCategory.COLONIST_LIFECYCLE.setMinLevel(DebugLogUtil.parseLevel(colonistLifecycleLevel));
        DebugCategory.PERFORMANCE.setMinLevel(DebugLogUtil.parseLevel(performanceLevel));
    }

    /** Also updates the live category threshold; call {@code config.save()} to persist. */
    public void setLevelForCategory(DebugCategory category, Level level) {
        String name = level.getName();
        switch (category) {
            case GENERAL                -> generalLevel = name;
            case MOVEMENT           -> movementLevel = name;
            case JOB_SYSTEM         -> jobSystemLevel = name;
            case JOB_ASSIGNMENT     -> jobAssignmentLevel = name;
            case WOODSMAN_JOB       -> woodsmanJobLevel = name;
            case MINER_JOB          -> minerJobLevel = name;
            case CONSTRUCTOR_JOB    -> constructorJobLevel = name;
            case TREE_SCANNER       -> treeScannerLevel = name;
            case COLONIST_DELIVERY  -> colonistDeliveryLevel = name;
            case CLAIM_SYSTEM           -> claimSystemLevel = name;
            case COLONIST_LIFECYCLE     -> colonistLifecycleLevel = name;
            case PERFORMANCE            -> performanceLevel = name;
        }
        category.setMinLevel(level);
    }

    public String getLevelNameForCategory(DebugCategory category) {
        return switch (category) {
            case GENERAL                -> generalLevel;
            case MOVEMENT               -> movementLevel;
            case JOB_SYSTEM             -> jobSystemLevel;
            case JOB_ASSIGNMENT         -> jobAssignmentLevel;
            case WOODSMAN_JOB           -> woodsmanJobLevel;
            case MINER_JOB              -> minerJobLevel;
            case CONSTRUCTOR_JOB        -> constructorJobLevel;
            case TREE_SCANNER           -> treeScannerLevel;
            case COLONIST_DELIVERY      -> colonistDeliveryLevel;
            case CLAIM_SYSTEM           -> claimSystemLevel;
            case COLONIST_LIFECYCLE     -> colonistLifecycleLevel;
            case PERFORMANCE            -> performanceLevel;
        };
    }

    public boolean isDrawColonistPaths() { return drawColonistPaths; }
    public void setDrawColonistPaths(boolean v) { drawColonistPaths = v; }

    public boolean isDrawTreeDetection() { return drawTreeDetection; }
    public void setDrawTreeDetection(boolean v) { drawTreeDetection = v; }

    public boolean isDrawConstructorOrders() { return drawConstructorOrders; }
    public void setDrawConstructorOrders(boolean v) { drawConstructorOrders = v; }

    public static Level parseLevelName(String name) {
        return DebugLogUtil.parseLevel(name);
    }
}
