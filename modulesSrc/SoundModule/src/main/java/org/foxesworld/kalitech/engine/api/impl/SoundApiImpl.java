/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.api.EngineApiImpl
 *  org.foxesworld.kalitech.engine.api.contract.ApiCostHint
 *  org.foxesworld.kalitech.engine.api.contract.ApiFlag
 *  org.foxesworld.kalitech.engine.api.contract.ApiMethod
 *  org.foxesworld.kalitech.engine.api.contract.ApiThreadRule
 *  org.foxesworld.kalitech.engine.api.module.AbstractApiModule
 *  org.foxesworld.kalitech.engine.api.module.ApiContext
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.audio.AudioNode;
import com.jme3.audio.Filter;
import com.jme3.audio.plugins.OGGLoader;
import com.jme3.audio.plugins.WAVLoader;
import java.util.Objects;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.SoundApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.modules.sound.SoundDeterminism;
import org.foxesworld.kalitech.engine.modules.sound.SoundNodeRegistry;
import org.foxesworld.kalitech.engine.modules.sound.SoundService;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class SoundApiImpl
extends AbstractApiModule
implements SoundApi {
    private EngineApiImpl engine;
    private SoundService service;
    private SoundNodeRegistry registry;

    public SoundApiImpl() {
        super("sound", "Sound", "1.2.0");
    }

    public SoundApiImpl(EngineApiImpl engineApi) {
        this();
        this.bind(engineApi);
    }

    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.bind(ctx.engine);
    }

    private static void requireNode(AudioNode node, String op) {
        if (node == null) {
            throw new IllegalArgumentException(op + ": audioNode is required");
        }
    }

    private AudioNode requireNodeById(long id, String op) {
        AudioNode node = this.registry.getById(id);
        if (node == null) {
            throw new IllegalArgumentException(op + ": id not found: " + id);
        }
        return node;
    }

    private void bind(EngineApiImpl engineApi) {
        this.engine = Objects.requireNonNull(engineApi, "engine");
        this.engine.getAssets().registerLoader(WAVLoader.class, new String[]{"wav"});
        this.engine.getAssets().registerLoader(OGGLoader.class, new String[]{"ogg"});
        this.registry = new SoundNodeRegistry();
        this.service = new SoundService(this.engine.getAssets(), this.registry);
    }

    private void logError(String msg, Throwable t) {
        if (this.log != null) {
            this.log.error(msg, t);
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public AudioNode create(LuaValueRef cfg) {
        if (cfg == null || cfg.isNull()) {
            IllegalArgumentException e = new IllegalArgumentException("sound.create(cfg): cfg is required");
            this.logError("[sound] create failed: cfg is null", e);
            throw e;
        }
        try {
            AudioNode node = this.service.create(cfg);
            this.registry.cache(node);
            return node;
        }
        catch (Throwable t) {
            this.logError("[sound] create failed", t);
            throw t;
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public long createId(LuaValueRef cfg) {
        AudioNode n = this.create(cfg);
        return this.registry.getId(n);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public long getId(AudioNode node) {
        return this.registry.getId(node);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public AudioNode getById(long id) {
        return this.registry.getById(id);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void play(AudioNode audioNode) {
        SoundApiImpl.requireNode(audioNode, "play");
        try {
            audioNode.playInstance();
            return;
        }
        catch (Throwable t) {
            long id = this.registry.getId(audioNode);
            if (audioNode.isPositional() && SoundErrors.isMonoOnlyPositionalError(t)) {
                this.logError("[sound] play failed: positional stereo buffer is not supported (use stereo3D L/R mono). id=" + id, t);
                boolean prev = audioNode.isPositional();
                try {
                    audioNode.setPositional(false);
                    audioNode.playInstance();
                }
                catch (Throwable t2) {
                    this.logError("[sound] play fallback failed id=" + id, t2);
                }
                finally {
                    try {
                        audioNode.setPositional(prev);
                    }
                    catch (Throwable t3) {
                        this.logError("[sound] failed to restore positional flag id=" + id, t3);
                    }
                }
                return;
            }
            this.logError("[sound] play failed id=" + id, t);
            return;
        }
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public AudioNode playEventCfg(LuaValueRef cfg) {
        if (cfg == null || cfg.isNull()) {
            IllegalArgumentException e = new IllegalArgumentException("sound.playEventCfg(cfg): cfg is required");
            this.logError("[sound] playEventCfg failed: cfg is null", e);
            throw e;
        }
        try {
            AudioNode n = this.createEventCfg(cfg);
            this.play(n);
            return n;
        }
        catch (Throwable t) {
            this.logError("[sound] playEventCfg failed", t);
            throw t;
        }
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public long playEventCfgId(LuaValueRef cfg) {
        AudioNode n = this.playEventCfg(cfg);
        return this.registry.getId(n);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public AudioNode createEventCfg(LuaValueRef cfg) {
        if (cfg == null || cfg.isNull()) {
            IllegalArgumentException e = new IllegalArgumentException("sound.createEventCfg(cfg): cfg is required");
            this.logError("[sound] createEventCfg failed: cfg is null", e);
            throw e;
        }
        try {
            AudioNode node = this.service.createEventCfg(cfg);
            this.registry.cache(node);
            return node;
        }
        catch (Throwable t) {
            this.logError("[sound] createEventCfg failed", t);
            throw t;
        }
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public long createEventId(LuaValueRef cfg) {
        AudioNode n = this.createEventCfg(cfg);
        return this.registry.getId(n);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public long getSeed() {
        return this.service.getSeed();
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setSeed(long seed) {
        try {
            this.service.setSeed(seed);
        }
        catch (Throwable t) {
            this.logError("[sound] setSeed failed seed=" + seed, t);
            throw t;
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setDeterministic(boolean deterministic) {
        try {
            this.service.setDeterminismMode(deterministic ? SoundDeterminism.Mode.DETERMINISTIC : SoundDeterminism.Mode.NON_DETERMINISTIC);
        }
        catch (Throwable t) {
            this.logError("[sound] setDeterministic failed", t);
            throw t;
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void stop(AudioNode audioNode) {
        SoundApiImpl.requireNode(audioNode, "stop");
        try {
            audioNode.stop();
        }
        catch (Throwable t) {
            this.logError("[sound] stop failed id=" + this.registry.getId(audioNode), t);
            throw t;
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setPosition(AudioNode audioNode, float x, float y, float z) {
        SoundApiImpl.requireNode(audioNode, "setPosition");
        try {
            audioNode.setPositional(true);
            audioNode.setLocalTranslation(x, y, z);
        }
        catch (Throwable t) {
            this.logError("[sound] setPosition failed id=" + this.registry.getId(audioNode), t);
            throw t;
        }
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setPositional(AudioNode audioNode, boolean positional) {
        SoundApiImpl.requireNode(audioNode, "setPositional");
        try {
            audioNode.setPositional(positional);
        }
        catch (Throwable t) {
            this.logError("[sound] setPositional failed id=" + this.registry.getId(audioNode), t);
            throw t;
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setLooping(AudioNode audioNode, boolean loop) {
        SoundApiImpl.requireNode(audioNode, "setLooping");
        try {
            audioNode.setLooping(loop);
        }
        catch (Throwable t) {
            this.logError("[sound] setLooping failed id=" + this.registry.getId(audioNode), t);
            throw t;
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setVolume(AudioNode audioNode, float volume) {
        SoundApiImpl.requireNode(audioNode, "setVolume");
        try {
            audioNode.setVolume(volume);
        }
        catch (Throwable t) {
            this.logError("[sound] setVolume failed id=" + this.registry.getId(audioNode), t);
            throw t;
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setPitch(AudioNode audioNode, float pitch) {
        SoundApiImpl.requireNode(audioNode, "setPitch");
        try {
            audioNode.setPitch(pitch);
        }
        catch (Throwable t) {
            this.logError("[sound] setPitch failed id=" + this.registry.getId(audioNode), t);
            throw t;
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setDirectional(AudioNode audioNode, boolean directional) {
        SoundApiImpl.requireNode(audioNode, "setDirectional");
        try {
            audioNode.setDirectional(directional);
        }
        catch (Throwable t) {
            this.logError("[sound] setDirectional failed id=" + this.registry.getId(audioNode), t);
            throw t;
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setMaxDistance(AudioNode audioNode, float maxDistance) {
        SoundApiImpl.requireNode(audioNode, "setMaxDistance");
        try {
            audioNode.setMaxDistance(maxDistance);
        }
        catch (Throwable t) {
            this.logError("[sound] setMaxDistance failed id=" + this.registry.getId(audioNode), t);
            throw t;
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setReverbEnabled(AudioNode audioNode, boolean reverbEnabled) {
        SoundApiImpl.requireNode(audioNode, "setReverbEnabled");
        try {
            audioNode.setReverbEnabled(reverbEnabled);
        }
        catch (Throwable t) {
            this.logError("[sound] setReverbEnabled failed id=" + this.registry.getId(audioNode), t);
            throw t;
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setDryFilter(AudioNode audioNode, Object filter) {
        SoundApiImpl.requireNode(audioNode, "setDryFilter");
        try {
            audioNode.setDryFilter((Filter)((Object)filter));
        }
        catch (Throwable t) {
            this.logError("[sound] setDryFilter failed id=" + this.registry.getId(audioNode), t);
            throw t;
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setPositionId(long id, float x, float y, float z) {
        this.setPosition(this.requireNodeById(id, "setPositionId"), x, y, z);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setPositionalId(long id, boolean positional) {
        this.setPositional(this.requireNodeById(id, "setPositionalId"), positional);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setLoopingId(long id, boolean loop) {
        this.setLooping(this.requireNodeById(id, "setLoopingId"), loop);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setVolumeId(long id, float volume) {
        this.setVolume(this.requireNodeById(id, "setVolumeId"), volume);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setPitchId(long id, float pitch) {
        this.setPitch(this.requireNodeById(id, "setPitchId"), pitch);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setDirectionalId(long id, boolean directional) {
        this.setDirectional(this.requireNodeById(id, "setDirectionalId"), directional);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setMaxDistanceId(long id, float maxDistance) {
        this.setMaxDistance(this.requireNodeById(id, "setMaxDistanceId"), maxDistance);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setReverbEnabledId(long id, boolean reverbEnabled) {
        this.setReverbEnabled(this.requireNodeById(id, "setReverbEnabledId"), reverbEnabled);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void setDryFilterId(long id, Object filter) {
        this.setDryFilter(this.requireNodeById(id, "setDryFilterId"), filter);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void playId(long id) {
        AudioNode n = this.registry.getById(id);
        if (n == null) {
            IllegalArgumentException e = new IllegalArgumentException("sound.playId: id not found: " + id);
            this.logError("[sound] playId failed: id not found: " + id, e);
            throw e;
        }
        this.play(n);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void stopId(long id) {
        AudioNode n = this.registry.getById(id);
        if (n == null) {
            IllegalArgumentException e = new IllegalArgumentException("sound.stopId: id not found: " + id);
            this.logError("[sound] stopId failed: id not found: " + id, e);
            throw e;
        }
        this.stop(n);
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void releaseId(long id) {
        try {
            AudioNode n = this.registry.remove(id);
            if (n != null) {
                n.stop();
            }
        }
        catch (Throwable t) {
            this.logError("[sound] releaseId failed id=" + id, t);
            throw t;
        }
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public AudioNode createAndPlay(LuaValueRef cfg) {
        AudioNode n = this.create(cfg);
        this.play(n);
        return n;
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void loadBank(LuaValueRef bankObj) {
        if (bankObj == null || bankObj.isNull()) {
            IllegalArgumentException e = new IllegalArgumentException("sound.loadBank(bankObj): bankObj is required");
            this.logError("[sound] loadBank failed: bankObj is null", e);
            throw e;
        }
        try {
            this.service.loadBank(bankObj);
        }
        catch (Throwable t) {
            this.logError("[sound] loadBank failed", t);
            throw t;
        }
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void clearBank() {
        try {
            this.service.clearBank();
        }
        catch (Throwable t) {
            this.logError("[sound] clearBank failed", t);
            throw t;
        }
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public String[] listEvents() {
        try {
            return this.service.listEvents();
        }
        catch (Throwable t) {
            this.logError("[sound] listEvents failed", t);
            throw t;
        }
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public AudioNode createEvent(String eventKey, LuaValueRef overrides) {
        try {
            AudioNode node = this.service.createEvent(eventKey, overrides);
            this.registry.cache(node);
            return node;
        }
        catch (Throwable t) {
            this.logError("[sound] createEvent failed key=" + eventKey, t);
            throw t;
        }
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public AudioNode playEvent(String eventKey, LuaValueRef overrides) {
        try {
            AudioNode n = this.createEvent(eventKey, overrides);
            this.play(n);
            return n;
        }
        catch (Throwable t) {
            this.logError("[sound] playEvent failed key=" + eventKey, t);
            throw t;
        }
    }

    static class SoundErrors {
        SoundErrors() {
        }

        static boolean isMonoOnlyPositionalError(Throwable t) {
            if (t == null) {
                return false;
            }
            String m = t.getMessage();
            if (m == null) {
                return false;
            }
            return m.contains("Only mono audio is supported") && m.contains("positional");
        }
    }
}

