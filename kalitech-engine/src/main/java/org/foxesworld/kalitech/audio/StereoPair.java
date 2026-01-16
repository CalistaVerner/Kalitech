// FILE: org/foxesworld/kalitech/audio/StereoPair.java
package org.foxesworld.kalitech.audio;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioKey;
import com.jme3.audio.AudioNode;
import com.jme3.audio.Filter;
import com.jme3.math.Vector3f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

/**
 * Single responsibility: owns two AudioNode channels and mirrors params.
 * Fail-soft: never throws on content issues; logs and leaves pair unattached.
 */
final class StereoPair {

    private static final Logger log = LogManager.getLogger(StereoPair.class);

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

    private static boolean isMono(AudioData d) {
        return d != null && d.getChannels() == 1;
    }

    boolean isAttached() {
        return left != null && right != null;
    }

    AudioKey getLeftKey() {
        return leftKey;
    }

    AudioKey getRightKey() {
        return rightKey;
    }

    int getDataTypeOrdinal() {
        return type == AudioData.DataType.Stream ? 1 : 0;
    }

    private static int channelsOf(AudioData d) {
        return d != null ? d.getChannels() : -1;
    }

    void setConfig(StereoConfig cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
    }

    boolean attach(AssetManager am, String leftFile, String rightFile, AudioData.DataType type) {
        Objects.requireNonNull(am, "assetManager");
        AudioKey lk = new AudioKey(leftFile, type == AudioData.DataType.Stream, true);
        AudioKey rk = new AudioKey(rightFile, type == AudioData.DataType.Stream, true);
        return attach(am, lk, rk, type);
    }

    boolean attach(AssetManager am, AudioKey leftKey, AudioKey rightKey, AudioData.DataType type) {
        Objects.requireNonNull(am, "assetManager");
        this.leftKey = leftKey;
        this.rightKey = rightKey;
        this.type = type;

        AudioData lData;
        AudioData rData;
        try {
            lData = am.loadAsset(leftKey);
            rData = am.loadAsset(rightKey);
        } catch (Throwable t) {
            log.warn("[sound] stereo3D: failed to load L/R assets: L={} R={}", leftKey.getName(), rightKey.getName(), t);
            detachInternal();
            return false;
        }

        if (!isMono(lData)) {
            log.warn("[sound] stereo3D: LEFT channel must be MONO for positional playback: {} (channels={})",
                    leftKey.getName(), channelsOf(lData));
            detachInternal();
            return false;
        }
        if (!isMono(rData)) {
            log.warn("[sound] stereo3D: RIGHT channel must be MONO for positional playback: {} (channels={})",
                    rightKey.getName(), channelsOf(rData));
            detachInternal();
            return false;
        }

        detachInternal();

        left = new AudioNode(am, leftKey.getName(), type);
        right = new AudioNode(am, rightKey.getName(), type);

        left.setPositional(true);
        right.setPositional(true);

        owner.attachChild(left);
        owner.attachChild(right);

        return true;
    }

    private void detachInternal() {
        if (left != null) owner.detachChild(left);
        if (right != null) owner.detachChild(right);
        left = null;
        right = null;
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

    void setLooping(boolean v) {
        if (!isAttached()) return;
        left.setLooping(v);
        right.setLooping(v);
    }

    void setVolume(float v) {
        if (!isAttached()) return;
        left.setVolume(v);
        right.setVolume(v);
    }

    void setPitch(float v) {
        if (!isAttached()) return;
        left.setPitch(v);
        right.setPitch(v);
    }

    void setTimeOffset(float v) {
        if (!isAttached()) return;
        left.setTimeOffset(v);
        right.setTimeOffset(v);
    }

    void setDirectional(boolean v) {
        if (!isAttached()) return;
        left.setDirectional(v);
        right.setDirectional(v);
    }

    void setDirection(Vector3f v) {
        if (!isAttached()) return;
        left.setDirection(v);
        right.setDirection(v);
    }

    void setMaxDistance(float v) {
        if (!isAttached()) return;
        left.setMaxDistance(v);
        right.setMaxDistance(v);
    }

    void setRefDistance(float v) {
        if (!isAttached()) return;
        left.setRefDistance(v);
        right.setRefDistance(v);
    }

    void setReverbEnabled(boolean v) {
        if (!isAttached()) return;
        left.setReverbEnabled(v);
        right.setReverbEnabled(v);
    }

    void setReverbFilter(Filter f) {
        if (!isAttached()) return;
        left.setReverbFilter(f);
        right.setReverbFilter(f);
    }

    void setDryFilter(Filter f) {
        if (!isAttached()) return;
        left.setDryFilter(f);
        right.setDryFilter(f);
    }
}