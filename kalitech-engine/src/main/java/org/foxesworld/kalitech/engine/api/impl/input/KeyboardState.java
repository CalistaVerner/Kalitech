package org.foxesworld.kalitech.engine.api.impl.input;

// Author: Calista Verner

import com.jme3.input.KeyInput;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keyboard state (AAA++):
 *  - tracks down / justPressed / justReleased
 *  - exposes keyCode(name) so JS never hardcodes numbers
 */
public final class KeyboardState {

    private static final int KEY_MAX = guessKeyMax();

    private final boolean[] down = new boolean[KEY_MAX];
    private final boolean[] prevDown = new boolean[KEY_MAX];

    // cached per-frame
    private int[] justPressed = new int[0];
    private int[] justReleased = new int[0];
    private int[] keysDown = new int[0];

    public int keyMax() { return down.length; }

    // ---------------- raw feed ----------------

    public void onKeyEvent(int keyCode, boolean pressed) {
        if (keyCode >= 0 && keyCode < down.length) {
            down[keyCode] = pressed;
        }
    }

    // ---------------- queries ----------------

    public boolean keyDown(String name) {
        int code = KeyNames.toKeyCode(name);
        if (code < 0 || code >= down.length) return false;
        return down[code];
    }

    /** Fast numeric query (used from Java without name lookup). */
    public boolean keyDown(int keyCode) {
        return keyCode >= 0 && keyCode < down.length && down[keyCode];
    }

    public int keyCode(String name) {
        return KeyNames.toKeyCode(name);
    }

    public int[] keysDown() { return keysDown; }
    public int[] justPressed() { return justPressed; }
    public int[] justReleased() { return justReleased; }

    /**
     * Snapshot-friendly copy of pressed key codes.
     *
     * NOTE: returns a new array each call (for snapshot immutability).
     */
    public int[] copyPressedKeyCodes() {
        int[] src = keysDown;
        if (src.length == 0) return new int[0];
        int[] out = new int[src.length];
        System.arraycopy(src, 0, out, 0, src.length);
        return out;
    }

    // ---------------- frame finalize ----------------

    /**
     * Back-compat name.
     */
    public void endFrame() {
        advanceFrame();
    }

    // ---------------- helpers ----------------

    private static int guessKeyMax() {
        try {
            Object v = KeyInput.class.getField("KEY_LAST").get(null);
            if (v instanceof Integer) return ((Integer) v) + 1;
        } catch (Exception ignored) {}
        return 512;
    }

    // ---------------- name → code ----------------

    private static final class KeyNames {
        private static final Map<String, Integer> MAP = new ConcurrentHashMap<>();

        static {
            // Auto-load ALL KeyInput.KEY_* constants correctly
            try {
                for (Field f : KeyInput.class.getFields()) {
                    int mod = f.getModifiers();
                    if (!Modifier.isStatic(mod) || f.getType() != int.class) continue;

                    String n = f.getName(); // e.g. "KEY_W"
                    if (!n.startsWith("KEY_")) continue;

                    int code = f.getInt(null);
                    String key = n.substring(4); // "W"
                    MAP.put(key, code);
                }
            } catch (Exception ignored) {}

            // Friendly aliases
            alias("ESC", "ESCAPE");
            alias("ENTER", "RETURN");
            alias("CTRL", "LCONTROL");
            alias("LCTRL", "LCONTROL");
            alias("RCTRL", "RCONTROL");
            alias("SHIFT", "LSHIFT");
            alias("LSHIFT", "LSHIFT");
            alias("RSHIFT", "RSHIFT");
            alias("ALT", "LMENU");
            alias("LALT", "LMENU");
            alias("RALT", "RMENU");
        }

        private static void alias(String a, String b) {
            Integer v = MAP.get(b);
            if (v != null) MAP.put(a, v);
        }

        static int toKeyCode(String key) {
            if (key == null) return -1;
            String k = key.trim().toUpperCase();
            if (k.isEmpty()) return -1;
            Integer v = MAP.get(k);
            return v != null ? v : -1;
        }
    }

    /**
     * Main per-frame computation (call once per snapshot).
     */
    public void advanceFrame() {
        int downCount = 0;
        int jpCount = 0;
        int jrCount = 0;

        for (int i = 0; i < down.length; i++) {
            boolean d = down[i];
            boolean p = prevDown[i];

            if (d) downCount++;
            if (d && !p) jpCount++;
            if (!d && p) jrCount++;
        }

        int[] kd = downCount == 0 ? new int[0] : new int[downCount];
        int[] jp = jpCount == 0 ? new int[0] : new int[jpCount];
        int[] jr = jrCount == 0 ? new int[0] : new int[jrCount];

        int id = 0, ip = 0, ir = 0;

        for (int i = 0; i < down.length; i++) {
            boolean d = down[i];
            boolean p = prevDown[i];

            if (d) kd[id++] = i;
            if (d && !p) jp[ip++] = i;
            if (!d && p) jr[ir++] = i;

            prevDown[i] = d;
        }

        keysDown = kd;
        justPressed = jp;
        justReleased = jr;
    }
}