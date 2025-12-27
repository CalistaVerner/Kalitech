package org.foxesworld.kalitech.engine.api.impl.input;

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

    public record Consumed(double dx, double dy, double wheel) {}

    public void setAbsolute(double x, double y) {
        this.mx = x;
        this.my = y;
    }

    public double mouseX() { return mx; }
    public double mouseY() { return my; }

    public boolean mouseDown(int button) {
        if (button < 0 || button >= 31) return false;
        return (mouseMask & (1 << button)) != 0;
    }

    public int peekMouseMask() { return mouseMask; }

    public void setMouseDown(int button, boolean down) {
        if (button < 0 || button >= 31) return;
        int bit = 1 << button;
        if (down) mouseMask |= bit;
        else mouseMask &= ~bit;
    }

    public void addDelta(double dx, double dy) {
        mdx += dx;
        mdy += dy;
    }

    public double mouseDx() { return mdx; }
    public double mouseDy() { return mdy; }

    public void addWheel(double w) { wheel += w; }
    public double peekWheel() { return wheel; }

    public double consumeWheelOnly() {
        double w = wheel;
        wheel = 0.0;
        return w;
    }

    public Consumed consumeDeltasOnly() {
        double dx = mdx, dy = mdy;
        mdx = 0.0;
        mdy = 0.0;
        return new Consumed(dx, dy, 0.0);
    }

    public Consumed consumeDeltasAndWheel() {
        double dx = mdx, dy = mdy, w = wheel;
        mdx = 0.0;
        mdy = 0.0;
        wheel = 0.0;
        return new Consumed(dx, dy, w);
    }

    public void resetBaselines() {
        havePrevAbs = false;
    }

    public void ensureFallbackDeltaIfNeeded(boolean grabbed, boolean motionThisFrame) {
        if (!grabbed) return;
        if (motionThisFrame) return;

        final double ax = mx;
        final double ay = my;

        if (!havePrevAbs) {
            prevAbsX = ax;
            prevAbsY = ay;
            havePrevAbs = true;
            return;
        }

        final double dx = ax - prevAbsX;
        final double dy = ay - prevAbsY;

        prevAbsX = ax;
        prevAbsY = ay;

        mdx += dx;
        mdy += dy;
    }
}