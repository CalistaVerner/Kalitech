package org.foxesworld.kalitech.engine.modules.input;

public final class InputFrame {
    private boolean motionThisFrame = false;

    void markMotion() {
        motionThisFrame = true;
    }

    public boolean motionThisFrame() {
        return motionThisFrame;
    }

    public void endFrame() {
        motionThisFrame = false;
    }
}
