package org.foxesworld.kalitech.engine.api.impl;


import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.TimeApi;
import org.graalvm.polyglot.Engine;

public final class TimeApiImpl implements TimeApi {

    private final EngineApiImpl api;
    public TimeApiImpl(EngineApiImpl engineApi) {
        this.api = engineApi;
    }

    private volatile double tpf;
    private final long startNs = System.nanoTime();
    private volatile long frame;

    /** Called from main update thread once per frame. */
    public void update(double tpfSeconds) {
        this.tpf = Math.max(0.0, tpfSeconds);
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