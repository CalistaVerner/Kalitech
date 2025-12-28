package org.foxesworld.kalitech.engine.api.interfaces;

import com.jme3.audio.AudioNode;
import org.graalvm.polyglot.Value;

public interface SoundApi {
    AudioNode create(Value cfg);
    void play(AudioNode audioNode);
    void stop(AudioNode audioNode);
    void setPosition(AudioNode audioNode, float x, float y, float z);
    void setLooping(AudioNode audioNode, boolean loop);
    void setVolume(AudioNode audioNode, float volume);
    void setPitch(AudioNode audioNode, float pitch);
    void setDirectional(AudioNode audioNode, boolean directional);
    void setMaxDistance(AudioNode audioNode, float maxDistance);
    void setReverbEnabled(AudioNode audioNode, boolean reverbEnabled);
    void setDryFilter(AudioNode audioNode, Object filter);
}
