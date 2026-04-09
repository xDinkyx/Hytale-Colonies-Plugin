package com.hytalecolonies.npc.sensors.common;

import com.hytalecolonies.components.jobs.JobTargetComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.SensorBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;
import com.hypixel.hytale.server.npc.sensorinfo.PositionProvider;
import javax.annotation.Nonnull;

/**
 * Runtime sensor that fires when the entity has an active job target position
 * and is within the configured range of it.
 *
 * <p>The target position is read from {@link JobTargetComponent} every tick.
 * When the entity is within {@code range} metres (horizontal) of the target,
 * the sensor fires and exposes the position to any paired action via
 * {@link #getSensorInfo()}.
 *
 * <p>Constructed by {@link BuilderSensorJobTarget}.
 */
public class SensorJobTarget extends SensorBase {

    private final double range;
    private final PositionProvider positionProvider = new PositionProvider();

    public SensorJobTarget(@Nonnull BuilderSensorJobTarget builder,
                           @Nonnull BuilderSupport support) {
        super(builder);
        this.range = builder.getRange(support);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref,
                           @Nonnull Role role,
                           double dt,
                           @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, role, dt, store)) {
            positionProvider.clear();
            return false;
        }

        JobTargetComponent jobTarget = store.getComponent(ref, JobTargetComponent.getComponentType());
        if (jobTarget == null) {
            positionProvider.clear();
            return false;
        }

        Vector3i target = jobTarget.targetPosition;
        if (target == null) {
            positionProvider.clear();
            return false;
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            positionProvider.clear();
            return false;
        }

        Vector3d pos = transform.getPosition();
        double dx = target.x + 0.5 - pos.x;
        double dz = target.z + 0.5 - pos.z;
        if (dx * dx + dz * dz > range * range) {
            positionProvider.clear();
            return false;
        }

        positionProvider.setTarget(target.x + 0.5, target.y, target.z + 0.5);
        return true;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return positionProvider;
    }
}
