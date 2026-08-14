/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 *  org.foxesworld.kalitech.engine.script.lua.LuaArray
 *  org.foxesworld.kalitech.engine.script.lua.LuaObject
 */
package org.foxesworld.kalitech.engine.modules.input;

import org.foxesworld.kalitech.engine.modules.input.LuaMarshalling;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.foxesworld.kalitech.engine.script.lua.LuaArray;
import org.foxesworld.kalitech.engine.script.lua.LuaObject;

public final class InputSnapshot
implements LuaObject {
    private static final LuaArray MEMBER_KEYS = LuaArray.fromArray((Object[])new Object[]{"frame", "timeNanos", "mx", "my", "dx", "dy", "wheel", "mouseMask", "grabbed", "cursorVisible", "keysDown", "justPressed", "justReleased"});
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

    public InputSnapshot(long frameId, long timeNanos, double mx, double my, double dx, double dy, double wheel, int mouseMask, boolean grabbed, boolean cursorVisible, int[] keysDown, int[] justPressed, int[] justReleased) {
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
        this.keysDown = keysDown != null ? keysDown : new int[]{};
        this.justPressed = justPressed != null ? justPressed : new int[]{};
        this.justReleased = justReleased != null ? justReleased : new int[]{};
    }

    public Object getMember(String key) {
        if (key == null) {
            return null;
        }
        return switch (key) {
            case "frame" -> this.frameId;
            case "timeNanos" -> this.timeNanos;
            case "mx" -> this.mx;
            case "my" -> this.my;
            case "dx" -> this.dx;
            case "dy" -> this.dy;
            case "wheel" -> this.wheel;
            case "mouseMask" -> this.mouseMask;
            case "grabbed" -> this.grabbed;
            case "cursorVisible" -> this.cursorVisible;
            case "keysDown" -> LuaMarshalling.intArray(this.keysDown);
            case "justPressed" -> LuaMarshalling.intArray(this.justPressed);
            case "justReleased" -> LuaMarshalling.intArray(this.justReleased);
            default -> null;
        };
    }

    public Object getMemberKeys() {
        return MEMBER_KEYS;
    }

    public boolean hasMember(String key) {
        if (key == null) {
            return false;
        }
        return switch (key) {
            case "frame", "timeNanos", "mx", "my", "dx", "dy", "wheel", "mouseMask", "grabbed", "cursorVisible", "keysDown", "justPressed", "justReleased" -> true;
            default -> false;
        };
    }

    public void putMember(String key, LuaValueRef value) {
    }
}

