package org.foxesworld.kalitech.engine.world.systems.providers;

import org.foxesworld.kalitech.engine.world.systems.JsWorldSystem;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemProvider;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.foxesworld.kalitech.engine.script.util.JsCfg.str;

public final class JsWorldSystemProvider implements SystemProvider {

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
    public String id() {
        return "jsSystem";
    }

    @Override
    public KSystem create(SystemContext ctx, Value config) {
        final String module = str(config, "module", null);
        if (module == null || module.isBlank()) {
            throw new IllegalArgumentException("jsSystem requires config.module = 'Scripts/.../file.js'");
        }

        final String requestedProfile = str(config, "profile", "world").trim();

        final SystemContext.RuntimePolicy pol = ctx.runtimePolicy();
        final String resolvedProfile = (pol != null)
                ? pol.resolveProfile(requestedProfile, SystemContext.RuntimePolicy.Origin.SCRIPT_CONFIG)
                : requestedProfile;

        final Value innerCfg = (config != null && !config.isNull() && config.hasMember("config"))
                ? config.getMember("config")
                : null;

        final Object cfgJs = toProxy(innerCfg);

        return new JsWorldSystem(module, cfgJs, resolvedProfile);
    }
}