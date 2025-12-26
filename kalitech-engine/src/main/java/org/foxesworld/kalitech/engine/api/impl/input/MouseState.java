package org.foxesworld.kalitech.engine.api.impl.input;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class MouseState {

    private static final Logger log = LogManager.getLogger(MouseState.class);

    // Absolute
    private volatile double mx = 0.0;
    private volatile double my = 0.0;

    // Buttons bitmask
    private volatile int mouseMask = 0;

    // Accumulators
    private double mdx = 0.0;
    private double mdy = 0.0;
    private double wheel = 0.0;

    // Baselines (fallback)
    private boolean havePrevAbs = false;
    private double prevAbsX = 0.0;
    private double prevAbsY = 0.0;

    private boolean haveLastRawAbs = false;
    private double lastRawAbsX = 0.0;
    private double lastRawAbsY = 0.0;

    // Debug throttling
    private long dbgTick = 0;

    // ---- absolute ----
    double mouseX() { return mx; }
    double mouseY() { return my; }
    void setAbsolute(double x, double y) { mx = x; my = y; }

    // ---- buttons ----
    boolean mouseDown(int button) {
        if (button < 0 || button >= 31) return false;
        return (mouseMask & (1 << button)) != 0;
    }

    int peekMouseMask() { return mouseMask; }

    void setMouseDown(int button, boolean down) {
        if (button < 0 || button >= 31) return;
        int bit = 1 << button;
        if (down) mouseMask |= bit;
        else mouseMask &= ~bit;
    }

    // ---- deltas ----
    synchronized void addDelta(double dx, double dy) { mdx += dx; mdy += dy; }
    synchronized void addWheel(double w) { wheel += w; }

    double mouseDx() { return mdx; }
    double mouseDy() { return mdy; }

    synchronized Delta consumeMouseDelta() {
        double dx = mdx, dy = mdy;
        mdx = 0.0; mdy = 0.0;
        return new Delta(dx, dy);
    }

    double peekWheel() { return wheel; }

    synchronized double consumeWheel() {
        double w = wheel;
        wheel = 0.0;
        return w;
    }

    synchronized Consumed consumeDeltasAndWheel() {
        double dx = mdx, dy = mdy, w = wheel;
        mdx = 0.0; mdy = 0.0; wheel = 0.0;
        return new Consumed(dx, dy, w);
    }

    static final class Consumed {
        final double dx, dy, wheel;
        Consumed(double dx, double dy, double wheel) {
            this.dx = dx; this.dy = dy; this.wheel = wheel;
        }
    }

    // ---- fallback logic ----
    void resetBaselines() {
        havePrevAbs = false;
        haveLastRawAbs = false;
    }

    void ensureFallbackDeltaIfNeeded(boolean grabbed, boolean motionThisFrame) {
        // Важно: fallback делаем только если grab и не было motion от raw/axis.
        if (!grabbed) return;
        if (motionThisFrame) return;
        applyFallbackDeltaFromAbsolute();
    }

    private synchronized void applyFallbackDeltaFromAbsolute() {
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

    // ---- raw motion feed ----
    void onRawMotion(double absX, double absY, double rawDx, double rawDy, double rawWheel) {
        mx = absX;
        my = absY;

        double dx = rawDx;
        double dy = rawDy;

        if (dx == 0.0 && dy == 0.0) {
            if (haveLastRawAbs) {
                dx = absX - lastRawAbsX;
                dy = absY - lastRawAbsY;
            }
        }

        lastRawAbsX = absX;
        lastRawAbsY = absY;
        haveLastRawAbs = true;

        synchronized (this) {
            mdx += dx;
            mdy += dy;
            wheel += rawWheel;
        }
    }

    // ---- debug ----
    void dbgDelta(String src, double dx, double dy, double w, boolean motion) {
        dbgTick++;
        if ((dbgTick % 60) != 0) return;
        log.info("[input] src={} mx={} my={} dx={} dy={} wheel={} motion={}",
                src, mx, my, dx, dy, w, motion);
    }

    void dbgEndFrame(double dx, double dy, double w, boolean motion, boolean grabbed, boolean cursorVisible) {
        dbgTick++;
        if ((dbgTick % 60) != 0) return;
        log.info("[input] src=endFrame mx={} my={} dx={} dy={} wheel={} motion={} cursorVisible={} grabbed={}",
                mx, my, dx, dy, w, motion, cursorVisible, grabbed);
    }

    static final class Delta {
        final double dx, dy;
        Delta(double dx, double dy) { this.dx = dx; this.dy = dy; }
    }
}