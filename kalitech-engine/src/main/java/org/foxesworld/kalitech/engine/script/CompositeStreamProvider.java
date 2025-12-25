// FILE: CompositeStreamProvider.java
package org.foxesworld.kalitech.engine.script;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

/**
 * Tries providers in order and returns first non-null stream.
 *
 * Author: Calista Verner
 */
public final class CompositeStreamProvider implements GraalScriptRuntime.ModuleStreamProvider {

    private final List<GraalScriptRuntime.ModuleStreamProvider> providers;

    public CompositeStreamProvider(List<GraalScriptRuntime.ModuleStreamProvider> providers) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
    }

    @Override
    public InputStream openStream(String moduleId) throws Exception {
        for (var p : providers) {
            InputStream in = p.openStream(moduleId);
            if (in != null) return in;
        }
        return null;
    }
}