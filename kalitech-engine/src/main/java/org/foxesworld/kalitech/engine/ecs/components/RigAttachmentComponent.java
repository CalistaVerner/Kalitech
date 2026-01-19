package org.foxesworld.kalitech.engine.ecs.components;

import org.foxesworld.kalitech.engine.modules.rig.RigBinding;

public final class RigAttachmentComponent {

    public int parentEntity;

    public String rigProfileId;

    public String socketId;

    public float ox, oy, oz;
    public float rxDeg, ryDeg, rzDeg;

    public boolean followRotation = true;

    public transient RigBinding binding;
    public transient String boundProfileId;

    public RigAttachmentComponent() {
    }

    public RigAttachmentComponent(int parentEntity, String rigProfileId, String socketId) {
        this.parentEntity = parentEntity;
        this.rigProfileId = rigProfileId;
        this.socketId = socketId;
    }
}