package org.foxesworld.kalitech.engine.api.impl.input;

import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Set;

public final class InputSnapshot implements ProxyObject {

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

    public final int[] keysDown;
    public final int[] justPressed;
    public final int[] justReleased;

    private static final Set<String> KEYS = Set.of(
            "frame", "timeNanos",
            "mx", "my",
            "dx", "dy",
            "wheel",
            "mouseMask",
            "grabbed", "cursorVisible",
            "keysDown", "justPressed", "justReleased"
    );

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
        this.keysDown = (keysDown != null) ? keysDown : new int[0];
        this.justPressed = (justPressed != null) ? justPressed : new int[0];
        this.justReleased = (justReleased != null) ? justReleased : new int[0];
    }

    @Override
    public Object getMember(String key) {
        return switch (key) {
            case "frame" -> frameId;
            case "timeNanos" -> timeNanos;
            case "mx" -> mx;
            case "my" -> my;
            case "dx" -> dx;
            case "dy" -> dy;
            case "wheel" -> wheel;
            case "mouseMask" -> mouseMask;
            case "grabbed" -> grabbed;
            case "cursorVisible" -> cursorVisible;
            case "keysDown" -> JsMarshalling.intArray(keysDown);
            case "justPressed" -> JsMarshalling.intArray(justPressed);
            case "justReleased" -> JsMarshalling.intArray(justReleased);
            default -> null;
        };
    }

    @Override
    public Object getMemberKeys() {
        return ProxyArray.fromArray(KEYS.toArray(new Object[0]));
    }

    @Override
    public boolean hasMember(String key) {
        return KEYS.contains(key);
    }

    @Override
    public void putMember(String key, org.graalvm.polyglot.Value value) {
    }
}