package org.foxesworld.kalitech.engine.api.impl.sound;

import com.jme3.audio.AudioData;
import com.jme3.audio.AudioNode;
import com.jme3.asset.AssetManager;
import org.foxesworld.kalitech.audio.SpatialStereoAudioNode;
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

        // Common params
        float volume = (float) num(cfg, "volume", 1.0);
        float pitch = (float) num(cfg, "pitch", 1.0);
        boolean looping = bool(cfg, "looping", false);
        boolean is3D = bool(cfg, "is3D", false);

        // Data type
        AudioData.DataType type = parseType(str(cfg, "type", "buffer"));

        // Spatial Stereo params (new, optional)
        String leftFile = str(cfg, "leftFile", "");
        String rightFile = str(cfg, "rightFile", "");
        float separation = (float) num(cfg, "separation", 0.20); // meters

        // Legacy mono/stereo file param (old)
        String soundFile = str(cfg, "soundFile", "");

        final AudioNode node;

        // Rule:
        // - If left/right provided => SpatialStereoAudioNode (true spatial stereo)
        // - Else => regular AudioNode (old behavior)
        if (hasText(leftFile) && hasText(rightFile)) {
            // Spatial stereo is meaningful only for 3D use-cases.
            // If is3D=false we still allow creation but keep it non-positional (headspace),
            // by using a regular AudioNode as fallback (since spatial stereo is not needed).
            if (!is3D) {
                // fallback headspace: pick leftFile (mono) or soundFile
                String f = hasText(soundFile) ? soundFile : leftFile;
                node = new AudioNode(assetManager, f, type);
                node.setPositional(false);
            } else {
                SpatialStereoAudioNode s = new SpatialStereoAudioNode(assetManager, leftFile, rightFile, type);
                s.setSeparation(separation);
                node = s;
                node.setPositional(true); // 3D spatial stereo
            }
        } else {
            // Legacy path: single file
            if (!hasText(soundFile)) {
                throw new IllegalArgumentException("sound.create(cfg): soundFile is required (or leftFile+rightFile)");
            }
            node = new AudioNode(assetManager, soundFile, type);

            // Old behavior: default headspace, becomes positional if is3D
            node.setPositional(is3D);
        }

        // Apply common params
        node.setVolume(volume);
        node.setPitch(pitch);
        node.setLooping(looping);

        // Position (only meaningful for 3D)
        if (is3D) {
            float x = (float) num(cfg, "x", 0.0);
            float y = (float) num(cfg, "y", 0.0);
            float z = (float) num(cfg, "z", 0.0);
            node.setLocalTranslation(x, y, z);
        }

        return node;
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

    // -------------------- helpers --------------------

    private static boolean hasText(String s) {
        return s != null && !s.isEmpty() && !s.isBlank();
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

    private static String str(Value v, String key, String def) {
        try {
            if (v.hasMember(key)) {
                Value m = v.getMember(key);
                if (m != null && !m.isNull()) return m.asString();
            }
        } catch (Exception e) { /* ignore */ }
        return def;
    }

    private static double num(Value v, String key, double def) {
        try {
            if (v.hasMember(key)) {
                Value m = v.getMember(key);
                if (m != null && !m.isNull()) return m.asDouble();
            }
        } catch (Exception e) { /* ignore */ }
        return def;
    }

    private static boolean bool(Value v, String key, boolean def) {
        try {
            if (v.hasMember(key)) {
                Value m = v.getMember(key);
                if (m != null && !m.isNull()) return m.asBoolean();
            }
        } catch (Exception e) { /* ignore */ }
        return def;
    }
}
