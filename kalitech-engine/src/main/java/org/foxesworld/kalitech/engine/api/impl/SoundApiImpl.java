// FILE: org/foxesworld/kalitech/engine/api/impl/SoundApiImpl.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.api.impl;


import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioNode;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.SoundApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.modules.sound.SoundNodeRegistry;
import org.foxesworld.kalitech.engine.modules.sound.SoundService;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.Objects;

@Deprecated
public final class SoundApiImpl extends AbstractApiModule implements SoundApi {

    private EngineApiImpl engine;
    private AssetManager assetManager;

    private SoundService service;
    private SoundNodeRegistry registry;

    public SoundApiImpl() {
        super("sound", "Sound", "1.2.0");
    }

    public SoundApiImpl(EngineApiImpl engineApi) {
        this();
        bind(engineApi);
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        bind(ctx.engine);
    }

    private static void requireNode(AudioNode node, String op) {
        if (node == null) throw new IllegalArgumentException(op + ": audioNode is required");
    }

    private AudioNode requireNodeById(long id, String op) {
        AudioNode node = registry.getById(id);
        if (node == null) throw new IllegalArgumentException(op + ": id not found: " + id);
        return node;
    }

    private void bind(EngineApiImpl engineApi) {
        this.engine = Objects.requireNonNull(engineApi, "engine");
        this.assetManager = engineApi.getApp().getAssetManager();
        this.registry = new SoundNodeRegistry();
        this.service = new SoundService(assetManager, registry);
    }

    private void logError(String msg, Throwable t) {
        if (log != null) log.error(msg, t);
    }

