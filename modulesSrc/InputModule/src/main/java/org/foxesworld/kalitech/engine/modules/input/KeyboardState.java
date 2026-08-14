/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.benmanes.caffeine.cache.Caffeine
 *  com.github.benmanes.caffeine.cache.LoadingCache
 *  com.jme3.input.KeyInput
 */
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
    private static final int KEY_MAX = KeyboardState.guessKeyMax();
    private static final LoadingCache<String, Integer> KEY_CODE_CACHE = Caffeine.newBuilder().maximumSize(256L).expireAfterAccess(Duration.ofMinutes(10L)).build(KeyboardState::resolveKeyCode);
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
        return this.down.length;
    }

    private static int[] ensureExact(int[] arr, int needed) {
        if (needed <= 0) {
            return EMPTY;
        }
        if (arr.length == needed) {
            return arr;
        }
        return new int[needed];
    }

    private static int resolveKeyCode(String raw) {
        String key = KeyboardState.normalizeKeyName(raw);
        if (key == null) {
            return -1;
        }
        Integer v = KeyNames.MAP.get(key);
        return v != null ? v : -1;
    }

    public boolean keyDown(int keyCode) {
        return keyCode >= 0 && keyCode < this.down.length && this.down[keyCode];
    }

    public int keyCode(String name) {
        if (name == null) {
            return -1;
        }
        return (Integer)KEY_CODE_CACHE.get(name);
    }

    public int[] keysDown() {
        return this.keysDown;
    }

    public int[] justPressed() {
        return this.justPressed;
    }

    public int[] justReleased() {
        return this.justReleased;
    }

    private static String normalizeKeyName(String raw) {
        if (raw == null) {
            return null;
        }
        String k = raw.trim();
        if (k.isEmpty()) {
            return null;
        }
        return k.toUpperCase();
    }

    public void endFrame() {
        this.advanceFrame();
    }

    private static int guessKeyMax() {
        try {
            Object v = KeyInput.class.getField("KEY_LAST").get(null);
            if (v instanceof Integer) {
                Integer i = (Integer)v;
                return i + 1;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return 512;
    }

    public void onKeyEvent(int keyCode, boolean pressed) {
        if (keyCode < 0 || keyCode >= this.down.length) {
            return;
        }
        if (pressed) {
            this.down[keyCode] = true;
            this.pressedThisFrame[keyCode] = true;
        } else {
            this.down[keyCode] = false;
            this.releasedThisFrame[keyCode] = true;
        }
    }

    public boolean keyDown(String name) {
        int code = this.keyCode(name);
        return code >= 0 && code < this.down.length && this.down[code];
    }

    public int[] copyPressedKeyCodes() {
        int[] src = this.keysDown;
        if (src.length == 0) {
            return EMPTY;
        }
        int[] out = new int[src.length];
        System.arraycopy(src, 0, out, 0, src.length);
        return out;
    }

    public void advanceFrame() {
        int downCount = 0;
        int jpCount = 0;
        int jrCount = 0;
        for (int i = 0; i < this.down.length; ++i) {
            if (this.down[i]) {
                ++downCount;
            }
            if (this.pressedThisFrame[i]) {
                ++jpCount;
            }
            if (!this.releasedThisFrame[i]) continue;
            ++jrCount;
        }
        this.keysDown = KeyboardState.ensureExact(this.keysDown, downCount);
        this.justPressed = KeyboardState.ensureExact(this.justPressed, jpCount);
        this.justReleased = KeyboardState.ensureExact(this.justReleased, jrCount);
        this.resolveDownCount = downCount;
        this.resolvePressedCount = jpCount;
        this.resolveReleasedCount = jrCount;
        int id = 0;
        int ip = 0;
        int ir = 0;
        for (int i = 0; i < this.down.length; ++i) {
            if (this.down[i]) {
                this.keysDown[id++] = i;
            }
            if (this.pressedThisFrame[i]) {
                this.justPressed[ip++] = i;
            }
            if (this.releasedThisFrame[i]) {
                this.justReleased[ir++] = i;
            }
            this.pressedThisFrame[i] = false;
            this.releasedThisFrame[i] = false;
        }
    }

    private static final class KeyNames {
        private static final Map<String, Integer> MAP = KeyNames.build();

        private KeyNames() {
        }

        private static Map<String, Integer> build() {
            HashMap<String, Integer> m = new HashMap<String, Integer>(512);
            try {
                for (Field f : KeyInput.class.getFields()) {
                    String n;
                    int mod = f.getModifiers();
                    if (!Modifier.isStatic(mod) || f.getType() != Integer.TYPE || !(n = f.getName()).startsWith("KEY_")) continue;
                    int code = f.getInt(null);
                    String key = n.substring(4);
                    m.put(key, code);
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            KeyNames.alias(m, "ESC", "ESCAPE");
            KeyNames.alias(m, "ENTER", "RETURN");
            KeyNames.alias(m, "CTRL", "LCONTROL");
            KeyNames.alias(m, "LCTRL", "LCONTROL");
            KeyNames.alias(m, "RCTRL", "RCONTROL");
            KeyNames.alias(m, "SHIFT", "LSHIFT");
            KeyNames.alias(m, "LSHIFT", "LSHIFT");
            KeyNames.alias(m, "RSHIFT", "RSHIFT");
            KeyNames.alias(m, "ALT", "LMENU");
            KeyNames.alias(m, "LALT", "LMENU");
            KeyNames.alias(m, "RALT", "RMENU");
            return Collections.unmodifiableMap(m);
        }

        private static void alias(HashMap<String, Integer> m, String a, String b) {
            Integer v = m.get(b);
            if (v != null) {
                m.put(a, v);
            }
        }
    }
}

