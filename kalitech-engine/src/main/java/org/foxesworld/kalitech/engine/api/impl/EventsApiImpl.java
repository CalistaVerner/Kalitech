// FILE: org/foxesworld/kalitech/engine/api/impl/EventsApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import org.foxesworld.kalitech.engine.api.contract.*;
import org.foxesworld.kalitech.engine.api.interfaces.EventsApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.script.events.ScriptEventBus;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

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
            method(EventsApiImpl.class, "on", String.class, LuaValueRef.class);

    private static final Method M_ONCE =
            method(EventsApiImpl.class, "once", String.class, LuaValueRef.class);

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
            method(EventsApiImpl.class, "onEvent", String.class, LuaValueRef.class, String.class, int.class);

    private static final Method M_ONCE_EVENT =
            method(EventsApiImpl.class, "onceEvent", String.class, LuaValueRef.class, String.class, int.class);

    private static final Method M_ON_ANY =
            method(EventsApiImpl.class, "onAny", LuaValueRef.class, String.class, int.class);

    private static final Method M_ONCE_ANY =
            method(EventsApiImpl.class, "onceAny", LuaValueRef.class, String.class, int.class);

    private static final Method M_ON_PATTERN_4 =
            method(EventsApiImpl.class, "onPattern", String.class, LuaValueRef.class, String.class, int.class);

    private static final Method M_ON_PATTERN_2 =
            method(EventsApiImpl.class, "onPattern", String.class, LuaValueRef.class);

    private static final Method M_ONCE_PATTERN_2 =
            method(EventsApiImpl.class, "oncePattern", String.class, LuaValueRef.class);

    private static final Method M_ONCE_PATTERN_4 =
            method(EventsApiImpl.class, "oncePattern", String.class, LuaValueRef.class, String.class, int.class);

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

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public void emit(@NotNull String topic) {
        apiVoid(M_EMIT_TOPIC, new Object[]{topic}, () -> emit(topic, null));
    }

    @LuaExport
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

    @LuaExport
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public int on(@NotNull String topic, @NotNull LuaValueRef handler) {
        return profiled(() ->
                apiCall(M_ON, new Object[]{topic, handler}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.on(topic, handler);
                })
        );
    }

    @LuaExport
    @Override
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public int once(@NotNull String topic, @NotNull LuaValueRef handler) {
        return profiled(() ->
                apiCall(M_ONCE, new Object[]{topic, handler}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.once(topic, handler);
                })
        );
    }

    @LuaExport
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

    @LuaExport
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

    @LuaExport
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

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.CHEAP
    )
    public void emitEvent(@NotNull String topic, Object payload) {
        apiVoid(M_EMIT_EVENT_2, new Object[]{topic, payload}, () -> emitEvent(topic, payload, null));
    }

    @LuaExport
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

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int onEvent(@NotNull String topic, @NotNull LuaValueRef handler, String phase, int priority) {
        return profiled(() ->
                apiCall(M_ON_EVENT, new Object[]{topic, handler, phase, priority}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.onEvent(topic, handler, parsePhase(phase), priority);
                })
        );
    }

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int onceEvent(@NotNull String topic, @NotNull LuaValueRef handler, String phase, int priority) {
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

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int onAny(@NotNull LuaValueRef handler, String phase, int priority) {
        return profiled(() ->
                apiCall(M_ON_ANY, new Object[]{handler, phase, priority}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.onAny(handler, parsePhase(phase), priority);
                })
        );
    }

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int onceAny(@NotNull LuaValueRef handler, String phase, int priority) {
        return profiled(() ->
                apiCall(M_ONCE_ANY, new Object[]{handler, phase, priority}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.onceAny(handler, parsePhase(phase), priority);
                })
        );
    }

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int onPattern(@NotNull String pattern, @NotNull LuaValueRef handler, String phase, int priority) {
        return profiled(() ->
                apiCall(M_ON_PATTERN_4, new Object[]{pattern, handler, phase, priority}, () -> {
                    ScriptEventBus b = bus();
                    return (b == null) ? 0 : b.onPattern(pattern, handler, parsePhase(phase), priority);
                })
        );
    }

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int onPattern(@NotNull String pattern, @NotNull LuaValueRef handler) {
        return apiCall(M_ON_PATTERN_2, new Object[]{pattern, handler}, () -> onPattern(pattern, handler, "MAIN", 0));
    }

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int oncePattern(@NotNull String pattern, @NotNull LuaValueRef handler) {
        return apiCall(M_ONCE_PATTERN_2, new Object[]{pattern, handler}, () -> oncePattern(pattern, handler, "MAIN", 0));
    }

    @LuaExport
    @ApiMethod(
            thread = ApiThreadRule.ANY,
            sync = false,
            flags = {ApiFlag.SANDBOX_ALLOWED},
            cost = ApiCostHint.NORMAL
    )
    public int oncePattern(@NotNull String pattern, @NotNull LuaValueRef handler, String phase, int priority) {
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

    @LuaExport
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

    @LuaExport
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

    @LuaExport
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

    @LuaExport
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