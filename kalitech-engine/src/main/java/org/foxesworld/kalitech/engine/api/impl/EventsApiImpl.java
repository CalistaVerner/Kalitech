// FILE: org/foxesworld/kalitech/engine/api/impl/EventsApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.EventsApi;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.Objects;

/**
 * JS-facing Events API (REDengine-style).
 *
 * IMPORTANT:
 *  - ScriptEventBus can be null early-boot, then appear later.
 *    So this API MUST NOT cache bus.
 *  - ScriptEventBus.pump() must be called once per frame from the engine main loop (WorldAppState).
 */

public final class EventsApiImpl implements EventsApi {

    private final EngineApiImpl engine;

    public EventsApiImpl(EngineApiImpl engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
    }

    private ScriptEventBus b() {
        return engine.getBus(); // ✅ dynamic resolve (never cached)
    }

    // -------------------------------------------------------------------------
    // Legacy contract (interface EventsApi)
    // -------------------------------------------------------------------------

    @HostAccess.Export
    @Override
    public void emit(String topic, Object payload) {
        ScriptEventBus b = b();
        if (b == null) return;
        b.emit(topic, payload);
    }

    @HostAccess.Export
    public void emit(String topic) {
        emit(topic, null);
    }

    @HostAccess.Export
    @Override
    public int on(String topic, Value handler) {
        ScriptEventBus b = b();
        if (b == null) return 0;
        return b.on(topic, handler);
    }

    @HostAccess.Export
    @Override
    public int once(String topic, Value handler) {
        ScriptEventBus b = b();
        if (b == null) return 0;
        return b.once(topic, handler);
    }

    @HostAccess.Export
    @Override
    public boolean off(String topic, int token) {
        ScriptEventBus b = b();
        if (b == null) return false;
        return b.off(topic, token);
    }

    @HostAccess.Export
    @Override
    public void clear(String topic) {
        ScriptEventBus b = b();
        if (b == null) return;
        b.clear(topic);
    }

    // -------------------------------------------------------------------------
    // AAA / REDengine-style additions
    // -------------------------------------------------------------------------

    /** Token-based unsubscribe (topic not required). */
    @HostAccess.Export
    public boolean off(int token) {
        ScriptEventBus b = b();
        if (b == null) return false;
        return b.off(token);
    }

    @HostAccess.Export
    public void emitEvent(String topic, Object payload, Object meta) {
        ScriptEventBus b = b();
        if (b == null) return;

        ScriptEventBus.Meta m = null;
        if (meta instanceof ScriptEventBus.Meta mm) m = mm;

        b.emitEvent(topic, payload, m);
    }

    @HostAccess.Export
    public void emitEvent(String topic, Object payload) {
        emitEvent(topic, payload, null);
    }

    @HostAccess.Export
    public int onEvent(String topic, Value handler, String phase, int priority) {
        ScriptEventBus b = b();
        if (b == null) return 0;
        return b.onEvent(topic, handler, parsePhase(phase), priority);
    }

    @HostAccess.Export
    public int onceEvent(String topic, Value handler, String phase, int priority) {
        ScriptEventBus b = b();
        if (b == null) return 0;
        return b.onceEvent(topic, handler, parsePhase(phase), priority);
    }

    @HostAccess.Export
    public int onAny(Value handler, String phase, int priority) {
        ScriptEventBus b = b();
        if (b == null) return 0;
        return b.onAny(handler, parsePhase(phase), priority);
    }

    @HostAccess.Export
    public int onceAny(Value handler, String phase, int priority) {
        ScriptEventBus b = b();
        if (b == null) return 0;
        return b.onceAny(handler, parsePhase(phase), priority);
    }

    @HostAccess.Export
    public int onPattern(String pattern, Value handler, String phase, int priority) {
        ScriptEventBus b = b();
        if (b == null) return 0;
        return b.onPattern(pattern, handler, parsePhase(phase), priority);
    }

    @HostAccess.Export
    public int oncePattern(String pattern, Value handler, String phase, int priority) {
        ScriptEventBus b = b();
        if (b == null) return 0;
        return b.oncePattern(pattern, handler, parsePhase(phase), priority);
    }

    // Back-compat overloads
    @HostAccess.Export
    public int onPattern(String pattern, Value handler) {
        return onPattern(pattern, handler, "MAIN", 0);
    }

    @HostAccess.Export
    public int oncePattern(String pattern, Value handler) {
        return oncePattern(pattern, handler, "MAIN", 0);
    }

    // -------------------------------------------------------------------------
    // History / debug / utilities
    // -------------------------------------------------------------------------

    @HostAccess.Export
    public void setHistoryMax(int max) {
        ScriptEventBus b = b();
        if (b == null) return;
        b.setHistoryMax(max);
    }

    @HostAccess.Export
    public List<ScriptEventBus.EventEnvelope> getHistory(int limit) {
        ScriptEventBus b = b();
        if (b == null) return List.of();
        return b.getHistory(limit);
    }

    @HostAccess.Export
    public int queuedEventsApprox() {
        ScriptEventBus b = b();
        if (b == null) return 0;
        return b.queuedEventsApprox();
    }

    @HostAccess.Export
    public void clearAll() {
        ScriptEventBus b = b();
        if (b == null) return;
        b.clearAll();
    }

    private static ScriptEventBus.Phase parsePhase(String s) {
        if (s == null) return ScriptEventBus.Phase.MAIN;
        try {
            return ScriptEventBus.Phase.valueOf(s.trim().toUpperCase());
        } catch (Throwable ignored) {
            return ScriptEventBus.Phase.MAIN;
        }
    }
}