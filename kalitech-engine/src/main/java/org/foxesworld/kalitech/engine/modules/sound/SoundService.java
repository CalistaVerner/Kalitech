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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SoundService {

    private final AssetManager assets;
    private final SoundNodeRegistry registry;

    private final Map<String, List<SoundDef>> bank = new ConcurrentHashMap<>();
    private final Map<String, AudioNode> prototypes = new ConcurrentHashMap<>();

    public SoundService(AssetManager assets, SoundNodeRegistry registry) {
        this.assets = Objects.requireNonNull(assets, "assets");
        this.registry = Objects.requireNonNull(registry, "registry");
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
        if (!SoundParsers.hasText(eventKey)) {
            throw new IllegalArgumentException("sound.createEvent(eventKey, overrides): eventKey is required");
        }

        List<SoundDef> defs = bank.get(eventKey);
        if (defs == null || defs.isEmpty()) {
            throw new IllegalArgumentException("sound event not found: " + eventKey);
        }

        SoundDef def = defs.get(ThreadLocalRandom.current().nextInt(defs.size()));
        AudioNode proto = ensurePrototype(def);

        AudioNode inst = (AudioNode) proto.clone(false);
        SoundParsers.applyDef(inst, def);
        SoundParsers.applyOverrides(inst, overrides);

        return inst;
    }

    private AudioNode ensurePrototype(SoundDef def) {
        String key = def.protoKey();
        return prototypes.computeIfAbsent(key, k -> SoundParsers.createPrototype(assets, def));
    }
}
