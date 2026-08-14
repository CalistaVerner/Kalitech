package org.foxesworld.kalitech.engine.api.interfaces;

import com.jme3.audio.AudioNode;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface SoundApi {
    AudioNode create(LuaValueRef cfg);

    @LuaExport
    long createId(LuaValueRef cfg);

    @LuaExport
    AudioNode createAndPlay(LuaValueRef cfg);

    void play(AudioNode audioNode);

    @LuaExport
    void playId(long id);

    @LuaExport
    long getSeed();

    @LuaExport
    void setSeed(long seed);

    @LuaExport
    void setDeterministic(boolean deterministic);

    void stop(AudioNode audioNode);

    @LuaExport
    void stopId(long id);

    void setPosition(AudioNode audioNode, float x, float y, float z);
    @LuaExport
    void setPositionId(long id, float x, float y, float z);

    void setLooping(AudioNode audioNode, boolean loop);
    @LuaExport
    void setLoopingId(long id, boolean loop);

    void setVolume(AudioNode audioNode, float volume);
    @LuaExport
    void setVolumeId(long id, float volume);

    void setPitch(AudioNode audioNode, float pitch);
    @LuaExport
    void setPitchId(long id, float pitch);

    void setDirectional(AudioNode audioNode, boolean directional);
    @LuaExport
    void setDirectionalId(long id, boolean directional);

    void setMaxDistance(AudioNode audioNode, float maxDistance);
    @LuaExport
    void setMaxDistanceId(long id, float maxDistance);

    void setReverbEnabled(AudioNode audioNode, boolean reverbEnabled);
    @LuaExport
    void setReverbEnabledId(long id, boolean reverbEnabled);

    void setDryFilter(AudioNode audioNode, Object filter);
    @LuaExport
    void setDryFilterId(long id, Object filter);

    @LuaExport
    void setPositionalId(long id, boolean positional);
}
