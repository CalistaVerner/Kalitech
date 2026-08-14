package org.foxesworld.kalitech.engine.world.systems.providers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.world.systems.LuaWorldSystem;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.AbstractSystemProvider;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemDescriptor;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemModule;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemType;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;
import org.foxesworld.kalitech.engine.script.lua.LuaArray;
import org.foxesworld.kalitech.engine.script.lua.LuaObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.foxesworld.kalitech.engine.script.util.LuaCfg.bool;
import static org.foxesworld.kalitech.engine.script.util.LuaCfg.str;

/**
 * Provider for script-defined world systems implemented in Lua modules.
 */
public final class LuaWorldSystemProvider extends AbstractSystemProvider {

    private static final Logger log = LogManager.getLogger(LuaWorldSystemProvider.class);

    public LuaWorldSystemProvider() {
        super(new SystemDescriptor(
                "luaSystem",
                SystemType.SCRIPTED,
                SystemModule.scripting("world"),
                "Executes Lua-defined world systems."
        ));
    }

    private static String normalizeProfile(String s) {
        if (s == null) return "world";
        final String t = s.trim();
        return t.isEmpty() ? "world" : t;
    }

    private static Object toProxy(LuaValueRef v) {
        if (v == null || v.isNull()) return null;

        if (v.isBoolean()) return v.asBoolean();
        if (v.isNumber()) {
            if (v.fitsInInt()) return v.asInt();
            if (v.fitsInLong()) return v.asLong();
            return v.asDouble();
        }
        if (v.isString()) return v.asString();

        if (v.hasArrayElements()) {
            final long size = v.getArraySize();
            final int len = (size > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) size;
            final Object[] arr = new Object[len];
            for (int i = 0; i < len; i++) arr[i] = toProxy(v.getArrayElement(i));
            return LuaArray.fromArray(arr);
        }

        if (v.hasMembers()) {
            final Map<String, Object> map = new LinkedHashMap<>();
            for (String k : v.getMemberKeys()) {
                try {
                    map.put(k, toProxy(v.getMember(k)));
                } catch (Throwable t) {
                    map.put(k, null);
                }
            }
            return LuaObject.fromMap(map);
        }

        return v;
    }

    private static String stableSystemId(String stableId, String module) {
        if (stableId != null && !stableId.isBlank()) return stableId.trim();
        return "lua:" + ((module == null) ? "unknown" : module.trim());
    }

    private static int readInt(LuaValueRef obj, String key, int def) {
        try {
            if (obj.hasMember(key)) {
                LuaValueRef v = obj.getMember(key);
                if (v != null && !v.isNull() && v.fitsInInt()) return v.asInt();
                if (v != null && !v.isNull() && v.isNumber()) return (int) v.asDouble();
            }
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static long readLong(LuaValueRef obj, String key, long def) {
        try {
            if (obj.hasMember(key)) {
                LuaValueRef v = obj.getMember(key);
                if (v != null && !v.isNull() && v.fitsInLong()) return v.asLong();
                if (v != null && !v.isNull() && v.isNumber()) return (long) v.asDouble();
            }
        } catch (Throwable ignored) {
        }
        return def;
    }

    private static double readDouble(LuaValueRef obj, String key, double def) {
        try {
            if (obj.hasMember(key)) {
                LuaValueRef v = obj.getMember(key);
                if (v != null && !v.isNull() && v.isNumber()) return v.asDouble();
            }
        } catch (Throwable ignored) {
        }
        return def;
    }

    @Override
    public KSystem create(SystemContext ctx, LuaValueRef config) {
        Objects.requireNonNull(ctx, "ctx");

        if (config == null || config.isNull() || !config.hasMembers()) {
            throw new IllegalArgumentException("luaSystem requires config object");
        }

        final String module = str(config, "module", null);
        if (module == null || module.isBlank()) {
            throw new IllegalArgumentException("luaSystem requires config.module = '@app/<namespace>/.../file.lua'");
        }

        final String stableId = str(config, "stableId", null);

        final int order = readInt(config, "order", 0);
        final int priority = readInt(config, "priority", 50);

        final double desiredHz = readDouble(config, "desiredHz", 60.0);
        final double minHz = readDouble(config, "minHz", desiredHz);
        final double maxHz = readDouble(config, "maxHz", desiredHz);

        final long softBudgetNanos = readLong(config, "softBudgetNanos", 0L);
        final long hardBudgetNanos = readLong(config, "hardBudgetNanos", 0L);

        final boolean sandboxReq = bool(config, "sandbox", false);
        final String rtReq = str(config, "runtime", str(config, "profile", null));
        final String requestedProfile = sandboxReq ? "sandbox" : normalizeProfile(rtReq);
        final String resolvedProfile = normalizeProfile(requestedProfile);

        // Enforce runtime policy (non-null in fixed SystemContext).
        // Capability choice is conservative: sandbox requests imply UNSAFE denial,
        // but we still run through policy for uniform control.
        try {
            SystemContext.RuntimePolicy.Capability cap = sandboxReq
                    ? SystemContext.RuntimePolicy.Capability.WORLD_ACCESS
                    : SystemContext.RuntimePolicy.Capability.WORLD_ACCESS;

            ctx.runtimePolicy().assertAllowed(resolvedProfile, stableSystemId(stableId, module), cap);
        } catch (SecurityException se) {
            log.warn("[luaSystem] denied by policy: module={} stableId={} requested={} resolved={}",
                    module, stableId, requestedProfile, resolvedProfile);
            throw se;
        } catch (Throwable t) {
            log.error("[luaSystem] policy check failed: module={} stableId={}", module, stableId, t);
            throw new SecurityException("Runtime policy check failed", t);
        }

        final Object cfgLua = toProxy(config);

        final Map<String, Object> sysDesc = new LinkedHashMap<>();
        sysDesc.put("id", id());
        sysDesc.put("order", order);
        sysDesc.put("priority", priority);
        sysDesc.put("stableId", stableId);
        sysDesc.put("module", module);
        sysDesc.put("runtime", resolvedProfile);
        sysDesc.put("desiredHz", desiredHz);
        sysDesc.put("minHz", minHz);
        sysDesc.put("maxHz", maxHz);
        sysDesc.put("softBudgetNanos", softBudgetNanos);
        sysDesc.put("hardBudgetNanos", hardBudgetNanos);
        sysDesc.put("sandbox", sandboxReq);
        sysDesc.put("config", cfgLua);

        if (log.isDebugEnabled()) {
            log.debug("[luaSystem] prepared module={} stableId={} order={} priority={} requested={} resolved={}",
                    module, stableId, order, priority, requestedProfile, resolvedProfile);
        }
        log.info("[luaSystem] module={} runtime={} order={} priority={}", module, resolvedProfile, order, priority);

        // LuaWorldSystem signature preserved: (module, cfgLua, sysDescProxy, profile)
        return new LuaWorldSystem(module, cfgLua, LuaObject.fromMap(sysDesc), resolvedProfile);
    }
}
