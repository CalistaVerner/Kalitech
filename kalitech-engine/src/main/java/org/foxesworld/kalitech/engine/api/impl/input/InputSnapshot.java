package org.foxesworld.kalitech.engine.api.impl.input;

// Author: Calista Verner

import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Immutable input snapshot for a single frame (AAA++).
 * One object → JS reads everything from it.
 */
public final class InputSnapshot {

    public final long frameId;
    public final long timeNanos;

    public final double mx;
    public final double my;

    public final double dx;
    public final double dy;

    public final double wheel;

    public final int mouseMask;

    public final boolean grabbed;
    public final boolean cursorVisible;

    /** Keys currently held down */
    public final int[] keysDown;

    /** Keys pressed THIS frame */
    public final int[] justPressed;

    /** Keys released THIS frame */
    public final int[] justReleased;

    public InputSnapshot(
            long frameId,
            long timeNanos,
            double mx, double my,
            double dx, double dy,
            double wheel,
            int mouseMask,
            boolean grabbed,
            boolean cursorVisible,
            int[] keysDown,
            int[] justPressed,
            int[] justReleased
    ) {
        this.frameId = frameId;
        this.timeNanos = timeNanos;

        this.mx = mx;
        this.my = my;

        this.dx = dx;
        this.dy = dy;

        this.wheel = wheel;

        this.mouseMask = mouseMask;

        this.grabbed = grabbed;
        this.cursorVisible = cursorVisible;

        this.keysDown     = (keysDown != null)     ? keysDown     : new int[0];
        this.justPressed  = (justPressed != null)  ? justPressed  : new int[0];
        this.justReleased = (justReleased != null) ? justReleased : new int[0];
    }

    /**
     * Export snapshot to JS as a stable plain object.
     */
    public Object toJs() {
        Map<String, Object> m = new HashMap<>(20);

        m.put("frame", frameId);
        m.put("timeNanos", timeNanos);

        m.put("mx", mx);
        m.put("my", my);

        m.put("dx", dx);
        m.put("dy", dy);

        m.put("wheel", wheel);

        m.put("mouseMask", mouseMask);

        m.put("grabbed", grabbed);
        m.put("cursorVisible", cursorVisible);

        m.put("keysDown", keysDown);
        m.put("justPressed", justPressed);
        m.put("justReleased", justReleased);

        return ProxyObject.fromMap(m);
    }
}