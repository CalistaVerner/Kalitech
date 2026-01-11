package org.foxesworld.kalitech.engine.world.systems.providers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.world.systems.JsWorldSystem;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemProvider;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.bool;
import static org.foxesworld.kalitech.engine.script.util.JsCfg.str;

public final class JsWorldSystemProvider implements SystemProvider {

    private static final Logger log = LogManager.getLogger(JsWorldSystemProvider.class);

    @Override
    public String id() {
        return "jsSystem";
    }

    private static String normalizeProfile(String s) {
        if (s == null) return "world";
        final String t = s.trim();
        return t.isEmpty() ? "world" : t;
    }

    private static Object toProxy(Value v) {
        if (v == null || v.isNull()) return null;

        if (v.isBoolean()) return v.asBoolean();
        if (v.isNumber()) return v.asDouble();
        if (v.isString()) return v.asString();

        if (v.hasArrayElements()) {
            final int len = (int) Math.min(v.getArraySize(), Integer.MAX_VALUE);
            final Object[] arr = new Object[len];
            for (int i = 0; i < len; i++) arr[i] = toProxy(v.getArrayElement(i));
            return ProxyArray.fromArray(arr);
        }

        if (v.hasMembers()) {
            final Map<String, Object> map = new LinkedHashMap<>();
            for (String k : v.getMemberKeys()) map.put(k, toProxy(v.getMember(k)));
            return ProxyObject.fromMap(map);
        }

        return v;
    }

    @Override
    public KSystem create(SystemContext ctx, Value config) {
        if (config == null || config.isNull() || !config.hasMembers()) {
            throw new IllegalArgumentException("jsSystem requires config object");
        }

        final String module = str(config, "module", null);
        if (module == null || module.isBlank()) {
            throw new IllegalArgumentException("jsSystem requires config.module = 'Scripts/.../file.js'");
        }

        final String stableId = str(config, "stableId", null);
        final int order = (config.hasMember("order") && config.getMember("order").fitsInInt())
                ? config.getMember("order").asInt()
                : 0;

        final boolean sandboxReq = bool(config, "sandbox", false);
        final String rtReq = str(config, "runtime", str(config, "profile", null));
        final String requested = sandboxReq ? "sandbox" : normalizeProfile(rtReq);

        final SystemContext.RuntimePolicy pol = ctx.runtimePolicy();
        final String resolved = (pol != null)
                ? pol.resolveProfile(requested, SystemContext.RuntimePolicy.Origin.SCRIPT_CONFIG)
                : normalizeProfile(requested);

        final Object cfgJs = toProxy(config);

        final Map<String, Object> sysDesc = new LinkedHashMap<>();
        sysDesc.put("id", id());
        sysDesc.put("order", order);
        sysDesc.put("stableId", stableId);
        sysDesc.put("module", module);
        sysDesc.put("runtime", resolved);
        sysDesc.put("config", cfgJs);

        if (log.isDebugEnabled()) {
            log.debug("[jsSystem] prepared module={} stableId={} order={} requested={} resolved={}",
                    module, stableId, order, requested, resolved);
        }
        log.info("[jsSystem] module={} runtime={}", module, resolved);

        return new JsWorldSystem(module, cfgJs, ProxyObject.fromMap(sysDesc), resolved);
    }
}