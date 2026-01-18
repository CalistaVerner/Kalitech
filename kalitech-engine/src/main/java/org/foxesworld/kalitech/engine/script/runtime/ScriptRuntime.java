package org.foxesworld.kalitech.engine.script.runtime;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core script runtime.
 * Guarantees builtins initialization before any user script execution.
 */
public final class ScriptRuntime implements ScriptModuleRuntime {

    private final AtomicBoolean builtinsReady = new AtomicBoolean(false);
    private final BuiltinsRegistry builtinsRegistry;

    public ScriptRuntime(BuiltinsRegistry builtinsRegistry) {
        this.builtinsRegistry = builtinsRegistry;
    }

    @Override
    public Object require(String id) {
        ensureReady();
        return builtinsRegistry.require(id);
    }

    @Override
    public void invalidate(String id) {
        builtinsRegistry.invalidate(id);
    }

    @Override
    public void ensureReady() {
        if (builtinsReady.compareAndSet(false, true)) {
            initBuiltIns();
        }
    }

    private void initBuiltIns() {
        builtinsRegistry.init();
    }
}
