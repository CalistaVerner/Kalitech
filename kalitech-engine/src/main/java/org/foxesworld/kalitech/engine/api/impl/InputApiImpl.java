package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.input.InputManager;
import com.jme3.input.RawInputListener;
import com.jme3.input.event.*;
import com.jme3.input.KeyInput;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.interfaces.InputApi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class InputApiImpl implements InputApi {

    private static final Logger log = LogManager.getLogger(InputApiImpl.class);

    private final InputManager input;

    // key state by keyCode
    private final ConcurrentHashMap<Integer, Boolean> keyDown = new ConcurrentHashMap<>();

    // mouse buttons: bitmask (0..31)
    private final AtomicInteger mouseMask = new AtomicInteger(0);

    private volatile double mx, my;
    private volatile double mdx, mdy;
    private volatile double wheel;

    public InputApiImpl(InputManager input) {
        this.input = input;
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
        if (code <= 0) return false;
        return Boolean.TRUE.equals(keyDown.get(code));
    }

    @Override public double mouseX() { return mx; }
    @Override public double mouseY() { return my; }
    @Override public double mouseDX() { return mdx; }
    @Override public double mouseDY() { return mdy; }
    @Override public double wheelDelta() { return wheel; }

    @Override
    public boolean mouseDown(int button) {
        if (button < 0 || button >= 31) return false;
        int mask = mouseMask.get();
        return (mask & (1 << button)) != 0;
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

        @Override
        public void onKeyEvent(KeyInputEvent evt) {
            keyDown.put(evt.getKeyCode(), evt.isPressed());
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

        @Override public void onJoyAxisEvent(JoyAxisEvent evt) {}
        @Override public void onJoyButtonEvent(JoyButtonEvent evt) {}
        @Override public void onTouchEvent(TouchEvent evt) {}
    }

    /** Small, deterministic name-to-code mapper for JS. Expand as needed. */
    private static final class KeyNames {
        private static final Map<String, Integer> MAP = new ConcurrentHashMap<>();

        static {
            // letters
            for (char c = 'A'; c <= 'Z'; c++) MAP.put(String.valueOf(c), KeyInput.KEY_A + (c - 'A'));
            // digits
            MAP.put("0", KeyInput.KEY_0); MAP.put("1", KeyInput.KEY_1); MAP.put("2", KeyInput.KEY_2);
            MAP.put("3", KeyInput.KEY_3); MAP.put("4", KeyInput.KEY_4); MAP.put("5", KeyInput.KEY_5);
            MAP.put("6", KeyInput.KEY_6); MAP.put("7", KeyInput.KEY_7); MAP.put("8", KeyInput.KEY_8);
            MAP.put("9", KeyInput.KEY_9);

            // common specials
            MAP.put("SPACE", KeyInput.KEY_SPACE);
            MAP.put("ESC", KeyInput.KEY_ESCAPE);
            MAP.put("ENTER", KeyInput.KEY_RETURN);
            MAP.put("TAB", KeyInput.KEY_TAB);
            MAP.put("SHIFT", KeyInput.KEY_LSHIFT);
            MAP.put("CTRL", KeyInput.KEY_LCONTROL);
            MAP.put("ALT", KeyInput.KEY_LMENU);

            // arrows
            MAP.put("LEFT", KeyInput.KEY_LEFT);
            MAP.put("RIGHT", KeyInput.KEY_RIGHT);
            MAP.put("UP", KeyInput.KEY_UP);
            MAP.put("DOWN", KeyInput.KEY_DOWN);

            // function keys (few)
            MAP.put("F1", KeyInput.KEY_F1);
            MAP.put("F2", KeyInput.KEY_F2);
            MAP.put("F3", KeyInput.KEY_F3);
            MAP.put("F4", KeyInput.KEY_F4);
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