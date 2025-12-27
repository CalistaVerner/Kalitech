package org.foxesworld.kalitech.engine.api.impl.input;

final class InputFrame {
    private boolean motionThisFrame = false;

    void markMotion() { motionThisFrame = true; }
    boolean motionThisFrame() { return motionThisFrame; }

    void endFrame() { motionThisFrame = false; }
}
