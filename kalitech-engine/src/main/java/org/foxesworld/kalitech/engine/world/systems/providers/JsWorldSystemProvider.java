package org.foxesworld.kalitech.engine.world.systems.providers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.foxesworld.kalitech.engine.util.ValueCfg;
import org.foxesworld.kalitech.engine.world.systems.JsWorldSystem;
import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.registry.SystemProvider;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * jsSystem provider (AAA):
 *  - reads module from config.module
 *  - prepares JS-friendly config object (ProxyObject/ProxyArray)
 *  - DOES NOT write ctx.put("config") here (ctx is shared / stable and would be overwritten by other systems)
 *  - passes prepared config into JsWorldSystem, which will scope-bind it for each callback (init/update/destroy).
 */
public final class JsWorldSystemProvider implements SystemProvider {

    private static final Logger log = LogManager.getLogger(JsWorldSystemProvider.class);

    @Override public String id() { return "jsSystem"; }

    @Override
    public KSystem create(SystemContext ctx, Value config) {
        // 1) Resolve module from provided config
        final String module = ValueCfg.str(config, "module", null);
        if (module == null || module.isBlank()) {
            throw new IllegalArgumentException("jsSystem requires config.module = 'Scripts/.../file.js'");
        }

        // 2) (Optional) unwrap inner config if you ever start passing descriptors with { config: {...} }
        // In your current logs, unwrapped=false and config already has shadows/fog/etc for sky.
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

        // 3) Convert to JS-friendly objects (important: scripts can use cfg.shadows.mapSize directly)
        final Object cfgJs = toProxy(inner);

        // 4) Build a small descriptor (scoped later by JsWorldSystem)
        final Map<String, Object> sysDesc = new LinkedHashMap<>();
        sysDesc.put("provider", id());
        sysDesc.put("module", module);
        sysDesc.put("config", cfgJs);

        if (log.isDebugEnabled()) {
            log.debug("[jsSystem] prepared (module={}, unwrapped={})", module, unwrapped);
        }
        log.info("[jsSystem] module={}", module);

        // IMPORTANT: pass cfg into JsWorldSystem, do NOT write into ctx here
        return new JsWorldSystem(module, cfgJs, ProxyObject.fromMap(sysDesc));
    }

    // --------- Value -> Proxy converters ---------

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

        return v; // fallback
    }
}