    // ---------------- API ----------------

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public AudioNode create(Value cfg) {
        if (cfg == null || cfg.isNull()) {
            IllegalArgumentException e = new IllegalArgumentException("sound.create(cfg): cfg is required");
            logError("[sound] create failed: cfg is null", e);
            throw e;
        }
        try {
            AudioNode node = service.create(cfg);
            registry.cache(node);
            return node;
        } catch (Throwable t) {
            logError("[sound] create failed", t);
            throw t;
        }
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public long createId(Value cfg) {
        AudioNode n = create(cfg);
        return registry.getId(n);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public long getId(AudioNode node) {
        return registry.getId(node);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public AudioNode getById(long id) {
        return registry.getById(id);
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void play(AudioNode audioNode) {
        requireNode(audioNode, "play");

        try {
            audioNode.playInstance();
            return;
        } catch (Throwable t) {
            long id = registry.getId(audioNode);

            if (audioNode.isPositional() && SoundErrors.isMonoOnlyPositionalError(t)) {

                logError("[sound] play failed: positional stereo buffer is not supported (use stereo3D L/R mono). id=" + id, t);

                boolean prev = audioNode.isPositional();
                try {
                    audioNode.setPositional(false);
                    audioNode.playInstance();
                } catch (Throwable t2) {
                    logError("[sound] play fallback failed id=" + id, t2);
                } finally {
                    try {
                        audioNode.setPositional(prev);
                    } catch (Throwable t3) {
                        logError("[sound] failed to restore positional flag id=" + id, t3);
                    }
                }
                return;
            }

            logError("[sound] play failed id=" + id, t);
        }
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public AudioNode playEventCfg(Value cfg) {
        if (cfg == null || cfg.isNull()) {
            IllegalArgumentException e = new IllegalArgumentException("sound.playEventCfg(cfg): cfg is required");
            logError("[sound] playEventCfg failed: cfg is null", e);
            throw e;
        }
        try {
            AudioNode n = createEventCfg(cfg);
            play(n);
            return n;
        } catch (Throwable t) {
            logError("[sound] playEventCfg failed", t);
            throw t;
        }
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public long playEventCfgId(Value cfg) {
        AudioNode n = playEventCfg(cfg);
        return registry.getId(n);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public AudioNode createEventCfg(Value cfg) {
        if (cfg == null || cfg.isNull()) {
            IllegalArgumentException e = new IllegalArgumentException("sound.createEventCfg(cfg): cfg is required");
            logError("[sound] createEventCfg failed: cfg is null", e);
            throw e;
        }
        try {
            AudioNode node = service.createEventCfg(cfg);
            registry.cache(node);
            return node;
        } catch (Throwable t) {
            logError("[sound] createEventCfg failed", t);
            throw t;
        }
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public long createEventId(Value cfg) {
        AudioNode n = createEventCfg(cfg);
        return registry.getId(n);
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public long getSeed() {
        return service.getSeed();
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setSeed(long seed) {
        try {
            service.setSeed(seed);
        } catch (Throwable t) {
            logError("[sound] setSeed failed seed=" + seed, t);
            throw t;
        }
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setDeterministic(boolean deterministic) {
        try {
            service.setDeterminismMode(
                    deterministic ? org.foxesworld.kalitech.engine.modules.sound.SoundDeterminism.Mode.DETERMINISTIC
                            : org.foxesworld.kalitech.engine.modules.sound.SoundDeterminism.Mode.NON_DETERMINISTIC
            );
        } catch (Throwable t) {
            logError("[sound] setDeterministic failed", t);
            throw t;
        }
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void stop(AudioNode audioNode) {
        requireNode(audioNode, "stop");
        try {
            audioNode.stop();
        } catch (Throwable t) {
            logError("[sound] stop failed id=" + registry.getId(audioNode), t);
            throw t;
        }
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setPosition(AudioNode audioNode, float x, float y, float z) {
        requireNode(audioNode, "setPosition");
        try {
            audioNode.setPositional(true);
            audioNode.setLocalTranslation(x, y, z);
        } catch (Throwable t) {
            logError("[sound] setPosition failed id=" + registry.getId(audioNode), t);
            throw t;
        }
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setPositional(AudioNode audioNode, boolean positional) {
        requireNode(audioNode, "setPositional");
        try {
            audioNode.setPositional(positional);
        } catch (Throwable t) {
            logError("[sound] setPositional failed id=" + registry.getId(audioNode), t);
            throw t;
        }
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setLooping(AudioNode audioNode, boolean loop) {
        requireNode(audioNode, "setLooping");
        try {
            audioNode.setLooping(loop);
        } catch (Throwable t) {
            logError("[sound] setLooping failed id=" + registry.getId(audioNode), t);
            throw t;
        }
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setVolume(AudioNode audioNode, float volume) {
        requireNode(audioNode, "setVolume");
        try {
            audioNode.setVolume(volume);
        } catch (Throwable t) {
            logError("[sound] setVolume failed id=" + registry.getId(audioNode), t);
            throw t;
        }
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setPitch(AudioNode audioNode, float pitch) {
        requireNode(audioNode, "setPitch");
        try {
            audioNode.setPitch(pitch);
        } catch (Throwable t) {
            logError("[sound] setPitch failed id=" + registry.getId(audioNode), t);
            throw t;
        }
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setDirectional(AudioNode audioNode, boolean directional) {
        requireNode(audioNode, "setDirectional");
        try {
            audioNode.setDirectional(directional);
        } catch (Throwable t) {
            logError("[sound] setDirectional failed id=" + registry.getId(audioNode), t);
            throw t;
        }
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setMaxDistance(AudioNode audioNode, float maxDistance) {
        requireNode(audioNode, "setMaxDistance");
        try {
            audioNode.setMaxDistance(maxDistance);
        } catch (Throwable t) {
            logError("[sound] setMaxDistance failed id=" + registry.getId(audioNode), t);
            throw t;
        }
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setReverbEnabled(AudioNode audioNode, boolean reverbEnabled) {
        requireNode(audioNode, "setReverbEnabled");
        try {
            audioNode.setReverbEnabled(reverbEnabled);
        } catch (Throwable t) {
            logError("[sound] setReverbEnabled failed id=" + registry.getId(audioNode), t);
            throw t;
        }
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setDryFilter(AudioNode audioNode, Object filter) {
        requireNode(audioNode, "setDryFilter");
        try {
            audioNode.setDryFilter((com.jme3.audio.Filter) filter);
        } catch (Throwable t) {
            logError("[sound] setDryFilter failed id=" + registry.getId(audioNode), t);
            throw t;
        }
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setPositionId(long id, float x, float y, float z) {
        setPosition(requireNodeById(id, "setPositionId"), x, y, z);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setPositionalId(long id, boolean positional) {
        setPositional(requireNodeById(id, "setPositionalId"), positional);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setLoopingId(long id, boolean loop) {
        setLooping(requireNodeById(id, "setLoopingId"), loop);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setVolumeId(long id, float volume) {
        setVolume(requireNodeById(id, "setVolumeId"), volume);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setPitchId(long id, float pitch) {
        setPitch(requireNodeById(id, "setPitchId"), pitch);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setDirectionalId(long id, boolean directional) {
        setDirectional(requireNodeById(id, "setDirectionalId"), directional);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setMaxDistanceId(long id, float maxDistance) {
        setMaxDistance(requireNodeById(id, "setMaxDistanceId"), maxDistance);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setReverbEnabledId(long id, boolean reverbEnabled) {
        setReverbEnabled(requireNodeById(id, "setReverbEnabledId"), reverbEnabled);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void setDryFilterId(long id, Object filter) {
        setDryFilter(requireNodeById(id, "setDryFilterId"), filter);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void playId(long id) {
        AudioNode n = registry.getById(id);
        if (n == null) {
            IllegalArgumentException e = new IllegalArgumentException("sound.playId: id not found: " + id);
            logError("[sound] playId failed: id not found: " + id, e);
            throw e;
        }
        play(n);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void stopId(long id) {
        AudioNode n = registry.getById(id);
        if (n == null) {
            IllegalArgumentException e = new IllegalArgumentException("sound.stopId: id not found: " + id);
            logError("[sound] stopId failed: id not found: " + id, e);
            throw e;
        }
        stop(n);
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void releaseId(long id) {
        try {
            AudioNode n = registry.remove(id);
            if (n != null) n.stop();
        } catch (Throwable t) {
            logError("[sound] releaseId failed id=" + id, t);
            throw t;
        }
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public AudioNode createAndPlay(Value cfg) {
        AudioNode n = create(cfg);
        play(n);
        return n;
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void loadBank(Value bankObj) {
        if (bankObj == null || bankObj.isNull()) {
            IllegalArgumentException e = new IllegalArgumentException("sound.loadBank(bankObj): bankObj is required");
            logError("[sound] loadBank failed: bankObj is null", e);
            throw e;
        }
        try {
            service.loadBank(bankObj);
        } catch (Throwable t) {
            logError("[sound] loadBank failed", t);
            throw t;
        }
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public void clearBank() {
        try {
            service.clearBank();
        } catch (Throwable t) {
            logError("[sound] clearBank failed", t);
            throw t;
        }
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public String[] listEvents() {
        try {
            return service.listEvents();
        } catch (Throwable t) {
            logError("[sound] listEvents failed", t);
            throw t;
        }
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public AudioNode createEvent(String eventKey, Value overrides) {
        try {
            AudioNode node = service.createEvent(eventKey, overrides);
            registry.cache(node);
            return node;
        } catch (Throwable t) {
            logError("[sound] createEvent failed key=" + eventKey, t);
            throw t;
        }
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public AudioNode playEvent(String eventKey, Value overrides) {
        try {
            AudioNode n = createEvent(eventKey, overrides);
            play(n);
            return n;
        } catch (Throwable t) {
            logError("[sound] playEvent failed key=" + eventKey, t);
            throw t;
        }
    }

    static class SoundErrors {

        static boolean isMonoOnlyPositionalError(Throwable t) {
            if (t == null) return false;
            String m = t.getMessage();
            if (m == null) return false;
            return m.contains("Only mono audio is supported") && m.contains("positional");
        }
    }
}
