/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.asset.AssetKey
 *  com.jme3.asset.AssetManager
 *  com.jme3.math.Vector3f
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.sound;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.sound.FloatRange;
import org.foxesworld.kalitech.engine.modules.sound.SoundDef;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class SoundParsers {
    private static final Logger log = LogManager.getLogger(SoundParsers.class);

    private SoundParsers() {
    }

    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean assetExists(AssetManager assets, String path) {
        if (!SoundParsers.hasText(path)) {
            return false;
        }
        return assets.locateAsset(new AssetKey(path)) != null;
    }

    private static boolean requireAsset(AssetManager assets, String path, String field) {
        if (!SoundParsers.hasText(path)) {
            log.warn("[sound] missing asset path for '{}'", (Object)field);
            return false;
        }
        if (!SoundParsers.assetExists(assets, path)) {
            log.warn("[sound] asset not found for '{}': {}", (Object)field, (Object)path);
            return false;
        }
        return true;
    }

    public static AudioNode createFromCfg(AssetManager assets, LuaValueRef cfg) {
        Vector3f pos;
        AudioNode node;
        float volume = (float)LuaCfg.num((LuaValueRef)cfg, (String)"volume", (double)1.0);
        float pitch = (float)LuaCfg.num((LuaValueRef)cfg, (String)"pitch", (double)1.0);
        boolean looping = LuaCfg.bool((LuaValueRef)cfg, (String)"looping", (boolean)false);
        boolean is3D = LuaCfg.bool((LuaValueRef)cfg, (String)"is3D", (boolean)false);
        AudioData.DataType type = SoundParsers.parseType(LuaCfg.str((LuaValueRef)cfg, (String)"type", (String)"buffer"));
        String leftFile = LuaCfg.str((LuaValueRef)cfg, (String)"leftFile", (String)"");
        String rightFile = LuaCfg.str((LuaValueRef)cfg, (String)"rightFile", (String)"");
        float separation = (float)LuaCfg.num((LuaValueRef)cfg, (String)"separation", (double)0.2);
        String soundFile = LuaCfg.str((LuaValueRef)cfg, (String)"src", (String)LuaCfg.str((LuaValueRef)cfg, (String)"soundFile", (String)""));
        if (SoundParsers.hasText(leftFile) && SoundParsers.hasText(rightFile)) {
            String field;
            if (!SoundParsers.requireAsset(assets, leftFile, "leftFile") || !SoundParsers.requireAsset(assets, rightFile, "rightFile")) {
                return null;
            }
            String f = SoundParsers.hasText(soundFile) ? soundFile : leftFile;
            String string = field = SoundParsers.hasText(soundFile) ? "src" : "leftFile";
            if (!SoundParsers.requireAsset(assets, f, field)) {
                return null;
            }
            node = new AudioNode(assets, f, type);
            node.setPositional(false);
        } else {
            if (!SoundParsers.hasText(soundFile)) {
                log.warn("[sound] createFromCfg: missing 'src' (or leftFile+rightFile)");
                return null;
            }
            if (!SoundParsers.requireAsset(assets, soundFile, "src")) {
                return null;
            }
            node = new AudioNode(assets, soundFile, type);
            node.setPositional(is3D);
        }
        node.setVolume(volume);
        node.setPitch(pitch);
        node.setLooping(looping);
        if (is3D && (pos = SoundParsers.parsePos(cfg)) != null) {
            node.setLocalTranslation(pos);
        }
        return node;
    }

    public static void collectBank(LuaValueRef v, String prefix, Map<String, List<SoundDef>> out) {
        if (v == null || v.isNull()) {
            return;
        }
        if (v.hasArrayElements()) {
            String key = prefix;
            if (key.endsWith(".")) {
                key = key.substring(0, key.length() - 1);
            }
            if (!SoundParsers.hasText(key)) {
                return;
            }
            int n = (int)v.getArraySize();
            if (n <= 0) {
                return;
            }
            List list = out.computeIfAbsent(key, k -> new ArrayList(n));
            for (int i = 0; i < n; ++i) {
                LuaValueRef el = v.getArrayElement((long)i);
                SoundDef d = SoundParsers.parseSoundDef(el);
                if (d == null) continue;
                list.add(d);
            }
            return;
        }
        if (v.hasMembers()) {
            for (String k2 : v.getMemberKeys()) {
                LuaValueRef child = v.getMember(k2);
                SoundParsers.collectBank(child, prefix + k2 + ".", out);
            }
        }
    }

    public static AudioNode createPrototype(AssetManager assets, SoundDef def) {
        AudioNode node;
        if (def.isStereo()) {
            if (!SoundParsers.requireAsset(assets, def.leftFile, "leftFile") || !SoundParsers.requireAsset(assets, def.rightFile, "rightFile")) {
                return null;
            }
            node = new AudioNode(assets, def.leftFile, def.type);
            node.setPositional(false);
        } else {
            if (!SoundParsers.requireAsset(assets, def.src, "src")) {
                return null;
            }
            node = new AudioNode(assets, def.src, def.type);
            node.setPositional(def.is3D);
        }
        SoundParsers.applyDef(node, def);
        return node;
    }

    public static SoundDef parseSoundDef(LuaValueRef v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isString()) {
            String src = v.asString();
            if (!SoundParsers.hasText(src)) {
                return null;
            }
            return SoundDef.simple(src);
        }
        if (!v.hasMembers()) {
            return null;
        }
        String src = LuaCfg.str((LuaValueRef)v, (String)"src", (String)LuaCfg.str((LuaValueRef)v, (String)"soundFile", (String)""));
        String left = LuaCfg.str((LuaValueRef)v, (String)"leftFile", (String)"");
        String right = LuaCfg.str((LuaValueRef)v, (String)"rightFile", (String)"");
        String typeStr = LuaCfg.str((LuaValueRef)v, (String)"type", (String)"buffer");
        boolean is3D = LuaCfg.bool((LuaValueRef)v, (String)"is3D", (boolean)false);
        boolean looping = LuaCfg.bool((LuaValueRef)v, (String)"looping", (boolean)false);
        float separation = (float)LuaCfg.num((LuaValueRef)v, (String)"separation", (double)0.2);
        FloatRange vol = FloatRange.parse(v, "volume", 1.0f, 0.0f, 32.0f);
        FloatRange pitch = FloatRange.parse(v, "pitch", 1.0f, 0.25f, 4.0f);
        if (SoundParsers.hasText(left) && SoundParsers.hasText(right)) {
            return SoundDef.stereo(left, right, SoundParsers.parseType(typeStr), separation, is3D, looping, vol, pitch);
        }
        if (!SoundParsers.hasText(src)) {
            return null;
        }
        return SoundDef.mono(src, SoundParsers.parseType(typeStr), is3D, looping, vol, pitch);
    }

    public static void applyDef(AudioNode node, SoundDef def, long seed) {
        node.setLooping(def.looping);
        node.setPositional(def.is3D);
        float v = def.volume.sample(seed, 1);
        float p = def.pitch.sample(seed, 2);
        node.setVolume(v);
        node.setPitch(p);
    }

    public static void applyDef(AudioNode node, SoundDef def) {
        SoundParsers.applyDef(node, def, 0L);
    }

    public static void applyOverrides(AudioNode node, LuaValueRef cfg) {
        Vector3f pos;
        if (cfg == null || cfg.isNull()) {
            return;
        }
        if (LuaCfg.has((LuaValueRef)cfg, (String)"looping")) {
            node.setLooping(LuaCfg.bool((LuaValueRef)cfg, (String)"looping", (boolean)node.isLooping()));
        }
        if (LuaCfg.has((LuaValueRef)cfg, (String)"is3D")) {
            node.setPositional(LuaCfg.bool((LuaValueRef)cfg, (String)"is3D", (boolean)node.isPositional()));
        }
        if (LuaCfg.has((LuaValueRef)cfg, (String)"volume")) {
            node.setVolume((float)LuaCfg.num((LuaValueRef)cfg, (String)"volume", (double)node.getVolume()));
        }
        if (LuaCfg.has((LuaValueRef)cfg, (String)"pitch")) {
            node.setPitch((float)LuaCfg.num((LuaValueRef)cfg, (String)"pitch", (double)node.getPitch()));
        }
        if ((pos = SoundParsers.parsePos(cfg)) != null) {
            node.setPositional(true);
            node.setLocalTranslation(pos);
        }
    }

    public static Vector3f parsePos(LuaValueRef cfg) {
        if (cfg == null) {
            return null;
        }
        LuaValueRef p = null;
        if (LuaCfg.has((LuaValueRef)cfg, (String)"pos")) {
            p = LuaCfg.member((LuaValueRef)cfg, (String)"pos");
        } else if (LuaCfg.has((LuaValueRef)cfg, (String)"position")) {
            p = LuaCfg.member((LuaValueRef)cfg, (String)"position");
        }
        if (p != null && p.hasMembers()) {
            float x = (float)LuaCfg.num((LuaValueRef)p, (String)"x", (double)0.0);
            float y = (float)LuaCfg.num((LuaValueRef)p, (String)"y", (double)0.0);
            float z = (float)LuaCfg.num((LuaValueRef)p, (String)"z", (double)0.0);
            return new Vector3f(x, y, z);
        }
        if (LuaCfg.has((LuaValueRef)cfg, (String)"x") || LuaCfg.has((LuaValueRef)cfg, (String)"y") || LuaCfg.has((LuaValueRef)cfg, (String)"z")) {
            float x = (float)LuaCfg.num((LuaValueRef)cfg, (String)"x", (double)0.0);
            float y = (float)LuaCfg.num((LuaValueRef)cfg, (String)"y", (double)0.0);
            float z = (float)LuaCfg.num((LuaValueRef)cfg, (String)"z", (double)0.0);
            return new Vector3f(x, y, z);
        }
        return null;
    }

    public static AudioData.DataType parseType(String s) {
        String v;
        if (s == null) {
            return AudioData.DataType.Buffer;
        }
        return switch (v = s.trim().toLowerCase()) {
            case "stream" -> AudioData.DataType.Stream;
            case "buffer" -> AudioData.DataType.Buffer;
            default -> AudioData.DataType.Buffer;
        };
    }
}

