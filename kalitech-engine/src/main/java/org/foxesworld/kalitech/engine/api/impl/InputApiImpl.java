// FILE: InputApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.InputApi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class InputApiImpl implements InputApi {

    private static final Logger log = LogManager.getLogger(InputApiImpl.class);

    private final InputManager input;

    // ✅ fast key state (avoid ConcurrentHashMap per key)
    private final boolean[] keys = new boolean[1024];

    private final AtomicInteger mouseMask = new AtomicInteger(0);

    private volatile double mx, my;
    private volatile double mdx, mdy;
    private volatile double wheel;

    public InputApiImpl(EngineApiImpl engineApi) {
        this.input = engineApi.getApp().getInputManager();
        this.input.addRawInputListener(new Raw());
        log.info("InputApi: raw listener attached");
    }

    /** Called from update-thread once per frame to reset deltas. */
    public void endFrame() {
        mdx = 0.0;
        mdy = 0.0;
        wheel = 0.0;
    }

    @Override
    public boolean keyDown(String key) {
        int code = KeyNames.toKeyCode(key);
        if (code <= 0 || code >= keys.length) return false;
        return keys[code];
    }

    @Override public double mouseX() { return mx; }
    @Override public double mouseY() { return my; }
    @Override public double mouseDX() { return mdx; }
    @Override public double mouseDY() { return mdy; }
    @Override public double wheelDelta() { return wheel; }

    @Override
    public boolean mouseDown(int button) {
        if (button < 0 || button >= 31) return false;
        return (mouseMask.get() & (1 << button)) != 0;
    }

    private void setMouseDown(int button, boolean down) {
        if (button < 0 || button >= 31) return;
        final int bit = 1 << button;
        while (true) {
            int cur = mouseMask.get();
            int next = down ? (cur | bit) : (cur & ~bit);
            if (mouseMask.compareAndSet(cur, next)) return;
        }
    }

    private final class Raw implements RawInputListener {
        @Override public void beginInput() {}
        @Override public void endInput() {}

        @Override public void onJoyAxisEvent(JoyAxisEvent evt) {}
        @Override public void onJoyButtonEvent(JoyButtonEvent evt) {}
        @Override public void onTouchEvent(TouchEvent evt) {}

        @Override
        public void onKeyEvent(KeyInputEvent evt) {
            int code = evt.getKeyCode();
            if (code > 0 && code < keys.length) keys[code] = evt.isPressed();
        }

        @Override
        public void onMouseMotionEvent(MouseMotionEvent evt) {
            mx = evt.getX();
            my = evt.getY();
            mdx += evt.getDX();
            mdy += evt.getDY();
            wheel += evt.getDeltaWheel();
        }

        @Override
        public void onMouseButtonEvent(MouseButtonEvent evt) {
            setMouseDown(evt.getButtonIndex(), evt.isPressed());
        }
    }

    private static final class KeyNames {
        private static final Map<String, Integer> MAP = new ConcurrentHashMap<>();
        static {
            for (char c = 'A'; c <= 'Z'; c++) MAP.put(String.valueOf(c), KeyInput.KEY_A + (c - 'A'));

            MAP.put("0", KeyInput.KEY_0); MAP.put("1", KeyInput.KEY_1); MAP.put("2", KeyInput.KEY_2);
            MAP.put("3", KeyInput.KEY_3); MAP.put("4", KeyInput.KEY_4); MAP.put("5", KeyInput.KEY_5);
            MAP.put("6", KeyInput.KEY_6); MAP.put("7", KeyInput.KEY_7); MAP.put("8", KeyInput.KEY_8);
            MAP.put("9", KeyInput.KEY_9);

            MAP.put("SPACE", KeyInput.KEY_SPACE);
            MAP.put("ESC", KeyInput.KEY_ESCAPE);
            MAP.put("ESCAPE", KeyInput.KEY_ESCAPE);
            MAP.put("ENTER", KeyInput.KEY_RETURN);
            MAP.put("RETURN", KeyInput.KEY_RETURN);
            MAP.put("TAB", KeyInput.KEY_TAB);

            MAP.put("SHIFT", KeyInput.KEY_LSHIFT);
            MAP.put("LSHIFT", KeyInput.KEY_LSHIFT);
            MAP.put("RSHIFT", KeyInput.KEY_RSHIFT);

            MAP.put("CTRL", KeyInput.KEY_LCONTROL);
            MAP.put("LCTRL", KeyInput.KEY_LCONTROL);
            MAP.put("RCTRL", KeyInput.KEY_RCONTROL);

            MAP.put("ALT", KeyInput.KEY_LMENU);
            MAP.put("LALT", KeyInput.KEY_LMENU);
            MAP.put("RALT", KeyInput.KEY_RMENU);

            MAP.put("LEFT", KeyInput.KEY_LEFT);
            MAP.put("RIGHT", KeyInput.KEY_RIGHT);
            MAP.put("UP", KeyInput.KEY_UP);
            MAP.put("DOWN", KeyInput.KEY_DOWN);

            MAP.put("F1", KeyInput.KEY_F1);
            MAP.put("F2", KeyInput.KEY_F2);
            MAP.put("F3", KeyInput.KEY_F3);
            MAP.put("F4", KeyInput.KEY_F4);
            MAP.put("F5", KeyInput.KEY_F5);
            MAP.put("F6", KeyInput.KEY_F6);
            MAP.put("F7", KeyInput.KEY_F7);
            MAP.put("F8", KeyInput.KEY_F8);
            MAP.put("F9", KeyInput.KEY_F9);
            MAP.put("F10", KeyInput.KEY_F10);
            MAP.put("F11", KeyInput.KEY_F11);
            MAP.put("F12", KeyInput.KEY_F12);
        }

        static int toKeyCode(String key) {
            if (key == null) return -1;
            String k = key.trim().toUpperCase();
            if (k.isEmpty()) return -1;
            Integer v = MAP.get(k);
            return v != null ? v : -1;
        }
    }
}