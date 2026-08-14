// FILE: org/foxesworld/kalitech/engine/api/impl/LogApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.LogApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

import java.util.Objects;

/**
 * Script-facing logging API.
 * Provides stable overloads for both one-arg and two-arg calls:
 * LOG.error("msg") and LOG.error("msg", err).
 */
public final class LogApiImpl extends AbstractApiModule implements LogApi {

    private Logger sink;

    public LogApiImpl() {
        super("log", "Log", "1.1.0");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // ------------------------------------------------------------
    // 1-arg overloads (existing contract)
    // ------------------------------------------------------------

    private static Throwable asThrowable(Object v) {
        if (v == null) return null;
        if (v instanceof Throwable t) return t;

        // LuaValueRef may wrap a host throwable
        if (v instanceof LuaValueRef val) {
            try {
                if (val.isHostObject()) {
                    Object host = val.asHostObject();
                    if (host instanceof Throwable t) return t;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static String toText(Object v) {
        if (v == null) return "null";
        if (v instanceof String s) return s;

        if (v instanceof Throwable t) {
            String m = t.getMessage();
            return (m == null || m.isBlank()) ? t.toString() : m;
        }

        if (v instanceof LuaValueRef val) {
            try {
                if (val.isNull()) return "null";
                if (val.isString()) return val.asString();
                if (val.isNumber() || val.isBoolean()) return val.toString();
                // Lua errors usually provide useful text here
                return val.toString();
            } catch (Throwable ignored) {
                return "<lua-value>";
            }
        }

        try {
            return String.valueOf(v);
        } catch (Throwable ignored) {
            return "<unstringifiable>";
        }
    }

    @Override
    public void attach(ApiContext ctx) {
        super.attach(ctx);
        this.sink = Objects.requireNonNull(ctx.log, "ctx.log");
    }

    @LuaExport
    @Override
    @ApiMethod(thread = ApiThreadRule.ANY, sync = false, flags = {ApiFlag.SANDBOX_ALLOWED}, cost = ApiCostHint.NORMAL)
    public void info(String msg) {
        profiledVoid(() -> sink.info("{}", msg));
    }

    // ------------------------------------------------------------
    // 2-arg overloads for the Lua bridge
    // ------------------------------------------------------------

    @LuaExport
    @Override
    @ApiMethod(thread = ApiThreadRule.ANY, sync = false, flags = {ApiFlag.SANDBOX_ALLOWED}, cost = ApiCostHint.NORMAL)
    public void warn(String msg) {
        profiledVoid(() -> sink.warn("{}", msg));
    }

    @LuaExport
    @Override
    @ApiMethod(thread = ApiThreadRule.ANY, sync = false, flags = {ApiFlag.SANDBOX_ALLOWED}, cost = ApiCostHint.NORMAL)
    public void error(String msg) {
        profiledVoid(() -> sink.error("{}", msg));
    }

    @LuaExport
    @Override
    @ApiMethod(thread = ApiThreadRule.ANY, sync = false, flags = {ApiFlag.SANDBOX_ALLOWED}, cost = ApiCostHint.NORMAL)
    public void debug(String msg) {
        profiledVoid(() -> sink.debug("{}", msg));
    }

    @LuaExport
    @ApiMethod(thread = ApiThreadRule.ANY, sync = false, flags = {ApiFlag.SANDBOX_ALLOWED}, cost = ApiCostHint.NORMAL)
    public void info(String msg, Object extra) {
        profiledVoid(() -> sink.info("{} {}", nullToEmpty(msg), toText(extra)));
    }

    // ------------------------------------------------------------
    // Two-value logging convenience
    // ------------------------------------------------------------

    @LuaExport
    @ApiMethod(thread = ApiThreadRule.ANY, sync = false, flags = {ApiFlag.SANDBOX_ALLOWED}, cost = ApiCostHint.NORMAL)
    public void debug(String msg, Object extra) {
        profiledVoid(() -> sink.debug("{} {}", nullToEmpty(msg), toText(extra)));
    }

    @LuaExport
    @ApiMethod(thread = ApiThreadRule.ANY, sync = false, flags = {ApiFlag.SANDBOX_ALLOWED}, cost = ApiCostHint.NORMAL)
    public void warn(String msg, Object extra) {
        profiledVoid(() -> {
            final Throwable t = asThrowable(extra);
            if (t != null) {
                sink.warn("{}", nullToEmpty(msg), t);
            } else {
                sink.warn("{} {}", nullToEmpty(msg), toText(extra));
            }
        });
    }

    @LuaExport
    @ApiMethod(thread = ApiThreadRule.ANY, sync = false, flags = {ApiFlag.SANDBOX_ALLOWED}, cost = ApiCostHint.NORMAL)
    public void error(String msg, Object extra) {
        profiledVoid(() -> {
            final Throwable t = asThrowable(extra);
            if (t != null) {
                sink.error("{}", nullToEmpty(msg), t);
            } else {
                sink.error("{} {}", nullToEmpty(msg), toText(extra));
            }
        });
    }

    @LuaExport
    @ApiMethod(thread = ApiThreadRule.ANY, sync = false, flags = {ApiFlag.SANDBOX_ALLOWED}, cost = ApiCostHint.NORMAL)
    public void info(Object a, Object b) {
        profiledVoid(() -> sink.info("{} {}", toText(a), toText(b)));
    }

    // ------------------------------------------------------------
    // Unformatted (kept)
    // ------------------------------------------------------------

    @LuaExport
    @ApiMethod(thread = ApiThreadRule.ANY, sync = false, flags = {ApiFlag.SANDBOX_ALLOWED}, cost = ApiCostHint.NORMAL)
    public void warn(Object a, Object b) {
        profiledVoid(() -> sink.warn("{} {}", toText(a), toText(b)));
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    @LuaExport
    @ApiMethod(thread = ApiThreadRule.ANY, sync = false, flags = {ApiFlag.SANDBOX_ALLOWED}, cost = ApiCostHint.NORMAL)
    public void error(Object a, Object b) {
        profiledVoid(() -> {
            // If second looks like throwable, keep stacktrace.
            final Throwable t = asThrowable(b);
            if (t != null) {
                sink.error("{}", toText(a), t);
            } else {
                sink.error("{} {}", toText(a), toText(b));
            }
        });
    }

    @LuaExport
    @ApiMethod(thread = ApiThreadRule.ANY, sync = false, flags = {ApiFlag.SANDBOX_ALLOWED}, cost = ApiCostHint.NORMAL)
    public void debug(Object a, Object b) {
        profiledVoid(() -> sink.debug("{} {}", toText(a), toText(b)));
    }

    @LuaExport
    @Override
    @ApiMethod(thread = ApiThreadRule.ANY, sync = false, flags = {ApiFlag.SANDBOX_ALLOWED}, cost = ApiCostHint.NORMAL)
    public void unformatted(String msg) {
        profiledVoid(() -> System.out.println(msg));
    }
}