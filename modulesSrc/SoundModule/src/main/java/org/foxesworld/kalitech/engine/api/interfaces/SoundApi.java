/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.api.interfaces;

import com.jme3.audio.AudioNode;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public interface SoundApi {
    public AudioNode create(LuaValueRef var1);

    @LuaExport
    public long createId(LuaValueRef var1);

    @LuaExport
    public AudioNode createAndPlay(LuaValueRef var1);

    public void play(AudioNode var1);

    @LuaExport
    public void playId(long var1);

    @LuaExport
    public long getSeed();

    @LuaExport
    public void setSeed(long var1);

    @LuaExport
    public void setDeterministic(boolean var1);

    public void stop(AudioNode var1);

    @LuaExport
    public void stopId(long var1);

    public void setPosition(AudioNode var1, float var2, float var3, float var4);

    @LuaExport
    public void setPositionId(long var1, float var3, float var4, float var5);

    public void setLooping(AudioNode var1, boolean var2);

    @LuaExport
    public void setLoopingId(long var1, boolean var3);

    public void setVolume(AudioNode var1, float var2);

    @LuaExport
    public void setVolumeId(long var1, float var3);

    public void setPitch(AudioNode var1, float var2);

    @LuaExport
    public void setPitchId(long var1, float var3);

    public void setDirectional(AudioNode var1, boolean var2);

    @LuaExport
    public void setDirectionalId(long var1, boolean var3);

    public void setMaxDistance(AudioNode var1, float var2);

    @LuaExport
    public void setMaxDistanceId(long var1, float var3);

    public void setReverbEnabled(AudioNode var1, boolean var2);

    @LuaExport
    public void setReverbEnabledId(long var1, boolean var3);

    public void setDryFilter(AudioNode var1, Object var2);

    @LuaExport
    public void setDryFilterId(long var1, Object var3);

    @LuaExport
    public void setPositionalId(long var1, boolean var3);
}

