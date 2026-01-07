package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.interfaces.EventsApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.List;

public final class EventsApiImpl extends AbstractApiModule implements EventsApi {

    public EventsApiImpl() {
        super("bus", "Events", "1.0.0");
    }

    private ScriptEventBus b() {
        // keep dynamic resolve (tolerate null)
        return (engine == null) ? null : engine.getBus();
    }

    @HostAccess.Export
    @Override
    public void emit(String topic, Object payload) {
        profiledVoid(() -> {
            ScriptEventBus b = b();
            if (b == null) return;
            b.emit(topic, payload);
        });
    }

    @HostAccess.Export
    public void emit(String topic) {
        emit(topic, null);
    }

    @HostAccess.Export
    @Override
    public int on(String topic, Value handler) {
        return profiled(() -> {
            ScriptEventBus b = b();
            if (b == null) return 0;
            return b.on(topic, handler);
        });
    }

    @HostAccess.Export
    @Override
    public int once(String topic, Value handler) {
        return profiled(() -> {
            ScriptEventBus b = b();
            if (b == null) return 0;
            return b.once(topic, handler);
        });
    }

    @HostAccess.Export
    @Override
    public boolean off(String topic, int token) {
        return profiled(() -> {
            ScriptEventBus b = b();
            if (b == null) return false;
            return b.off(topic, token);
        });
    }

    @HostAccess.Export
    @Override
    public void clear(String topic) {
        profiledVoid(() -> {
            ScriptEventBus b = b();
            if (b == null) return;
            b.clear(topic);
        });
    }

    @HostAccess.Export
    public boolean off(int token) {
        return profiled(() -> {
            ScriptEventBus b = b();
            if (b == null) return false;
            return b.off(token);
        });
    }

    @HostAccess.Export
    public void emitEvent(String topic, Object payload, Object meta) {
        profiledVoid(() -> {
            ScriptEventBus b = b();
            if (b == null) return;

            ScriptEventBus.Meta m = null;
            if (meta instanceof ScriptEventBus.Meta mm) m = mm;

            b.emitEvent(topic, payload, m);
        });
    }

    @HostAccess.Export
    public void emitEvent(String topic, Object payload) {
        emitEvent(topic, payload, null);
    }

    @HostAccess.Export
    public int onEvent(String topic, Value handler, String phase, int priority) {
        return profiled(() -> {
            ScriptEventBus b = b();
            if (b == null) return 0;
            return b.onEvent(topic, handler, parsePhase(phase), priority);
        });
    }

    @HostAccess.Export
    public int onceEvent(String topic, Value handler, String phase, int priority) {
        return profiled(() -> {
            ScriptEventBus b = b();
            if (b == null) return 0;
            return b.onceEvent(topic, handler, parsePhase(phase), priority);
        });
    }

    @HostAccess.Export
    public int onAny(Value handler, String phase, int priority) {
        return profiled(() -> {
            ScriptEventBus b = b();
            if (b == null) return 0;
            return b.onAny(handler, parsePhase(phase), priority);
        });
    }

    @HostAccess.Export
    public int onceAny(Value handler, String phase, int priority) {
        return profiled(() -> {
            ScriptEventBus b = b();
            if (b == null) return 0;
            return b.onceAny(handler, parsePhase(phase), priority);
        });
    }

    @HostAccess.Export
    public int onPattern(String pattern, Value handler, String phase, int priority) {
        return profiled(() -> {
            ScriptEventBus b = b();
            if (b == null) return 0;
            return b.onPattern(pattern, handler, parsePhase(phase), priority);
        });
    }

    @HostAccess.Export
    public int oncePattern(String pattern, Value handler, String phase, int priority) {
        return profiled(() -> {
            ScriptEventBus b = b();
            if (b == null) return 0;
            return b.oncePattern(pattern, handler, parsePhase(phase), priority);
        });
    }

    @HostAccess.Export
    public int onPattern(String pattern, Value handler) {
        return onPattern(pattern, handler, "MAIN", 0);
    }

    @HostAccess.Export
    public int oncePattern(String pattern, Value handler) {
        return oncePattern(pattern, handler, "MAIN", 0);
    }

    @HostAccess.Export
    public void setHistoryMax(int max) {
        profiledVoid(() -> {
            ScriptEventBus b = b();
            if (b == null) return;
            b.setHistoryMax(max);
        });
    }

    @HostAccess.Export
    public List<ScriptEventBus.EventEnvelope> getHistory(int limit) {
        return profiled(() -> {
            ScriptEventBus b = b();
            if (b == null) return List.of();
            return b.getHistory(limit);
        });
    }

    @HostAccess.Export
    public int queuedEventsApprox() {
        return profiled(() -> {
            ScriptEventBus b = b();
            if (b == null) return 0;
            return b.queuedEventsApprox();
        });
    }

    @HostAccess.Export
    public void clearAll() {
        profiledVoid(() -> {
            ScriptEventBus b = b();
            if (b == null) return;
            b.clearAll();
        });
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