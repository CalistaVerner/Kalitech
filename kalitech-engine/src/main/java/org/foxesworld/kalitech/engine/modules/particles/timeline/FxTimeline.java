// FILE: org/foxesworld/kalitech/engine/modules/particles/timeline/FxTimeline.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles.timeline;

import java.util.Objects;

/**
 * Optional timelines for emitter parameters over normalized life.
 * If a curve is null, the parameter remains controlled by emitter defaults.
 */
public final class FxTimeline {

    private final CurveFloat rateOverLife;
    private final CurveFloat sizeOverLife;
    private final CurveColor colorOverLife;

    public FxTimeline(CurveFloat rateOverLife, CurveFloat sizeOverLife, CurveColor colorOverLife) {
        this.rateOverLife = rateOverLife;
        this.sizeOverLife = sizeOverLife;
        this.colorOverLife = colorOverLife;
    }

    public static FxTimeline empty() {
        return new FxTimeline(null, null, null);
    }

    public static FxTimeline of(CurveFloat rate, CurveFloat size, CurveColor color) {
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(color, "color");
        return new FxTimeline(rate, size, color);
    }

    public CurveFloat rateOverLife() {
        return rateOverLife;
    }

    public CurveFloat sizeOverLife() {
        return sizeOverLife;
    }

    public CurveColor colorOverLife() {
        return colorOverLife;
    }
}