package org.foxesworld.kalitech.engine.api.impl.input;

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

    private static final int KEY_MAX = guessKeyMax();

    private static final LoadingCache<String, Integer> KEY_CODE_CACHE =
            Caffeine.newBuilder()
                    .maximumSize(256)
                    .expireAfterAccess(Duration.ofMinutes(10))
                    .build(KeyboardState::resolveKeyCode);

    private final boolean[] down = new boolean[KEY_MAX];
    private final boolean[] prevDown = new boolean[KEY_MAX];

    private int[] justPressed = new int[0];
    private int[] justReleased = new int[0];
    private int[] keysDown = new int[0];

    public int keyMax() {
        return down.length;
    }

    public void onKeyEvent(int keyCode, boolean pressed) {
        if (keyCode >= 0 && keyCode < down.length) {
            down[keyCode] = pressed;
        }
    }

    public boolean keyDown(String name) {
        int code = keyCode(name);
        return code >= 0 && code < down.length && down[code];
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

    public int[] copyPressedKeyCodes() {
        int[] src = keysDown;
        if (src.length == 0) return new int[0];
        int[] out = new int[src.length];
        System.arraycopy(src, 0, out, 0, src.length);
        return out;
    }

    public void endFrame() {
        advanceFrame();
    }

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

    private static int resolveKeyCode(String raw) {
        String key = normalizeKeyName(raw);
        if (key == null) return -1;
        Integer v = KeyNames.MAP.get(key);
        return v != null ? v : -1;
    }

    private static String normalizeKeyName(String raw) {
        if (raw == null) return null;
        String k = raw.trim();
        if (k.isEmpty()) return null;
        return k.toUpperCase();
    }

    private static int guessKeyMax() {
        try {
            Object v = KeyInput.class.getField("KEY_LAST").get(null);
            if (v instanceof Integer) return ((Integer) v) + 1;
        } catch (Exception ignored) {
        }
        return 512;
    }

    private static final class KeyNames {
        private static final Map<String, Integer> MAP = build();

        private static Map<String, Integer> build() {
            HashMap<String, Integer> m = new HashMap<>(512);

            try {
                for (Field f : KeyInput.class.getFields()) {
                    int mod = f.getModifiers();
                    if (!Modifier.isStatic(mod) || f.getType() != int.class) continue;

                    String n = f.getName();
                    if (!n.startsWith("KEY_")) continue;

                    int code = f.getInt(null);
                    String key = n.substring(4);
                    m.put(key, code);
                }
            } catch (Exception ignored) {
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
            Integer v = m.get(b);
            if (v != null) m.put(a, v);
        }
    }
}