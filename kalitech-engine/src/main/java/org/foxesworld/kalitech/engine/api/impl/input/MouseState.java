package org.foxesworld.kalitech.engine.api.impl.input;

// Author: Calista Verner

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MouseState {

    private static final Logger log = LogManager.getLogger(MouseState.class);

    // Mouse buttons bitmask
    private volatile int mouseMask = 0;

    // Absolute
    private volatile double mx = 0.0;
    private volatile double my = 0.0;

    // Accumulated deltas
    private volatile double mdx = 0.0;
    private volatile double mdy = 0.0;
    private volatile double wheel = 0.0;

    // fallback baseline
    private volatile boolean havePrevAbs = false;
    private volatile double prevAbsX = 0.0;
    private volatile double prevAbsY = 0.0;

    public record Consumed(double dx, double dy, double wheel) {}

    // --- absolute ---
    public void setAbsolute(double x, double y) {
        this.mx = x;
        this.my = y;
    }

    public double mouseX() { return mx; }
    public double mouseY() { return my; }

    // --- buttons ---
    public boolean mouseDown(int button) {
        if (button < 0 || button >= 31) return false;
        return (mouseMask & (1 << button)) != 0;
    }

    public int mouseMask() { return mouseMask; }
    public int peekMouseMask() { return mouseMask; }

    public void setMouseDown(int button, boolean down) {
        if (button < 0 || button >= 31) return;
        int bit = 1 << button;
        if (down) mouseMask |= bit;
        else mouseMask &= ~bit;
    }

    // --- deltas ---
    public void addDelta(double dx, double dy) {
        mdx += dx;
        mdy += dy;
    }

    public double mouseDx() { return mdx; }
    public double mouseDy() { return mdy; }

    // --- wheel ---
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

    // --- fallback ---
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

    void dbgDelta(String src, double dx, double dy, double w, boolean motion) {
        // throttled outside, keep it simple
        log.info("[input] src={} mx={} my={} dx={} dy={} wheel={} motion={}",
                src, mx, my, dx, dy, w, motion);
    }
}