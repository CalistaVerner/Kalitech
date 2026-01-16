// FILE: org/foxesworld/kalitech/engine/modules/particles/templates/FxLibrary.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles.templates;

import org.graalvm.polyglot.Value;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry of FX templates.
 * Allows designer-driven content without code changes.
 */
public final class FxLibrary {

    private final ConcurrentHashMap<String, FxTemplate> byName = new ConcurrentHashMap<>();

    private static String normalize(String n) {
        if (n == null) return "";
        return n.trim().toLowerCase();
    }

    public void register(String name, Value cfg) {
        byName.put(normalize(name), new FxTemplate(name, cfg));
    }

    public FxTemplate get(String name) {
        return byName.get(normalize(name));
    }

    public boolean has(String name) {
        return byName.containsKey(normalize(name));
    }

    public int size() {
        return byName.size();
    }
}