// FILE: org/foxesworld/kalitech/audio/SpatialStereoAudioNode.java
package org.foxesworld.kalitech.audio;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioData;
import com.jme3.audio.AudioKey;
import com.jme3.audio.AudioNode;
import com.jme3.audio.Filter;
import com.jme3.export.InputCapsule;
import com.jme3.export.JmeExporter;
import com.jme3.export.JmeImporter;
import com.jme3.export.OutputCapsule;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Objects;

public final class SpatialStereoAudioNode extends AudioNode {

    public static final int SAVABLE_VERSION = 2;
    private static final Logger log = LogManager.getLogger(SpatialStereoAudioNode.class);

    private StereoConfig config = StereoConfig.defaults();
    private transient StereoPair pair;
    private transient StereoPlacementStrategy placer;

    private transient boolean warnedNotReady;

    public SpatialStereoAudioNode() {
        super();
        initTransient();
    }

    public SpatialStereoAudioNode(AssetManager am, String leftFile, String rightFile, AudioData.DataType type) {
        super();
        Objects.requireNonNull(am, "assetManager");
        this.config = StereoConfig.defaults();
        initTransient();
        attachStereo(am, leftFile, rightFile, type);
    }

    public SpatialStereoAudioNode(AssetManager am, String monoFile, AudioData.DataType type) {
        this(am, monoFile, monoFile, type);
    }

    private void initTransient() {
        this.placer = new ListenerRightStereoPlacement();
        this.pair = new StereoPair(this, config);
        this.warnedNotReady = false;
    }

    public boolean attachStereo(AssetManager am, String leftFile, String rightFile, AudioData.DataType type) {
        if (pair == null) initTransient();
        boolean ok = pair.attach(am, leftFile, rightFile, type);
        applyConfigToChannels();
        updateChannelOffsets();
        warnedNotReady = false;
        return ok;
    }

    public StereoConfig getConfig() {
        return config;
    }

    public void setConfig(StereoConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        if (pair == null) initTransient();
        pair.setConfig(config);
        applyConfigToChannels();
        updateChannelOffsets();
    }

    public float getSeparation() {
        return config.separationMeters();
    }

    public void setSeparation(float meters) {
        setConfig(config.withSeparationMeters(meters));
    }

    public void setPlacementStrategy(StereoPlacementStrategy strategy) {
        this.placer = Objects.requireNonNull(strategy, "strategy");
        updateChannelOffsets();
    }

    @Override
    public void play() {
        if (!isReady()) return;
        pair.play(false);
    }

    @Override
    public void playInstance() {
        if (!isReady()) return;
        pair.play(true);
    }

    @Override
    public void stop() {
        if (pair != null) pair.stop();
    }

    @Override
    public void pause() {
        if (pair != null) pair.pause();
    }

    @Override
    public void setLooping(boolean loop) {
        super.setLooping(loop);
        if (pair != null) pair.setLooping(loop);
    }

    @Override
    public void setVolume(float volume) {
        super.setVolume(volume);
        if (pair != null) pair.setVolume(volume);
    }

    @Override
    public void setPitch(float pitch) {
        super.setPitch(pitch);
        if (pair != null) pair.setPitch(pitch);
    }

    @Override
    public void setTimeOffset(float timeOffset) {
        super.setTimeOffset(timeOffset);
        if (pair != null) pair.setTimeOffset(timeOffset);
    }

    @Override
    public void setDirectional(boolean directional) {
        super.setDirectional(directional);
        if (pair != null) pair.setDirectional(directional);
    }

    @Override
    public void setDirection(Vector3f direction) {
        super.setDirection(direction);
        if (pair != null) pair.setDirection(direction);
    }

    @Override
    public void setMaxDistance(float maxDistance) {
        super.setMaxDistance(maxDistance);
        if (pair != null) pair.setMaxDistance(maxDistance);
    }

    @Override
    public void setRefDistance(float refDistance) {
        super.setRefDistance(refDistance);
        if (pair != null) pair.setRefDistance(refDistance);
    }

