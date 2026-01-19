// FILE: org/foxesworld/kalitech/engine/api/impl/EventsApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.contract.*;
import org.foxesworld.kalitech.engine.api.interfaces.EventsApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Script event bus facade.
 */
public final class EventsApiImpl extends AbstractApiModule implements EventsApi {

    private static final Method M_EMIT_TOPIC =
            method(EventsApiImpl.class, "emit", String.class);

    private static final Method M_EMIT_TOPIC_PAYLOAD =
            method(EventsApiImpl.class, "emit", String.class, Object.class);

    private static final Method M_ON =
            method(EventsApiImpl.class, "on", String.class, Value.class);

    private static final Method M_ONCE =
            method(EventsApiImpl.class, "once", String.class, Value.class);

    private static final Method M_OFF_TOPIC_TOKEN =
            method(EventsApiImpl.class, "off", String.class, int.class);

    private static final Method M_CLEAR_TOPIC =
            method(EventsApiImpl.class, "clear", String.class);

    private static final Method M_OFF_TOKEN =
            method(EventsApiImpl.class, "off", int.class);

    private static final Method M_EMIT_EVENT_2 =
            method(EventsApiImpl.class, "emitEvent", String.class, Object.class);

    private static final Method M_EMIT_EVENT_3 =
            method(EventsApiImpl.class, "emitEvent", String.class, Object.class, Object.class);

    private static final Method M_ON_EVENT =
            method(EventsApiImpl.class, "onEvent", String.class, Value.class, String.class, int.class);

    private static final Method M_ONCE_EVENT =
            method(EventsApiImpl.class, "onceEvent", String.class, Value.class, String.class, int.class);

    private static final Method M_ON_ANY =
            method(EventsApiImpl.class, "onAny", Value.class, String.class, int.class);

    private static final Method M_ONCE_ANY =
            method(EventsApiImpl.class, "onceAny", Value.class, String.class, int.class);

    private static final Method M_ON_PATTERN_4 =
            method(EventsApiImpl.class, "onPattern", String.class, Value.class, String.class, int.class);

    private static final Method M_ON_PATTERN_2 =
            method(EventsApiImpl.class, "onPattern", String.class, Value.class);

    private static final Method M_ONCE_PATTERN_2 =
            method(EventsApiImpl.class, "oncePattern", String.class, Value.class);

    private static final Method M_ONCE_PATTERN_4 =
            method(EventsApiImpl.class, "oncePattern", String.class, Value.class, String.class, int.class);

    private static final Method M_SET_HISTORY_MAX =
            method(EventsApiImpl.class, "setHistoryMax", int.class);

    private static final Method M_GET_HISTORY =
            method(EventsApiImpl.class, "getHistory", int.class);

    private static final Method M_QUEUED =
            method(EventsApiImpl.class, "queuedEventsApprox");

    private static final Method M_CLEAR_ALL =
            method(EventsApiImpl.class, "clearAll");

    public EventsApiImpl() {
        super("bus", "Events", "1.0.0");
    }

    private static ScriptEventBus.Phase parsePhase(String s) {
        if (s == null) return ScriptEventBus.Phase.MAIN;
        try {
            return ScriptEventBus.Phase.valueOf(s.trim().toUpperCase());
        } catch (Throwable t) {
            return ScriptEventBus.Phase.MAIN;
        }
    }

    private ScriptEventBus bus() {
        return (engine == null) ? null : engine.getBus();
    }

