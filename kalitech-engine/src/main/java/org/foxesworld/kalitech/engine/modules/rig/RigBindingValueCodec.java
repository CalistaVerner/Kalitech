package org.foxesworld.kalitech.engine.modules.rig;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RigBindingValueCodec
 *
 * Converts RigBinding to JS object (ProxyObject) in a deterministic shape.
 */
public final class RigBindingValueCodec {

    private RigBindingValueCodec() {
    }

    public static Value toJs(Value jsContext, RigBinding binding) {
        Objects.requireNonNull(binding, "binding");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profileId", binding.profileId);

        Map<String, Object> roles = new LinkedHashMap<>();
        for (var e : binding.roleToBoneIndex.entrySet()) roles.put(e.getKey(), e.getValue());
        out.put("roles", ProxyObject.fromMap(roles));

        Map<String, Object> sockets = new LinkedHashMap<>();
        for (var e : binding.socketToBoneIndex.entrySet()) sockets.put(e.getKey(), e.getValue());
        out.put("sockets", ProxyObject.fromMap(sockets));

        Object proxy = ProxyObject.fromMap(out);

        if (jsContext != null && jsContext.hasMember("Object")) {
            try {
                Value obj = jsContext.getMember("Object");
                if (obj != null && obj.hasMember("freeze") && obj.getMember("freeze").canExecute()) {
                    return obj.getMember("freeze").execute(proxy);
                }
            } catch (Throwable ignored) {
            }
        }

        return Value.asValue(proxy);
    }
}