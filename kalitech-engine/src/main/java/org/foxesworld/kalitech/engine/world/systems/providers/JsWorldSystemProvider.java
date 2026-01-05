package org.foxesworld.kalitech.engine.world.systems.providers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.foxesworld.kalitech.engine.util.ValueCfg;
import org.foxesworld.kalitech.engine.world.WorldAppState;
import org.foxesworld.kalitech.engine.world.systems.JsWorldSystem;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemProvider;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JsWorldSystemProvider implements SystemProvider {

    private static final Logger log = LogManager.getLogger(JsWorldSystemProvider.class);

    @Override public String id() { return "jsSystem"; }

    @Override
    public KSystem create(SystemContext ctx, Value config) {
        final String module = ValueCfg.str(config, "module", null);
        if (module == null || module.isBlank()) {
            throw new IllegalArgumentException("jsSystem requires config.module = 'Scripts/.../file.js'");
        }

        // Requested by SCRIPT CONFIG:
        // config.runtime: world|ui|tools|hotreload|sandbox
        // OR config.sandbox=true (alias for runtime=sandbox)
        final boolean sandboxReq = ValueCfg.bool(config, "sandbox", false);
        final String rtReq = ValueCfg.str(config, "runtime", null);
        final String requested = sandboxReq ? "sandbox" : ((rtReq == null || rtReq.isBlank()) ? "world" : rtReq.trim());

        // Enforce CDPR contract
        final WorldAppState.RuntimePolicy policy = ctx.runtimePolicy();
        final String resolved = policy.resolveProfile(requested, WorldAppState.RequestOrigin.SCRIPT_CONFIG);

        if (!resolved.equalsIgnoreCase(normalize(requested))) {
            log.warn("[jsSystem] runtime request denied/rewritten: requested={} -> resolved={} (module={})",
                    requested, resolved, module);
        }

        // Optional unwrap inner config
        Value inner = config;
        boolean unwrapped = false;
        try {
            if (config != null && !config.isNull() && config.hasMember("config")) {
                Value c = config.getMember("config");
                if (c != null && !c.isNull()) {
                    inner = c;
                    unwrapped = true;
                }
            }
        } catch (Throwable t) {
            log.warn("[jsSystem] unwrap inner config failed: {}", t.toString());
        }

        final Object cfgJs = toProxy(inner);

        final Map<String, Object> sysDesc = new LinkedHashMap<>();
        sysDesc.put("provider", id());
        sysDesc.put("module", module);
        sysDesc.put("runtime", resolved);
        sysDesc.put("config", cfgJs);

        if (log.isDebugEnabled()) {
            log.debug("[jsSystem] prepared (module={}, unwrapped={}, runtime={} -> {})", module, unwrapped, requested, resolved);
        }
        log.info("[jsSystem] module={} runtime={}", module, resolved);

        return new JsWorldSystem(module, cfgJs, ProxyObject.fromMap(sysDesc), resolved);
    }

    private static String normalize(String s) {
        if (s == null) return "world";
        String t = s.trim().toLowerCase();
        return t.isEmpty() ? "world" : t;
    }

    private static Object toProxy(Value v) {
        if (v == null || v.isNull()) return null;

        if (v.isBoolean()) return v.asBoolean();
        if (v.isNumber())  return v.asDouble();
        if (v.isString())  return v.asString();

        if (v.hasArrayElements()) {
            long n = v.getArraySize();
            int len = (int) Math.min(n, Integer.MAX_VALUE);
            Object[] arr = new Object[len];
            for (int i = 0; i < len; i++) {
                arr[i] = toProxy(v.getArrayElement(i));
            }
            return ProxyArray.fromArray(arr);
        }

        if (v.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (String k : v.getMemberKeys()) {
                try { map.put(k, toProxy(v.getMember(k))); }
                catch (Throwable ignored) {}
            }
            return ProxyObject.fromMap(map);
        }

        return v;
    }
}