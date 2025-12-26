package org.foxesworld.kalitech.engine.api.impl.input;

import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.HashMap;
import java.util.Map;

final class JsMarshalling {

    private JsMarshalling() {}

    static Object vec2(double x, double y) {
        Map<String, Object> m = new HashMap<>(2);
        m.put("x", x);
        m.put("y", y);
        return ProxyObject.fromMap(m);
    }

    static Object delta(double dx, double dy) {
        Map<String, Object> m = new HashMap<>(2);
        m.put("dx", dx);
        m.put("dy", dy);
        return ProxyObject.fromMap(m);
    }
}