// FILE: org/foxesworld/kalitech/engine/api/impl/SoundApiImpl.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.math.Vector3f;
import org.foxesworld.kalitech.audio.SpatialStereoAudioNode;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SoundApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.modules.render.RenderCfg;
import org.foxesworld.kalitech.engine.util.LongHashMap;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.*;

public final class SoundApiImpl extends AbstractApiModule implements SoundApi {

    private static final String USERDATA_SOUND_ID = "kalitech.soundId";

    private EngineApiImpl engine;
    private AssetManager assetManager;

    private final Map<String, List<SoundDef>> bank = new ConcurrentHashMap<>();
    private final Map<String, AudioNode> prototypes = new ConcurrentHashMap<>();

    /**
     * Cache of created AudioNode instances by long id (allocation-light).
     * Not thread-safe: expected to be used on main thread like other engine APIs.
     */
    private final LongHashMap<AudioNode> nodeCache = new LongHashMap<>(256);

    private final AtomicLong nextSoundId = new AtomicLong(1L);

    public SoundApiImpl() {
        super("sound", "Sound", "1.1.0");
    }

    public SoundApiImpl(EngineApiImpl engineApi) {
        this();
        bind(engineApi);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static Vector3f parsePos(Value cfg) {
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

    private static void collectBank(Value v, String prefix, Map<String, List<SoundDef>> out) {
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

    private static AudioData.DataType parseType(String s) {
        if (s == null) return AudioData.DataType.Buffer;
        String v = s.trim().toLowerCase();
        return switch (v) {
            case "stream" -> AudioData.DataType.Stream;
            case "buffer" -> AudioData.DataType.Buffer;
            default -> AudioData.DataType.Buffer;
        };
    }

    private static SoundDef parseSoundDef(Value v) {
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

    private static void applyDef(AudioNode node, SoundDef def) {
        node.setLooping(def.looping);
        node.setPositional(def.is3D);

        float v = def.volume.sample();
        float p = def.pitch.sample();

        node.setVolume(v);
        node.setPitch(p);
    }

    // ------------------------------------------------------------
    // Legacy create/play API (kept)
    // ------------------------------------------------------------

    private static void applyOverrides(AudioNode node, Value cfg) {
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

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        bind(ctx.engine);
    }

    private void bind(EngineApiImpl engineApi) {
        this.engine = Objects.requireNonNull(engineApi, "engine");
        this.assetManager = engineApi.getApp().getAssetManager();
    }

    private long cacheNode(AudioNode node) {
        long id = nextSoundId.getAndIncrement();
        nodeCache.put(id, node);
        node.setUserData(USERDATA_SOUND_ID, id);
        return id;
    }

    @HostAccess.Export
    @Override
    public AudioNode create(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("sound.create(cfg): cfg is required");

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
                node = new AudioNode(assetManager, f, type);
                node.setPositional(false);
            } else {
                SpatialStereoAudioNode s = new SpatialStereoAudioNode(assetManager, leftFile, rightFile, type);
                s.setSeparation(separation);
                node = s;
                node.setPositional(true);
            }
        } else {
            if (!hasText(soundFile)) {
                throw new IllegalArgumentException("sound.create(cfg): src (or leftFile+rightFile) is required");
            }
            node = new AudioNode(assetManager, soundFile, type);
            node.setPositional(is3D);
        }

        node.setVolume(volume);
        node.setPitch(pitch);
        node.setLooping(looping);

        if (is3D) {
            Vector3f pos = parsePos(cfg);
            if (pos != null) node.setLocalTranslation(pos);
        }

        cacheNode(node);
        return node;
    }

    @HostAccess.Export
    public long createId(Value cfg) {
        AudioNode n = create(cfg);
        Long id = n.getUserData(USERDATA_SOUND_ID);
        return id != null ? id : 0L;
    }

    @HostAccess.Export
    public long getId(AudioNode node) {
        if (node == null) return 0L;
        Long id = node.getUserData(USERDATA_SOUND_ID);
        return id != null ? id : 0L;
    }

    @HostAccess.Export
    public AudioNode getById(long id) {
        return nodeCache.get(id);
    }

    @HostAccess.Export
    @Override
    public void play(AudioNode audioNode) {
        if (audioNode == null) throw new IllegalArgumentException("play: audioNode is required");
        audioNode.playInstance();
    }

    @HostAccess.Export
    @Override
    public void stop(AudioNode audioNode) {
        if (audioNode == null) throw new IllegalArgumentException("stop: audioNode is required");
        audioNode.stop();
    }


    @HostAccess.Export
    @Override
    public void setPosition(AudioNode audioNode, float x, float y, float z) {
        if (audioNode == null) throw new IllegalArgumentException("setPosition: audioNode is required");
        audioNode.setPositional(true);
        audioNode.setLocalTranslation(x, y, z);
    }

    @HostAccess.Export
    public void playId(long id) {
        AudioNode n = nodeCache.get(id);
        if (n == null) throw new IllegalArgumentException("sound.playId: id not found: " + id);
        n.playInstance();
    }

    @HostAccess.Export
    @Override
    public void setLooping(AudioNode audioNode, boolean loop) {
        if (audioNode == null) throw new IllegalArgumentException("setLooping: audioNode is required");
        audioNode.setLooping(loop);
    }

    @HostAccess.Export
    @Override
    public void setVolume(AudioNode audioNode, float volume) {
        if (audioNode == null) throw new IllegalArgumentException("setVolume: audioNode is required");
        audioNode.setVolume(volume);
    }

    @HostAccess.Export
    @Override
    public void setPitch(AudioNode audioNode, float pitch) {
        if (audioNode == null) throw new IllegalArgumentException("setPitch: audioNode is required");
        audioNode.setPitch(pitch);
    }

    @HostAccess.Export
    @Override
    public void setDirectional(AudioNode audioNode, boolean directional) {
        if (audioNode == null) throw new IllegalArgumentException("setDirectional: audioNode is required");
        audioNode.setDirectional(directional);
    }

    @HostAccess.Export
    @Override
    public void setMaxDistance(AudioNode audioNode, float maxDistance) {
        if (audioNode == null) throw new IllegalArgumentException("setMaxDistance: audioNode is required");
        audioNode.setMaxDistance(maxDistance);
    }

    @HostAccess.Export
    @Override
    public void setReverbEnabled(AudioNode audioNode, boolean reverbEnabled) {
        if (audioNode == null) throw new IllegalArgumentException("setReverbEnabled: audioNode is required");
        audioNode.setReverbEnabled(reverbEnabled);
    }

    @HostAccess.Export
    @Override
    public void setDryFilter(AudioNode audioNode, Object filter) {
        if (audioNode == null) throw new IllegalArgumentException("setDryFilter: audioNode is required");
        audioNode.setDryFilter((com.jme3.audio.Filter) filter);
    }

    // ------------------------------------------------------------
    // Sound Bank (AAA event system)
    // ------------------------------------------------------------

    @HostAccess.Export
    public void stopId(long id) {
        AudioNode n = nodeCache.get(id);
        if (n == null) throw new IllegalArgumentException("sound.stopId: id not found: " + id);
        n.stop();
    }

    @HostAccess.Export
    public void releaseId(long id) {
        AudioNode n = nodeCache.remove(id);
        if (n != null) {
            n.stop();
        }
    }

    @HostAccess.Export
    @Override
    public AudioNode createAndPlay(Value cfg) {
        AudioNode n = create(cfg);
        play(n);
        return n;
    }

    @HostAccess.Export
    public void setPositional(AudioNode audioNode, boolean positional) {
        if (audioNode == null) throw new IllegalArgumentException("setPositional: audioNode is required");
        audioNode.setPositional(positional);
    }

    /**
     * Loads a bank object (typically parsed JSON from JS) and caches prototypes.
     * Leaf arrays become events. Example:
     * { world: { rock: { hit: ["Sounds/hit1.ogg"] } } } -> event "world.rock.hit"
     */
    @HostAccess.Export
    public void loadBank(Value bankObj) {
        if (bankObj == null || bankObj.isNull()) {
            throw new IllegalArgumentException("sound.loadBank(bankObj): bankObj is required");
        }

        Map<String, List<SoundDef>> collected = new ConcurrentHashMap<>();
        collectBank(bankObj, "", collected);

        for (Map.Entry<String, List<SoundDef>> e : collected.entrySet()) {
            List<SoundDef> defs = List.copyOf(e.getValue());
            bank.put(e.getKey(), defs);

            for (SoundDef d : defs) {
                ensurePrototype(d);
            }
        }
    }

    // ------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------

    /**
     * Clears loaded bank and prototype cache.
     */
    @HostAccess.Export
    public void clearBank() {
        bank.clear();
        prototypes.clear();
    }

    /**
     * Returns all event keys currently loaded.
     */
    @HostAccess.Export
    public String[] listEvents() {
        Set<String> keys = bank.keySet();
        return keys.toArray(new String[0]);
    }

    /**
     * Plays one random variant for the given event key.
     * Overrides may contain the same fields as create(cfg): volume, pitch, looping, is3D, pos/x/y/z, etc.
     */
    @HostAccess.Export
    public AudioNode playEvent(String eventKey, Value overrides) {
        AudioNode n = createEvent(eventKey, overrides);
        play(n);
        return n;
    }

    /**
     * Creates (clones) one random variant for the given event key.
     * Useful if you want to tweak the instance further from JS (positional, etc.).
     */
    @HostAccess.Export
    public AudioNode createEvent(String eventKey, Value overrides) {
        if (!hasText(eventKey))
            throw new IllegalArgumentException("sound.createEvent(eventKey, overrides): eventKey is required");

        List<SoundDef> defs = bank.get(eventKey);
        if (defs == null || defs.isEmpty()) {
            throw new IllegalArgumentException("sound event not found: " + eventKey);
        }

        SoundDef def = defs.get(ThreadLocalRandom.current().nextInt(defs.size()));
        AudioNode proto = ensurePrototype(def);

        AudioNode inst = (AudioNode) proto.clone(false);
        applyDef(inst, def);
        applyOverrides(inst, overrides);

        return inst;
    }

    private AudioNode ensurePrototype(SoundDef def) {
        String key = def.protoKey();
        return prototypes.computeIfAbsent(key, k -> createPrototype(def));
    }

    private AudioNode createPrototype(SoundDef def) {
        final AudioNode node;

        if (def.isStereo()) {
            if (!def.is3D) {
                node = new AudioNode(assetManager, def.leftFile, def.type);
                node.setPositional(false);
            } else {
                SpatialStereoAudioNode s = new SpatialStereoAudioNode(assetManager, def.leftFile, def.rightFile, def.type);
                s.setSeparation(def.separation);
                node = s;
                node.setPositional(true);
            }
        } else {
            node = new AudioNode(assetManager, def.src, def.type);
            node.setPositional(def.is3D);
        }

        applyDef(node, def);
        return node;
    }

    // ------------------------------------------------------------
    // Data
    // ------------------------------------------------------------

    private static final class SoundDef {
        final String src;
        final String leftFile;
        final String rightFile;
        final AudioData.DataType type;
        final float separation;
        final boolean is3D;
        final boolean looping;
        final FloatRange volume;
        final FloatRange pitch;

        private SoundDef(
                String src,
                String leftFile,
                String rightFile,
                AudioData.DataType type,
                float separation,
                boolean is3D,
                boolean looping,
                FloatRange volume,
                FloatRange pitch
        ) {
            this.src = src;
            this.leftFile = leftFile;
            this.rightFile = rightFile;
            this.type = type;
            this.separation = separation;
            this.is3D = is3D;
            this.looping = looping;
            this.volume = volume;
            this.pitch = pitch;
        }

        static SoundDef simple(String src) {
            return mono(src, AudioData.DataType.Buffer, false, false,
                    new FloatRange(1.0f, 1.0f), new FloatRange(1.0f, 1.0f));
        }

        static SoundDef mono(
                String src,
                AudioData.DataType type,
                boolean is3D,
                boolean looping,
                FloatRange volume,
                FloatRange pitch
        ) {
            return new SoundDef(src, "", "", type, 0.20f, is3D, looping, volume, pitch);
        }

        static SoundDef stereo(
                String left,
                String right,
                AudioData.DataType type,
                float separation,
                boolean is3D,
                boolean looping,
                FloatRange volume,
                FloatRange pitch
        ) {
            return new SoundDef("", left, right, type, separation, is3D, looping, volume, pitch);
        }

        boolean isStereo() {
            return hasText(leftFile) && hasText(rightFile);
        }

        String protoKey() {
            if (isStereo()) {
                return "stereo|" + type + "|" + leftFile + "|" + rightFile + "|3d=" + is3D + "|sep=" + separation;
            }
            return "mono|" + type + "|" + src + "|3d=" + is3D;
        }
    }

    private static final class FloatRange {
        final float min;
        final float max;

        FloatRange(float min, float max) {
            this.min = min;
            this.max = max;
        }

        static FloatRange parse(Value cfg, String key, float def, float clampMin, float clampMax) {
            if (cfg == null || cfg.isNull() || !has(cfg, key)) {
                float v = clamp(def, clampMin, clampMax);
                return new FloatRange(v, v);
            }

            Value v = member(cfg, key);
            if (v == null || v.isNull()) {
                float x = clamp(def, clampMin, clampMax);
                return new FloatRange(x, x);
            }

            if (v.hasArrayElements() && v.getArraySize() >= 2) {
                float a = (float) v.getArrayElement(0).asDouble();
                float b = (float) v.getArrayElement(1).asDouble();
                float lo = clamp(Math.min(a, b), clampMin, clampMax);
                float hi = clamp(Math.max(a, b), clampMin, clampMax);
                return new FloatRange(lo, hi);
            }

            float x = clamp((float) v.asDouble(), clampMin, clampMax);
            return new FloatRange(x, x);
        }

        private static float clamp(float v, float lo, float hi) {
            return RenderCfg.clamp(v, lo, hi);
        }

        float sample() {
            if (min == max) return min;
            float t = ThreadLocalRandom.current().nextFloat();
            return min + (max - min) * t;
        }
    }
}