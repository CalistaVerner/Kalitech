package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.interfaces.TimeApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;

public final class TimeApiImpl extends AbstractApiModule implements TimeApi {

    private volatile double tpf;
    private final long startNs = System.nanoTime();
    private volatile long frame;

    public TimeApiImpl() {
        super("time", "Time", "1.0.0");
    }

    /** Called from main update thread once per frame. */
    public void update(double tpfSeconds) {
        // keep hot path minimal
        if (!(tpfSeconds > 0.0) || !Double.isFinite(tpfSeconds)) tpfSeconds = 0.0;
        this.tpf = tpfSeconds;
        this.frame++;
    }

    @Override public double tpf() { return tpf; }
    @Override public double dt()  { return tpf; }

    @Override
    public double now() {
        long ns = System.nanoTime() - startNs;
        return ns / 1_000_000_000.0;
    }

    @Override public long frame() { return frame; }
}