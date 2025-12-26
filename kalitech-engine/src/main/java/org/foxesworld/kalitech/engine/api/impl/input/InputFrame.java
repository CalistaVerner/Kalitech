package org.foxesworld.kalitech.engine.api.impl.input;

final class InputFrame {
    private volatile boolean motionThisFrame = false;

    void markMotion() { motionThisFrame = true; }
    boolean motionThisFrame() { return motionThisFrame; }

    void endFrame() {
        // mdx/mdy НЕ чистим тут — JS их consum'ит
        motionThisFrame = false;
    }
}