    @Override
    public void setReverbEnabled(boolean reverbEnabled) {
        super.setReverbEnabled(reverbEnabled);
        if (pair != null) pair.setReverbEnabled(reverbEnabled);
    }

    @Override
    public void setReverbFilter(Filter reverbFilter) {
        super.setReverbFilter(reverbFilter);
        if (pair != null) pair.setReverbFilter(reverbFilter);
    }

    @Override
    public void setDryFilter(Filter dryFilter) {
        super.setDryFilter(dryFilter);
        if (pair != null) pair.setDryFilter(dryFilter);
    }

    @Override
    public void updateGeometricState() {
        super.updateGeometricState();
        if (pair == null || !pair.isAttached()) return;
        updateChannelOffsets();
    }

    private void updateChannelOffsets() {
        if (pair == null || !pair.isAttached()) return;

        ListenerSnapshot ls = ListenerSnapshot.tryRead();
        StereoPlacementStrategy.Result r = placer.compute(ls, getWorldTranslation(), config);

        Quaternion invWorldRot = getWorldRotation().inverse();
        Vector3f axisLocal = invWorldRot.mult(r.axisWorld());

        if (!StereoMath.isFinite(axisLocal) || axisLocal.lengthSquared() < 1e-8f) {
            axisLocal = Vector3f.UNIT_X;
        } else {
            axisLocal.normalizeLocal();
        }

        float half = 0.5f * config.separationMeters();
        pair.setLocalOffsets(axisLocal.mult(-half), axisLocal.mult(+half));
    }

    private void applyConfigToChannels() {
        if (pair == null || !pair.isAttached()) return;
        pair.setLooping(isLooping());
        pair.setVolume(getVolume());
        pair.setPitch(getPitch());
        pair.setTimeOffset(getTimeOffset());
        pair.setDirectional(isDirectional());
        pair.setDirection(getDirection());
        pair.setMaxDistance(getMaxDistance());
        pair.setRefDistance(getRefDistance());
        pair.setReverbEnabled(isReverbEnabled());
        pair.setReverbFilter(getReverbFilter());
        pair.setDryFilter(getDryFilter());
    }

    private boolean isReady() {
        boolean ok = pair != null && pair.isAttached();
        if (!ok && !warnedNotReady) {
            warnedNotReady = true;
            log.warn("[sound] SpatialStereoAudioNode is not ready: stereo channels are not attached/valid.");
        }
        return ok;
    }

    @Override
    public void write(JmeExporter ex) throws IOException {
        super.write(ex);
        OutputCapsule oc = ex.getCapsule(this);

        oc.write(config.separationMeters(), "kali_separation_m", StereoConfig.DEFAULT_SEPARATION_METERS);

        if (pair != null && pair.isAttached()) {
            oc.write(pair.getLeftKey(), "kali_left_key", null);
            oc.write(pair.getRightKey(), "kali_right_key", null);
            oc.write(pair.getDataTypeOrdinal(), "kali_dtype", 0);
        } else {
            oc.write((AudioKey) null, "kali_left_key", null);
            oc.write((AudioKey) null, "kali_right_key", null);
            oc.write(0, "kali_dtype", 0);
        }
    }

    @Override
    public void read(JmeImporter im) throws IOException {
        super.read(im);
        InputCapsule ic = im.getCapsule(this);

        float sep = ic.readFloat("kali_separation_m", StereoConfig.DEFAULT_SEPARATION_METERS);
        this.config = StereoConfig.defaults().withSeparationMeters(sep);

        initTransient();
        pair.setConfig(config);

        AudioKey lk = (AudioKey) ic.readSavable("kali_left_key", null);
        AudioKey rk = (AudioKey) ic.readSavable("kali_right_key", null);
        int dtype = ic.readInt("kali_dtype", 0);

        if (lk != null && rk != null) {
            AssetManager am = im.getAssetManager();
            AudioData.DataType type = (dtype == 1) ? AudioData.DataType.Stream : AudioData.DataType.Buffer;
            pair.attach(am, lk, rk, type);
            applyConfigToChannels();
            updateChannelOffsets();
        }
    }
}