package org.foxesworld.kalitech.audio;

public record StereoConfig(float separationMeters) {

    public static final float DEFAULT_SEPARATION_METERS = 0.20f; // 20cm “AAA default”
    public static final float MIN_SEPARATION = 0.00f;
    public static final float MAX_SEPARATION = 2.00f;

    public static StereoConfig defaults() {
        return new StereoConfig(DEFAULT_SEPARATION_METERS);
    }

    public StereoConfig {
        if (Float.isNaN(separationMeters) || Float.isInfinite(separationMeters)) {
            separationMeters = DEFAULT_SEPARATION_METERS;
        }
        if (separationMeters < MIN_SEPARATION) separationMeters = MIN_SEPARATION;
        if (separationMeters > MAX_SEPARATION) separationMeters = MAX_SEPARATION;
    }

    public StereoConfig withSeparationMeters(float meters) {
        return new StereoConfig(meters);
    }
}
