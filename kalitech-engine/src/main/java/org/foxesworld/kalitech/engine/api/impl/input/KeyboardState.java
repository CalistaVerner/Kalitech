package org.foxesworld.kalitech.engine.api.impl.input;

// Author: Calista Verner

import com.jme3.input.KeyInput;

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

    public int keyCode(String name) {
        return KeyNames.toKeyCode(name);
    }

    public int[] keysDown() { return keysDown; }
    public int[] justPressed() { return justPressed; }
    public int[] justReleased() { return justReleased; }

    // ---------------- frame finalize ----------------

    /**
     * Must be called once per frame (from InputApiImpl.endFrame()).
     * Computes transitions and freezes snapshot arrays.
     */
    public void endFrame() {
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
            for (char c = 'A'; c <= 'Z'; c++) {
                MAP.put(String.valueOf(c), KeyInput.KEY_A + (c - 'A'));
            }

            MAP.put("0", KeyInput.KEY_0); MAP.put("1", KeyInput.KEY_1);
            MAP.put("2", KeyInput.KEY_2); MAP.put("3", KeyInput.KEY_3);
            MAP.put("4", KeyInput.KEY_4); MAP.put("5", KeyInput.KEY_5);
            MAP.put("6", KeyInput.KEY_6); MAP.put("7", KeyInput.KEY_7);
            MAP.put("8", KeyInput.KEY_8); MAP.put("9", KeyInput.KEY_9);

            MAP.put("SPACE", KeyInput.KEY_SPACE);
            MAP.put("ESC", KeyInput.KEY_ESCAPE);
            MAP.put("ESCAPE", KeyInput.KEY_ESCAPE);
            MAP.put("ENTER", KeyInput.KEY_RETURN);
            MAP.put("TAB", KeyInput.KEY_TAB);

            MAP.put("SHIFT", KeyInput.KEY_LSHIFT);
            MAP.put("CTRL", KeyInput.KEY_LCONTROL);
            MAP.put("ALT", KeyInput.KEY_LMENU);

            MAP.put("LEFT", KeyInput.KEY_LEFT);
            MAP.put("RIGHT", KeyInput.KEY_RIGHT);
            MAP.put("UP", KeyInput.KEY_UP);
            MAP.put("DOWN", KeyInput.KEY_DOWN);

            MAP.put("F1", KeyInput.KEY_F1); MAP.put("F2", KeyInput.KEY_F2);
            MAP.put("F3", KeyInput.KEY_F3); MAP.put("F4", KeyInput.KEY_F4);
            MAP.put("F5", KeyInput.KEY_F5); MAP.put("F6", KeyInput.KEY_F6);
            MAP.put("F7", KeyInput.KEY_F7); MAP.put("F8", KeyInput.KEY_F8);
            MAP.put("F9", KeyInput.KEY_F9); MAP.put("F10", KeyInput.KEY_F10);
            MAP.put("F11", KeyInput.KEY_F11); MAP.put("F12", KeyInput.KEY_F12);
        }

        static int toKeyCode(String key) {
            if (key == null) return -1;
            String k = key.trim().toUpperCase();
            if (k.isEmpty()) return -1;
            return MAP.getOrDefault(k, -1);
        }
    }

    /**
     * Immutable list of currently pressed key codes (KeyInput codes).
     * Used for per-frame InputSnapshot (keysDown).
     *
     * @return int[] of pressed key codes, never null
     */
    public int[] copyPressedKeyCodes() {
        int count = 0;

        // first pass: count
        for (boolean d : down) {
            if (d) count++;
        }

        if (count == 0) {
            return new int[0];
        }

        // second pass: fill
        int[] out = new int[count];
        int i = 0;
        for (int keyCode = 0; keyCode < down.length; keyCode++) {
            if (down[keyCode]) {
                out[i++] = keyCode;
            }
        }

        return out;
    }

}
