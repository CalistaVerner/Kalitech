/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.input;

public final class InputFrame {
    private boolean motionThisFrame = false;

    void markMotion() {
        this.motionThisFrame = true;
    }

    public boolean motionThisFrame() {
        return this.motionThisFrame;
    }

    public void endFrame() {
        this.motionThisFrame = false;
    }
}

