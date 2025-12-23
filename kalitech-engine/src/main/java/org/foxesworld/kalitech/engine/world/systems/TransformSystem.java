package org.foxesworld.kalitech.engine.world.systems;

import org.foxesworld.kalitech.engine.ecs.EcsWorld;
import org.foxesworld.kalitech.engine.ecs.components.RenderableComponent;
import org.foxesworld.kalitech.engine.ecs.components.TransformComponent;

public final class TransformSystem implements KSystem {

    private final EcsWorld ecs;

    public TransformSystem(EcsWorld ecs) {
        this.ecs = ecs;
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        // Наивно: пробегаем по всем Transform-ам и двигаем Spatial если есть Renderable
        for (var entry : ecs.components().view(TransformComponent.class).entrySet()) {
            int e = entry.getKey();
            TransformComponent tr = entry.getValue();
            RenderableComponent rc = ecs.components().get(e, RenderableComponent.class);
            if (rc != null && rc.spatial != null) {
                rc.spatial.setLocalTranslation(tr.x, tr.y, tr.z);
                rc.spatial.setLocalRotation(rc.spatial.getLocalRotation().fromAngles(0f, tr.rotY, 0f));
            }
        }
    }
}