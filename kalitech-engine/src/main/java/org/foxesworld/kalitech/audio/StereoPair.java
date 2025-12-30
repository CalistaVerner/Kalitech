package org.foxesworld.kalitech.audio;

import com.jme3.audio.*;
import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;

import java.util.Objects;

/**
 * Single responsibility: owns two AudioNode channels and mirrors params.
 */
final class StereoPair {

    private final SpatialStereoAudioNode owner;
    private StereoConfig cfg;

    private AudioNode left;
    private AudioNode right;

    private AudioKey leftKey;
    private AudioKey rightKey;
    private AudioData.DataType type = AudioData.DataType.Buffer;

    StereoPair(SpatialStereoAudioNode owner, StereoConfig cfg) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    void setConfig(StereoConfig cfg) { this.cfg = Objects.requireNonNull(cfg); }

    boolean isAttached() { return left != null && right != null; }

    AudioKey getLeftKey() { return leftKey; }
    AudioKey getRightKey() { return rightKey; }
    int getDataTypeOrdinal() { return type == AudioData.DataType.Stream ? 1 : 0; }

    void attach(AssetManager am, String leftFile, String rightFile, AudioData.DataType type) {
        Objects.requireNonNull(am, "assetManager");
        this.leftKey = new AudioKey(leftFile, type == AudioData.DataType.Stream, true);
        this.rightKey = new AudioKey(rightFile, type == AudioData.DataType.Stream, true);
        attach(am, leftKey, rightKey, type);
    }

    void attach(AssetManager am, AudioKey leftKey, AudioKey rightKey, AudioData.DataType type) {
        Objects.requireNonNull(am, "assetManager");
        this.leftKey = leftKey;
        this.rightKey = rightKey;
        this.type = type;

        // clean old
        if (left != null) owner.detachChild(left);
        if (right != null) owner.detachChild(right);

        left = new AudioNode(am, leftKey.getName(), type);
        right = new AudioNode(am, rightKey.getName(), type);

        // positional is mandatory for spatial stereo
        left.setPositional(true);
        right.setPositional(true);

        // ensure mono for positional correctness
        enforceMono(left, "left");
        enforceMono(right, "right");

        owner.attachChild(left);
        owner.attachChild(right);
    }

    private static void enforceMono(AudioNode n, String tag) {
        AudioData d = n.getAudioData();
        if (d != null && d.getChannels() > 1) {
            throw new IllegalStateException("SpatialStereoAudioNode: " + tag + " channel must be MONO. Got " + d.getChannels() + "ch");
        }
    }

    void setLocalOffsets(Vector3f leftOffset, Vector3f rightOffset) {
        if (!isAttached()) return;
        left.setLocalTranslation(leftOffset);
        right.setLocalTranslation(rightOffset);
    }

    void play(boolean instance) {
        if (!isAttached()) return;
        if (instance) {
            left.playInstance();
            right.playInstance();
        } else {
            left.play();
            right.play();
        }
    }

    void stop() {
        if (!isAttached()) return;
        left.stop();
        right.stop();
    }

    void pause() {
        if (!isAttached()) return;
        left.pause();
        right.pause();
    }

    // mirror params
    void setLooping(boolean v){ if(isAttached()){ left.setLooping(v); right.setLooping(v);} }
    void setVolume(float v){ if(isAttached()){ left.setVolume(v); right.setVolume(v);} }
    void setPitch(float v){ if(isAttached()){ left.setPitch(v); right.setPitch(v);} }
    void setTimeOffset(float v){ if(isAttached()){ left.setTimeOffset(v); right.setTimeOffset(v);} }
    void setDirectional(boolean v){ if(isAttached()){ left.setDirectional(v); right.setDirectional(v);} }
    void setDirection(Vector3f v){ if(isAttached()){ left.setDirection(v); right.setDirection(v);} }
    void setMaxDistance(float v){ if(isAttached()){ left.setMaxDistance(v); right.setMaxDistance(v);} }
    void setRefDistance(float v){ if(isAttached()){ left.setRefDistance(v); right.setRefDistance(v);} }
    void setReverbEnabled(boolean v){ if(isAttached()){ left.setReverbEnabled(v); right.setReverbEnabled(v);} }
    void setReverbFilter(Filter f){ if(isAttached()){ left.setReverbFilter(f); right.setReverbFilter(f);} }
    void setDryFilter(Filter f){ if(isAttached()){ left.setDryFilter(f); right.setDryFilter(f);} }
}