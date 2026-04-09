package com.hytalecolonies.npc.sensors;

import com.hytalecolonies.components.jobs.JobComponent;
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
import javax.annotation.Nonnull;

/**
 * Runtime sensor that fires when the colonist is within {@link #THRESHOLD} blocks (XZ)
 * of their assigned workstation.
 *
 * <p>Constructed by {@link BuilderSensorAtWorkstation}.
 */
public class SensorAtWorkstation extends SensorBase {

    /** XZ distance in blocks at which the colonist is considered "at" the workstation. */
    private static final double THRESHOLD = 3.0;

    public SensorAtWorkstation(@Nonnull BuilderSensorAtWorkstation builder,
                               @Nonnull BuilderSupport support) {
        super(builder);
    }

    @Override
    public boolean matches(@Nonnull Ref<EntityStore> ref,
                           @Nonnull Role role,
                           double dt,
                           @Nonnull Store<EntityStore> store) {
        if (!super.matches(ref, role, dt, store)) return false;

        JobComponent job = store.getComponent(ref, JobComponent.getComponentType());
        if (job == null) return false;

        Vector3i wsPos = job.getWorkStationBlockPosition();
        if (wsPos == null) return false;

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return false;

        Vector3d pos = transform.getPosition();
        double dx = wsPos.x + 0.5 - pos.x;
        double dz = wsPos.z + 0.5 - pos.z;
        return dx * dx + dz * dz <= THRESHOLD * THRESHOLD;
    }

    @Override
    public InfoProvider getSensorInfo() {
        return null;
    }
}
