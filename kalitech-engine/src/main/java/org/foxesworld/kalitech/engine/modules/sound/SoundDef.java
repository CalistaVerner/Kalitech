// FILE: org/foxesworld/kalitech/engine/modules/sound/SoundDef.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.sound;

import com.jme3.audio.AudioData;

public final class SoundDef {

    public final String src;
    public final String leftFile;
    public final String rightFile;
    public final AudioData.DataType type;
    public final float separation;
    public final boolean is3D;
    public final boolean looping;
    public final FloatRange volume;
    public final FloatRange pitch;

    private SoundDef(
            String src,
            String leftFile,
            String rightFile,
            AudioData.DataType type,
            float separation,
            boolean is3D,
            boolean looping,
            FloatRange volume,
            FloatRange pitch
    ) {
        this.src = src;
        this.leftFile = leftFile;
        this.rightFile = rightFile;
        this.type = type;
        this.separation = separation;
        this.is3D = is3D;
        this.looping = looping;
        this.volume = volume;
        this.pitch = pitch;
    }

    public static SoundDef simple(String src) {
        return mono(src, AudioData.DataType.Buffer, false, false,
                new FloatRange(1.0f, 1.0f), new FloatRange(1.0f, 1.0f));
    }

    public static SoundDef mono(
            String src,
            AudioData.DataType type,
            boolean is3D,
            boolean looping,
            FloatRange volume,
            FloatRange pitch
    ) {
        return new SoundDef(src, "", "", type, 0.20f, is3D, looping, volume, pitch);
    }

    public static SoundDef stereo(
            String left,
            String right,
            AudioData.DataType type,
            float separation,
            boolean is3D,
            boolean looping,
            FloatRange volume,
            FloatRange pitch
    ) {
        return new SoundDef("", left, right, type, separation, is3D, looping, volume, pitch);
    }

    public boolean isStereo() {
        return SoundParsers.hasText(leftFile) && SoundParsers.hasText(rightFile);
    }

    public String protoKey() {
        if (isStereo()) {
            return "stereo|" + type + "|" + leftFile + "|" + rightFile + "|3d=" + is3D + "|sep=" + separation;
        }
        return "mono|" + type + "|" + src + "|3d=" + is3D;
    }
}