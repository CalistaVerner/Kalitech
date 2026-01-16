// FILE: org/foxesworld/kalitech/engine/modules/sound/SoundParsers.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.sound;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.audio.SpatialStereoAudioNode;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.*;

public final class SoundParsers {

    private SoundParsers() {
    }

    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    public static AudioNode createFromCfg(AssetManager assets, Value cfg) {
        float volume = (float) num(cfg, "volume", 1.0);
        float pitch = (float) num(cfg, "pitch", 1.0);
        boolean looping = bool(cfg, "looping", false);
        boolean is3D = bool(cfg, "is3D", false);

        AudioData.DataType type = parseType(str(cfg, "type", "buffer"));

        String leftFile = str(cfg, "leftFile", "");
        String rightFile = str(cfg, "rightFile", "");
        float separation = (float) num(cfg, "separation", 0.20);

        String soundFile = str(cfg, "src", str(cfg, "soundFile", ""));

        final AudioNode node;
        if (hasText(leftFile) && hasText(rightFile)) {
            if (!is3D) {
                String f = hasText(soundFile) ? soundFile : leftFile;
                node = new AudioNode(assets, f, type);
                node.setPositional(false);
            } else {
                SpatialStereoAudioNode s = new SpatialStereoAudioNode(assets, leftFile, rightFile, type);
                s.setSeparation(separation);
                node = s;
                node.setPositional(true);
            }
        } else {
            if (!hasText(soundFile)) {
                throw new IllegalArgumentException("sound.create(cfg): src (or leftFile+rightFile) is required");
            }
            node = new AudioNode(assets, soundFile, type);
            node.setPositional(is3D);
        }

        node.setVolume(volume);
        node.setPitch(pitch);
        node.setLooping(looping);

        if (is3D) {
            Vector3f pos = parsePos(cfg);
            if (pos != null) node.setLocalTranslation(pos);
        }

        return node;
    }

    public static void collectBank(Value v, String prefix, Map<String, List<SoundDef>> out) {
        if (v == null || v.isNull()) return;

        if (v.hasArrayElements()) {
            String key = prefix;
            if (key.endsWith(".")) key = key.substring(0, key.length() - 1);
            if (!hasText(key)) return;

            int n = (int) v.getArraySize();
            if (n <= 0) return;

            List<SoundDef> list = out.computeIfAbsent(key, k -> new ArrayList<>(n));
            for (int i = 0; i < n; i++) {
                Value el = v.getArrayElement(i);
                SoundDef d = parseSoundDef(el);
                if (d != null) list.add(d);
            }
            return;
        }

        if (v.hasMembers()) {
            for (String k : v.getMemberKeys()) {
                Value child = v.getMember(k);
                collectBank(child, prefix + k + ".", out);
            }
        }
    }

    public static AudioNode createPrototype(AssetManager assets, SoundDef def) {
        final AudioNode node;

        if (def.isStereo()) {
            if (!def.is3D) {
                node = new AudioNode(assets, def.leftFile, def.type);
                node.setPositional(false);
            } else {
                SpatialStereoAudioNode s = new SpatialStereoAudioNode(assets, def.leftFile, def.rightFile, def.type);
                s.setSeparation(def.separation);
                node = s;
                node.setPositional(true);
            }
        } else {
            node = new AudioNode(assets, def.src, def.type);
            node.setPositional(def.is3D);
        }

        applyDef(node, def);
        return node;
    }

    public static SoundDef parseSoundDef(Value v) {
        if (v == null || v.isNull()) return null;

        if (v.isString()) {
            String src = v.asString();
            if (!hasText(src)) return null;
            return SoundDef.simple(src);
        }

        if (!v.hasMembers()) return null;

        String src = str(v, "src", str(v, "soundFile", ""));
        String left = str(v, "leftFile", "");
        String right = str(v, "rightFile", "");
        String typeStr = str(v, "type", "buffer");

        boolean is3D = bool(v, "is3D", false);
        boolean looping = bool(v, "looping", false);
        float separation = (float) num(v, "separation", 0.20);

        FloatRange vol = FloatRange.parse(v, "volume", 1.0f, 0.0f, 32.0f);
        FloatRange pitch = FloatRange.parse(v, "pitch", 1.0f, 0.25f, 4.0f);

        if (hasText(left) && hasText(right)) {
            return SoundDef.stereo(left, right, parseType(typeStr), separation, is3D, looping, vol, pitch);
        }

        if (!hasText(src)) return null;
        return SoundDef.mono(src, parseType(typeStr), is3D, looping, vol, pitch);
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
        applyDef(node, def, 0L);
    }

    public static void applyOverrides(AudioNode node, Value cfg) {
        if (cfg == null || cfg.isNull()) return;

        if (has(cfg, "looping")) node.setLooping(bool(cfg, "looping", node.isLooping()));
        if (has(cfg, "is3D")) node.setPositional(bool(cfg, "is3D", node.isPositional()));

        if (has(cfg, "volume")) node.setVolume((float) num(cfg, "volume", node.getVolume()));
        if (has(cfg, "pitch")) node.setPitch((float) num(cfg, "pitch", node.getPitch()));

        Vector3f pos = parsePos(cfg);
        if (pos != null) {
            node.setPositional(true);
            node.setLocalTranslation(pos);
        }
    }

    public static Vector3f parsePos(Value cfg) {
        if (cfg == null) return null;

        Value p = null;
        if (has(cfg, "pos")) p = member(cfg, "pos");
        else if (has(cfg, "position")) p = member(cfg, "position");

        if (p != null && p.hasMembers()) {
            float x = (float) num(p, "x", 0.0);
            float y = (float) num(p, "y", 0.0);
            float z = (float) num(p, "z", 0.0);
            return new Vector3f(x, y, z);
        }

        if (has(cfg, "x") || has(cfg, "y") || has(cfg, "z")) {
            float x = (float) num(cfg, "x", 0.0);
            float y = (float) num(cfg, "y", 0.0);
            float z = (float) num(cfg, "z", 0.0);
            return new Vector3f(x, y, z);
        }

        return null;
    }

    public static AudioData.DataType parseType(String s) {
        if (s == null) return AudioData.DataType.Buffer;
        String v = s.trim().toLowerCase();
        return switch (v) {
            case "stream" -> AudioData.DataType.Stream;
            case "buffer" -> AudioData.DataType.Buffer;
            default -> AudioData.DataType.Buffer;
        };
    }
}
