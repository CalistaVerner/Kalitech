/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.asset.AssetManager
 *  org.foxesworld.kalitech.engine.script.util.LuaCfg
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.sound;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioNode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.foxesworld.kalitech.engine.modules.sound.SoundDef;
import org.foxesworld.kalitech.engine.modules.sound.SoundDeterminism;
import org.foxesworld.kalitech.engine.modules.sound.SoundEventContext;
import org.foxesworld.kalitech.engine.modules.sound.SoundNodeRegistry;
import org.foxesworld.kalitech.engine.modules.sound.SoundParsers;
import org.foxesworld.kalitech.engine.script.util.LuaCfg;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class SoundService {
    private final AssetManager assets;
    private final SoundNodeRegistry registry;
    private final Map<String, List<SoundDef>> bank = new ConcurrentHashMap<String, List<SoundDef>>();
    private final Map<String, AudioNode> prototypes = new ConcurrentHashMap<String, AudioNode>();
    private volatile SoundDeterminism.Mode mode = SoundDeterminism.Mode.DETERMINISTIC;
    private volatile long seed = 1L;

    public SoundService(AssetManager assets, SoundNodeRegistry registry) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public long getSeed() {
        return this.seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public SoundDeterminism.Mode getDeterminismMode() {
        return this.mode;
    }

    public void setDeterminismMode(SoundDeterminism.Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    public AudioNode create(LuaValueRef cfg) {
        return SoundParsers.createFromCfg(this.assets, cfg);
    }

    public void loadBank(LuaValueRef bankObj) {
        ConcurrentHashMap<String, List<SoundDef>> collected = new ConcurrentHashMap<String, List<SoundDef>>();
        SoundParsers.collectBank(bankObj, "", collected);
        for (Map.Entry e : collected.entrySet()) {
            List<SoundDef> defs = List.copyOf((Collection)e.getValue());
            this.bank.put((String)e.getKey(), defs);
            for (SoundDef d : defs) {
                this.ensurePrototype(d);
            }
        }
    }

    public void clearBank() {
        this.bank.clear();
        this.prototypes.clear();
    }

    public String[] listEvents() {
        Set<String> keys = this.bank.keySet();
        return keys.toArray(new String[0]);
    }

    public AudioNode createEvent(String eventKey, LuaValueRef overrides) {
        return this.createEvent(eventKey, overrides, SoundEventContext.none());
    }

    public AudioNode createEvent(String eventKey, LuaValueRef overrides, SoundEventContext ctx) {
        int idx;
        if (!SoundParsers.hasText(eventKey)) {
            throw new IllegalArgumentException("sound.createEvent(eventKey, overrides): eventKey is required");
        }
        List<SoundDef> defs = this.bank.get(eventKey);
        if (defs == null || defs.isEmpty()) {
            throw new IllegalArgumentException("sound event not found: " + eventKey);
        }
        long base = this.seed;
        long s = SoundDeterminism.seedForEvent(base, eventKey, ctx.a, ctx.b, ctx.c);
        if (this.mode == SoundDeterminism.Mode.DETERMINISTIC) {
            idx = SoundDeterminism.chooseIndex(s, defs.size());
        } else {
            long t = System.nanoTime();
            idx = SoundDeterminism.chooseIndex(s ^ t, defs.size());
        }
        SoundDef def = defs.get(idx);
        AudioNode proto = this.ensurePrototype(def);
        AudioNode inst = (AudioNode)proto.clone(false);
        if (this.mode == SoundDeterminism.Mode.DETERMINISTIC) {
            SoundParsers.applyDef(inst, def, s);
        } else {
            SoundParsers.applyDef(inst, def, s ^ System.nanoTime());
        }
        SoundParsers.applyOverrides(inst, overrides);
        return inst;
    }

    public AudioNode createEventCfg(LuaValueRef cfg) {
        LuaValueRef c;
        String eventKey = LuaCfg.str((LuaValueRef)cfg, (String)"event", (String)"");
        if (!SoundParsers.hasText(eventKey)) {
            throw new IllegalArgumentException("sound.createEventCfg(cfg): 'event' is required");
        }
        LuaValueRef overrides = LuaCfg.has((LuaValueRef)cfg, (String)"overrides") ? LuaCfg.member((LuaValueRef)cfg, (String)"overrides") : null;
        boolean deterministicCall = LuaCfg.bool((LuaValueRef)cfg, (String)"deterministic", (this.mode == SoundDeterminism.Mode.DETERMINISTIC ? 1 : 0) != 0);
        long seedCall = (long)LuaCfg.num((LuaValueRef)cfg, (String)"seed", (double)this.seed);
        SoundEventContext ctx = SoundEventContext.none();
        if (LuaCfg.has((LuaValueRef)cfg, (String)"context") && (c = LuaCfg.member((LuaValueRef)cfg, (String)"context")) != null && !c.isNull() && c.hasMembers()) {
            String entityUuid = LuaCfg.str((LuaValueRef)c, (String)"entityUuid", (String)"");
            long surfaceId = (long)LuaCfg.num((LuaValueRef)c, (String)"surfaceId", (double)0.0);
            long seq = (long)LuaCfg.num((LuaValueRef)c, (String)"seq", (double)0.0);
            long tick = (long)LuaCfg.num((LuaValueRef)c, (String)"tick", (double)0.0);
            long slot = (long)LuaCfg.num((LuaValueRef)c, (String)"slot", (double)0.0);
            long entitySeed = SoundService.uuidSeed(entityUuid);
            long b = surfaceId ^ slot << 32;
            long cc = seq ^ tick << 32;
            ctx = new SoundEventContext(entitySeed, b, cc);
        }
        SoundDeterminism.Mode callMode = deterministicCall ? SoundDeterminism.Mode.DETERMINISTIC : SoundDeterminism.Mode.NON_DETERMINISTIC;
        return this.createEventInternal(eventKey, overrides, ctx, callMode, seedCall);
    }

    private static long uuidSeed(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return 0L;
        }
        UUID u = UUID.fromString(uuid.trim());
        long mixed = u.getMostSignificantBits() ^ u.getLeastSignificantBits();
        return SoundDeterminism.mix64(mixed);
    }

    private AudioNode createEventInternal(String eventKey, LuaValueRef overrides, SoundEventContext ctx, SoundDeterminism.Mode callMode, long callSeed) {
        List<SoundDef> defs = this.bank.get(eventKey);
        if (defs == null || defs.isEmpty()) {
            throw new IllegalArgumentException("sound event not found: " + eventKey);
        }
        long s = SoundDeterminism.seedForEvent(callSeed, eventKey, ctx.a, ctx.b, ctx.c);
        int idx = callMode == SoundDeterminism.Mode.DETERMINISTIC ? SoundDeterminism.chooseIndex(s, defs.size()) : SoundDeterminism.chooseIndex(s ^ System.nanoTime(), defs.size());
        SoundDef def = defs.get(idx);
        AudioNode proto = this.ensurePrototype(def);
        AudioNode inst = (AudioNode)proto.clone(false);
        long sampleSeed = callMode == SoundDeterminism.Mode.DETERMINISTIC ? s : s ^ System.nanoTime();
        SoundParsers.applyDef(inst, def, sampleSeed);
        SoundParsers.applyOverrides(inst, overrides);
        return inst;
    }

    private AudioNode ensurePrototype(SoundDef def) {
        String key = def.protoKey();
        return this.prototypes.computeIfAbsent(key, k -> SoundParsers.createPrototype(this.assets, def));
    }
}

