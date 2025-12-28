package org.foxesworld.kalitech.engine.api.impl.sound;

import com.jme3.audio.AudioNode;
import com.jme3.audio.AudioData;
import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.SoundApi;

import java.util.Objects;

public final class SoundApiImpl implements SoundApi {

    private final EngineApiImpl engine;
    private final AssetManager assetManager;

    public SoundApiImpl(EngineApiImpl engineApi) {
        this.engine = Objects.requireNonNull(engineApi, "engine");
        this.assetManager = engineApi.getApp().getAssetManager();
    }

    @HostAccess.Export
    @Override
    public AudioNode create(Value cfg) {
        if (cfg == null || cfg.isNull()) throw new IllegalArgumentException("sound.create(cfg): cfg is required");

        String soundFile = str(cfg, "soundFile", "");
        float volume = (float) num(cfg, "volume", 1.0f);
        float pitch = (float) num(cfg, "pitch", 1.0f);
        boolean looping = bool(cfg, "looping", false);
        boolean is3D = bool(cfg, "is3D", false);

        AudioNode audioNode = new AudioNode(assetManager, soundFile, AudioData.DataType.Buffer);
        audioNode.setVolume(volume);
        audioNode.setPitch(pitch);
        audioNode.setLooping(looping);
        audioNode.setPositional(false);

        if (is3D) {
            audioNode.setPositional(true);
            float x = (float) num(cfg, "x", 0.0f);
            float y = (float) num(cfg, "y", 0.0f);
            float z = (float) num(cfg, "z", 0.0f);
            audioNode.setLocalTranslation(x, y, z);
        }

        return audioNode;
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

    private static String str(Value v, String key, String def) {
        if (v.hasMember(key)) {
            return v.getMember(key).asString();
        }
        return def;
    }

    private static double num(Value v, String key, double def) {
        if (v.hasMember(key)) {
            return v.getMember(key).asDouble();
        }
        return def;
    }

    private static boolean bool(Value v, String key, boolean def) {
        if (v.hasMember(key)) {
            return v.getMember(key).asBoolean();
        }
        return def;
    }
}