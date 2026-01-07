package org.foxesworld.kalitech.engine.modules.input;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.jme3.input.KeyInput;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class KeyboardState {

    private static final int[] EMPTY = new int[0];
    private static final int KEY_MAX = guessKeyMax();

    private static final LoadingCache<String, Integer> KEY_CODE_CACHE =
            Caffeine.newBuilder()
                    .maximumSize(256)
                    .expireAfterAccess(Duration.ofMinutes(10))
                    .build(KeyboardState::resolveKeyCode);

    private final boolean[] down = new boolean[KEY_MAX];
    private final boolean[] pressedThisFrame = new boolean[KEY_MAX];
    private final boolean[] releasedThisFrame = new boolean[KEY_MAX];

    private int[] keysDown = EMPTY;
    private int[] justPressed = EMPTY;
    private int[] justReleased = EMPTY;

    private int resolveDownCount = 0;
    private int resolvePressedCount = 0;
    private int resolveReleasedCount = 0;

    public int keyMax() {
        return down.length;
    }

    private static int[] ensureExact(int[] arr, int needed) {
        if (needed <= 0) return EMPTY;
        if (arr.length == needed) return arr;
        return new int[needed];
    }

    private static int resolveKeyCode(String raw) {
        final String key = normalizeKeyName(raw);
        if (key == null) return -1;
        final Integer v = KeyNames.MAP.get(key);
        return (v != null) ? v : -1;
    }

    public boolean keyDown(int keyCode) {
        return keyCode >= 0 && keyCode < down.length && down[keyCode];
    }

    public int keyCode(String name) {
        if (name == null) return -1;
        return KEY_CODE_CACHE.get(name);
    }

    public int[] keysDown() {
        return keysDown;
    }

    public int[] justPressed() {
        return justPressed;
    }

    public int[] justReleased() {
        return justReleased;
    }

    private static String normalizeKeyName(String raw) {
        if (raw == null) return null;
        final String k = raw.trim();
        if (k.isEmpty()) return null;
        return k.toUpperCase();
    }

    public void endFrame() {
        advanceFrame();
    }

    private static int guessKeyMax() {
        try {
            final Object v = KeyInput.class.getField("KEY_LAST").get(null);
            if (v instanceof Integer i) return i + 1;
        } catch (Throwable ignored) {
        }
        return 512;
    }

    public void onKeyEvent(int keyCode, boolean pressed) {
        if (keyCode < 0 || keyCode >= down.length) return;

        if (pressed) {
            down[keyCode] = true;
            pressedThisFrame[keyCode] = true;
        } else {
            down[keyCode] = false;
            releasedThisFrame[keyCode] = true;
        }
    }

    public boolean keyDown(String name) {
        final int code = keyCode(name);
        return code >= 0 && code < down.length && down[code];
    }

    public int[] copyPressedKeyCodes() {
        final int[] src = keysDown;
        if (src.length == 0) return EMPTY;
        final int[] out = new int[src.length];
        System.arraycopy(src, 0, out, 0, src.length);
        return out;
    }

    public void advanceFrame() {
        int downCount = 0;
        int jpCount = 0;
        int jrCount = 0;

        for (int i = 0; i < down.length; i++) {
            if (down[i]) downCount++;
            if (pressedThisFrame[i]) jpCount++;
            if (releasedThisFrame[i]) jrCount++;
        }

        keysDown = ensureExact(keysDown, downCount);
        justPressed = ensureExact(justPressed, jpCount);
        justReleased = ensureExact(justReleased, jrCount);

        resolveDownCount = downCount;
        resolvePressedCount = jpCount;
        resolveReleasedCount = jrCount;

        int id = 0, ip = 0, ir = 0;

        for (int i = 0; i < down.length; i++) {
            if (down[i]) keysDown[id++] = i;
            if (pressedThisFrame[i]) justPressed[ip++] = i;
            if (releasedThisFrame[i]) justReleased[ir++] = i;

            pressedThisFrame[i] = false;
            releasedThisFrame[i] = false;
        }
    }

    private static final class KeyNames {
        private static final Map<String, Integer> MAP = build();

        private static Map<String, Integer> build() {
            final HashMap<String, Integer> m = new HashMap<>(512);

            try {
                for (Field f : KeyInput.class.getFields()) {
                    final int mod = f.getModifiers();
                    if (!Modifier.isStatic(mod) || f.getType() != int.class) continue;

                    final String n = f.getName();
                    if (!n.startsWith("KEY_")) continue;

                    final int code = f.getInt(null);
                    final String key = n.substring(4);
                    m.put(key, code);
                }
            } catch (Throwable ignored) {
            }

            alias(m, "ESC", "ESCAPE");
            alias(m, "ENTER", "RETURN");
            alias(m, "CTRL", "LCONTROL");
            alias(m, "LCTRL", "LCONTROL");
            alias(m, "RCTRL", "RCONTROL");
            alias(m, "SHIFT", "LSHIFT");
            alias(m, "LSHIFT", "LSHIFT");
            alias(m, "RSHIFT", "RSHIFT");
            alias(m, "ALT", "LMENU");
            alias(m, "LALT", "LMENU");
            alias(m, "RALT", "RMENU");

            return Collections.unmodifiableMap(m);
        }

        private static void alias(HashMap<String, Integer> m, String a, String b) {
            final Integer v = m.get(b);
            if (v != null) m.put(a, v);
        }
    }
}