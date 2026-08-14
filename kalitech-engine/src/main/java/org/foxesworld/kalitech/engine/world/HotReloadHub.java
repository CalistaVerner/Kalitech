// FILE: org/foxesworld/kalitech/engine/world/HotReloadHub.java
package org.foxesworld.kalitech.engine.world;

import org.foxesworld.kalitech.engine.script.ScriptFailureBoundary;

import java.util.ArrayList;
import java.util.List;

public final class HotReloadHub {

    private final List<Hook> hooks = new ArrayList<>(16);

    public void register(Hook hook) {
        if (hook == null) return;
        hooks.add(hook);
    }

    public int size() {
        return hooks.size();
    }

    public void clear() {
        hooks.clear();
    }

    public void fire(String reason) {
        final String why = (reason == null || reason.isBlank()) ? "F5" : reason;
        for (int i = 0; i < hooks.size(); i++) {
            try {
                hooks.get(i).run(why);
            } catch (Throwable failure) {
                ScriptFailureBoundary.rethrowIfFatal(failure);
            }
        }
    }

    @FunctionalInterface
    public interface Hook {
        void run(String reason);
    }
}