    // ---------------------------------------------------------------------------------
    // Topic bus (cheap, pure-ish, safe in sandbox)
    // ---------------------------------------------------------------------------------

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public void emit(@NotNull String topic) {
        apiVoid(M_EMIT_TOPIC, new Object[]{topic}, () -> emit(topic, null));
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public void emit(@NotNull String topic, Object payload) {
        profiledVoid(() ->
                apiVoid(M_EMIT_TOPIC_PAYLOAD, new Object[]{topic, payload}, () -> {
                    ScriptEventBus b = bus();
                    if (b != null) b.emit(topic, payload);
                })
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public int on(@NotNull String topic, @NotNull Value handler) {
        return profiled(() ->
                apiCall(M_ON, new Object[]{topic, handler}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.on(topic, handler);
                })
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public int once(@NotNull String topic, @NotNull Value handler) {
        return profiled(() ->
                apiCall(M_ONCE, new Object[]{topic, handler}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.once(topic, handler);
                })
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public boolean off(@NotNull String topic, @NonNegative int token) {
        return profiled(() ->
                apiCall(M_OFF_TOPIC_TOKEN, new Object[]{topic, token}, () -> {
                    ScriptEventBus b = bus();
                    return b != null && b.off(topic, token);
                })
        );
    }

    @HostAccess.Export
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public void clear(@NotNull String topic) {
        profiledVoid(() ->
                apiVoid(M_CLEAR_TOPIC, new Object[]{topic}, () -> {
                    ScriptEventBus b = bus();
                    if (b != null) b.clear(topic);
                })
        );
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public boolean off(@NonNegative int token) {
        return profiled(() ->
                apiCall(M_OFF_TOKEN, new Object[]{token}, () -> {
                    ScriptEventBus b = bus();
                    return b != null && b.off(token);
                })
        );
    }

    // ---------------------------------------------------------------------------------
    // Envelope event bus (supports meta + phases)
    // ---------------------------------------------------------------------------------

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public void emitEvent(@NotNull String topic, Object payload) {
        apiVoid(M_EMIT_EVENT_2, new Object[]{topic, payload}, () -> emitEvent(topic, payload, null));
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public void emitEvent(@NotNull String topic, Object payload, Object meta) {
        profiledVoid(() ->
                apiVoid(M_EMIT_EVENT_3, new Object[]{topic, payload, meta}, () -> {
                    ScriptEventBus b = bus();
                    if (b == null) return;

                    ScriptEventBus.Meta m = (meta instanceof ScriptEventBus.Meta mm) ? mm : null;
                    b.emitEvent(topic, payload, m);
                })
        );
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int onEvent(@NotNull String topic, @NotNull Value handler, String phase, int priority) {
        return profiled(() ->
                apiCall(M_ON_EVENT, new Object[]{topic, handler, phase, priority}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.onEvent(topic, handler, parsePhase(phase), priority);
                })
        );
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int onceEvent(@NotNull String topic, @NotNull Value handler, String phase, int priority) {
        return profiled(() ->
                apiCall(M_ONCE_EVENT, new Object[]{topic, handler, phase, priority}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.onceEvent(topic, handler, parsePhase(phase), priority);
                })
        );
    }

    // ---------------------------------------------------------------------------------
    // Any / Pattern subscriptions
    // ---------------------------------------------------------------------------------

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int onAny(@NotNull Value handler, String phase, int priority) {
        return profiled(() ->
                apiCall(M_ON_ANY, new Object[]{handler, phase, priority}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.onAny(handler, parsePhase(phase), priority);
                })
        );
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int onceAny(@NotNull Value handler, String phase, int priority) {
        return profiled(() ->
                apiCall(M_ONCE_ANY, new Object[]{handler, phase, priority}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.onceAny(handler, parsePhase(phase), priority);
                })
        );
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int onPattern(@NotNull String pattern, @NotNull Value handler, String phase, int priority) {
        return profiled(() ->
                apiCall(M_ON_PATTERN_4, new Object[]{pattern, handler, phase, priority}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.onPattern(pattern, handler, parsePhase(phase), priority);
                })
        );
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int onPattern(@NotNull String pattern, @NotNull Value handler) {
        return apiCall(M_ON_PATTERN_2, new Object[]{pattern, handler}, () -> onPattern(pattern, handler, "MAIN", 0));
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int oncePattern(@NotNull String pattern, @NotNull Value handler) {
        return apiCall(M_ONCE_PATTERN_2, new Object[]{pattern, handler}, () -> oncePattern(pattern, handler, "MAIN", 0));
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int oncePattern(@NotNull String pattern, @NotNull Value handler, String phase, int priority) {
        return profiled(() ->
                apiCall(M_ONCE_PATTERN_4, new Object[]{pattern, handler, phase, priority}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.oncePattern(pattern, handler, parsePhase(phase), priority);
                })
        );
    }

    // ---------------------------------------------------------------------------------
    // Diagnostics / history
    // ---------------------------------------------------------------------------------

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED, ApiFlag.EDITOR_VISIBLE},
            cost = ApiCostHint.CHEAP
    )
    public void setHistoryMax(@NonNegative int max) {
        profiledVoid(() ->
                apiVoid(M_SET_HISTORY_MAX, new Object[]{max}, () -> {
                    ScriptEventBus b = bus();
                    if (b != null) b.setHistoryMax(max);
                })
        );
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED, ApiFlag.EDITOR_VISIBLE},
            cost = ApiCostHint.CHEAP
    )
    public List<ScriptEventBus.EventEnvelope> getHistory(@NonNegative int limit) {
        return profiled(() ->
                apiCall(M_GET_HISTORY, new Object[]{limit}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? List.of() : b.getHistory(limit);
                })
        );
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED, ApiFlag.EDITOR_VISIBLE},
            cost = ApiCostHint.CHEAP
    )
    public int queuedEventsApprox() {
        return profiled(() ->
                apiCall(M_QUEUED, new Object[0], () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.queuedEventsApprox();
                })
        );
    }

    @HostAccess.Export
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED, ApiFlag.EDITOR_VISIBLE},
            cost = ApiCostHint.NORMAL
    )
    public void clearAll() {
        profiledVoid(() ->
                apiVoid(M_CLEAR_ALL, new Object[0], () -> {
                    ScriptEventBus b = bus();
                    if (b != null) b.clearAll();
                })
        );
    }
}