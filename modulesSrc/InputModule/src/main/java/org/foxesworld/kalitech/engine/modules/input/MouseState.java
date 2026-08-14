/*
 * Decompiled with CFR 0.152.
 */
package org.foxesworld.kalitech.engine.modules.input;

public final class MouseState {
    private int mouseMask = 0;
    private double mx = 0.0;
    private double my = 0.0;
    private double mdx = 0.0;
    private double mdy = 0.0;
    private double wheel = 0.0;
    private boolean havePrevAbs = false;
    private double prevAbsX = 0.0;
    private double prevAbsY = 0.0;

    public void setAbsolute(double x, double y) {
        this.mx = x;
        this.my = y;
    }

    public double mouseX() {
        return this.mx;
    }

    public double mouseY() {
        return this.my;
    }

    public boolean mouseDown(int button) {
        if (button < 0 || button >= 31) {
            return false;
        }
        return (this.mouseMask & 1 << button) != 0;
    }

    public int peekMouseMask() {
        return this.mouseMask;
    }

    public void setMouseDown(int button, boolean down) {
        if (button < 0 || button >= 31) {
            return;
        }
        int bit = 1 << button;
        this.mouseMask = down ? (this.mouseMask |= bit) : (this.mouseMask &= ~bit);
    }

    public void addDelta(double dx, double dy) {
        this.mdx += dx;
        this.mdy += dy;
    }

    public double mouseDx() {
        return this.mdx;
    }

    public double mouseDy() {
        return this.mdy;
    }

    public void addWheel(double w) {
        this.wheel += w;
    }

    public double peekWheel() {
        return this.wheel;
    }

    public double consumeWheelOnly() {
        double w = this.wheel;
        this.wheel = 0.0;
        return w;
    }

    public Consumed consumeDeltasOnly() {
        double dx = this.mdx;
        double dy = this.mdy;
        this.mdx = 0.0;
        this.mdy = 0.0;
        return new Consumed(dx, dy, 0.0);
    }

    public Consumed consumeDeltasAndWheel() {
        double dx = this.mdx;
        double dy = this.mdy;
        double w = this.wheel;
        this.mdx = 0.0;
        this.mdy = 0.0;
        this.wheel = 0.0;
        return new Consumed(dx, dy, w);
    }

    public void resetBaselines() {
        this.havePrevAbs = false;
    }

    public void ensureFallbackDeltaIfNeeded(boolean grabbed, boolean motionThisFrame) {
        if (!grabbed) {
            return;
        }
        if (motionThisFrame) {
            return;
        }
        double ax = this.mx;
        double ay = this.my;
        if (!this.havePrevAbs) {
            this.prevAbsX = ax;
            this.prevAbsY = ay;
            this.havePrevAbs = true;
            return;
        }
        double dx = ax - this.prevAbsX;
        double dy = ay - this.prevAbsY;
        this.prevAbsX = ax;
        this.prevAbsY = ay;
        this.mdx += dx;
        this.mdy += dy;
    }

    public record Consumed(double dx, double dy, double wheel) {
    }
}

