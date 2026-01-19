// FILE: org/foxesworld/kalitech/engine/modules/sound/SoundService.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.sound;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioNode;
import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SoundService {

    private final AssetManager assets;
    private final SoundNodeRegistry registry;

    private final Map<String, List<SoundDef>> bank = new ConcurrentHashMap<>();
    private final Map<String, AudioNode> prototypes = new ConcurrentHashMap<>();

    private volatile SoundDeterminism.Mode mode = SoundDeterminism.Mode.DETERMINISTIC;
    private volatile long seed = 1L;

    public SoundService(AssetManager assets, SoundNodeRegistry registry) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public SoundDeterminism.Mode getDeterminismMode() {
        return mode;
    }

    public void setDeterminismMode(SoundDeterminism.Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public AudioNode create(Value cfg) {
        return SoundParsers.createFromCfg(assets, cfg);
    }

    public void loadBank(Value bankObj) {
        Map<String, List<SoundDef>> collected = new ConcurrentHashMap<>();
        SoundParsers.collectBank(bankObj, "", collected);

        for (Map.Entry<String, List<SoundDef>> e : collected.entrySet()) {
            List<SoundDef> defs = List.copyOf(e.getValue());
            bank.put(e.getKey(), defs);
            for (SoundDef d : defs) ensurePrototype(d);
        }
    }

    public void clearBank() {
        bank.clear();
        prototypes.clear();
    }

    public String[] listEvents() {
        Set<String> keys = bank.keySet();
        return keys.toArray(new String[0]);
    }

    public AudioNode createEvent(String eventKey, Value overrides) {
        return createEvent(eventKey, overrides, SoundEventContext.none());
    }

    public AudioNode createEvent(String eventKey, Value overrides, SoundEventContext ctx) {
        if (!SoundParsers.hasText(eventKey)) {
            throw new IllegalArgumentException("sound.createEvent(eventKey, overrides): eventKey is required");
        }

        List<SoundDef> defs = bank.get(eventKey);
        if (defs == null || defs.isEmpty()) {
            throw new IllegalArgumentException("sound event not found: " + eventKey);
        }

        long base = seed;
        long s = SoundDeterminism.seedForEvent(base, eventKey, ctx.a, ctx.b, ctx.c);

        int idx;
        if (mode == SoundDeterminism.Mode.DETERMINISTIC) {
            idx = SoundDeterminism.chooseIndex(s, defs.size());
        } else {
            long t = System.nanoTime();
            idx = SoundDeterminism.chooseIndex(s ^ t, defs.size());
        }

        SoundDef def = defs.get(idx);
        AudioNode proto = ensurePrototype(def);

        AudioNode inst = (AudioNode) proto.clone(false);

        if (mode == SoundDeterminism.Mode.DETERMINISTIC) {
            SoundParsers.applyDef(inst, def, s);
        } else {
            SoundParsers.applyDef(inst, def, s ^ System.nanoTime());
        }

        SoundParsers.applyOverrides(inst, overrides);
        return inst;
    }

    // in SoundService.java
    public AudioNode createEventCfg(Value cfg) {
        String eventKey = org.foxesworld.kalitech.engine.script.util.JsCfg.str(cfg, "event", "");
        if (!SoundParsers.hasText(eventKey)) {
            throw new IllegalArgumentException("sound.createEventCfg(cfg): 'event' is required");
        }

        Value overrides = org.foxesworld.kalitech.engine.script.util.JsCfg.has(cfg, "overrides")
                ? org.foxesworld.kalitech.engine.script.util.JsCfg.member(cfg, "overrides")
                : null;

        boolean deterministicCall = org.foxesworld.kalitech.engine.script.util.JsCfg.bool(cfg, "deterministic",
                mode == SoundDeterminism.Mode.DETERMINISTIC);

        long seedCall = (long) org.foxesworld.kalitech.engine.script.util.JsCfg.num(cfg, "seed", seed);

        SoundEventContext ctx = SoundEventContext.none();
        if (org.foxesworld.kalitech.engine.script.util.JsCfg.has(cfg, "context")) {
            Value c = org.foxesworld.kalitech.engine.script.util.JsCfg.member(cfg, "context");
            if (c != null && !c.isNull() && c.hasMembers()) {
                String entityUuid = org.foxesworld.kalitech.engine.script.util.JsCfg.str(c, "entityUuid", "");
                long surfaceId = (long) org.foxesworld.kalitech.engine.script.util.JsCfg.num(c, "surfaceId", 0);
                long seq = (long) org.foxesworld.kalitech.engine.script.util.JsCfg.num(c, "seq", 0);
                long tick = (long) org.foxesworld.kalitech.engine.script.util.JsCfg.num(c, "tick", 0);
                long slot = (long) org.foxesworld.kalitech.engine.script.util.JsCfg.num(c, "slot", 0);

                // packing: stable fields -> ctx a/b/c
                // a: entityUuidHash, b: surfaceId ^ (slot<<32), c: seq ^ (tick<<32)
                long entitySeed = uuidSeed(entityUuid);
                long b = surfaceId ^ (slot << 32);
                long cc = seq ^ (tick << 32);
                ctx = new SoundEventContext(entitySeed, b, cc);
            }
        }

        // Per-call deterministic override
        SoundDeterminism.Mode callMode = deterministicCall
                ? SoundDeterminism.Mode.DETERMINISTIC
                : SoundDeterminism.Mode.NON_DETERMINISTIC;

        return createEventInternal(eventKey, overrides, ctx, callMode, seedCall);
    }

    private static long uuidSeed(String uuid) {
        if (uuid == null || uuid.isBlank()) return 0L;
        UUID u = UUID.fromString(uuid.trim());
        long mixed = u.getMostSignificantBits() ^ u.getLeastSignificantBits();
        return SoundDeterminism.mix64(mixed);
    }

    private AudioNode createEventInternal(String eventKey, Value overrides, SoundEventContext ctx,
                                          SoundDeterminism.Mode callMode, long callSeed) {
        List<SoundDef> defs = bank.get(eventKey);
        if (defs == null || defs.isEmpty()) {
            throw new IllegalArgumentException("sound event not found: " + eventKey);
        }

        long s = SoundDeterminism.seedForEvent(callSeed, eventKey, ctx.a, ctx.b, ctx.c);

        int idx;
        if (callMode == SoundDeterminism.Mode.DETERMINISTIC) {
            idx = SoundDeterminism.chooseIndex(s, defs.size());
        } else {
            idx = SoundDeterminism.chooseIndex(s ^ System.nanoTime(), defs.size());
        }

        SoundDef def = defs.get(idx);
        AudioNode proto = ensurePrototype(def);
        AudioNode inst = (AudioNode) proto.clone(false);

        long sampleSeed = (callMode == SoundDeterminism.Mode.DETERMINISTIC) ? s : (s ^ System.nanoTime());
        SoundParsers.applyDef(inst, def, sampleSeed);
        SoundParsers.applyOverrides(inst, overrides);
        return inst;
    }


    private AudioNode ensurePrototype(SoundDef def) {
        String key = def.protoKey();
        return prototypes.computeIfAbsent(key, k -> SoundParsers.createPrototype(assets, def));
    }
}
