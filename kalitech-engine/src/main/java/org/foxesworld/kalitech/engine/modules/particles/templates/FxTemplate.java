// FILE: org/foxesworld/kalitech/engine/modules/particles/templates/FxTemplate.java
// Author: Calista Verner
package org.foxesworld.kalitech.engine.modules.particles.templates;

import org.graalvm.polyglot.Value;

/**
 * FX template wrapper.
 * Holds raw JS object config (or parsed config later) and supports overrides.
 */
public final class FxTemplate {

    private final String name;
    private final Value cfg;

    public FxTemplate(String name, Value cfg) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        this.name = name;
        this.cfg = cfg;
    }

    public String name() {
        return name;
    }

    public Value cfg() {
        return cfg;
    }
}