package com.hytalecolonies.npc.sensors.common;

import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hytalecolonies.debug.DebugCategory;
import com.hytalecolonies.debug.DebugLog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import javax.annotation.Nonnull;

/**
 * Runtime sensor -- fires when {@link JobTargetComponent} has a position AND
 * the block at that position is air (id == 0), meaning the target block was broken.
 */
public class SensorJobTargetBroken extends SensorBase {

    public SensorJobTargetBroken(@Nonnull BuilderSensorJobTargetBroken builder,
                                  @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                           double dt, @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, role, dt, store)) {
            return false;
        }

        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jobTarget == null || jobTarget.targetPosition == null) {
            return false;
        }

        Vector3i position = jobTarget.targetPosition;
        World world = store.getExternalData().getWorld();
        boolean broken = world.getBlock(position.x, position.y, position.z) == 0;
        DebugLog.fine(DebugCategory.MINER_JOB,
                "[SensorJobTargetBroken] [%s] position=%s broken=%b.", DebugLog.npcId(ref, store), position, broken);
        return broken;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }
}
