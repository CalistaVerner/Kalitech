package org.foxesworld.kalitech.engine.api.interfaces;

import com.jme3.audio.AudioNode;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public interface SoundApi {
    AudioNode create(Value cfg);

    @HostAccess.Export
    long createId(Value cfg);

    @HostAccess.Export
    AudioNode createAndPlay(Value cfg);

    void play(AudioNode audioNode);

    @HostAccess.Export
    void playId(long id);

    @HostAccess.Export
    long getSeed();

    @HostAccess.Export
    void setSeed(long seed);

    @HostAccess.Export
    void setDeterministic(boolean deterministic);

    void stop(AudioNode audioNode);

    @HostAccess.Export
    void stopId(long id);

    void setPosition(AudioNode audioNode, float x, float y, float z);
    @HostAccess.Export
    void setPositionId(long id, float x, float y, float z);

    void setLooping(AudioNode audioNode, boolean loop);
    @HostAccess.Export
    void setLoopingId(long id, boolean loop);

    void setVolume(AudioNode audioNode, float volume);
    @HostAccess.Export
    void setVolumeId(long id, float volume);

    void setPitch(AudioNode audioNode, float pitch);
    @HostAccess.Export
    void setPitchId(long id, float pitch);

    void setDirectional(AudioNode audioNode, boolean directional);
    @HostAccess.Export
    void setDirectionalId(long id, boolean directional);

    void setMaxDistance(AudioNode audioNode, float maxDistance);
    @HostAccess.Export
    void setMaxDistanceId(long id, float maxDistance);

    void setReverbEnabled(AudioNode audioNode, boolean reverbEnabled);
    @HostAccess.Export
    void setReverbEnabledId(long id, boolean reverbEnabled);

    void setDryFilter(AudioNode audioNode, Object filter);
    @HostAccess.Export
    void setDryFilterId(long id, Object filter);

    @HostAccess.Export
    void setPositionalId(long id, boolean positional);
}
