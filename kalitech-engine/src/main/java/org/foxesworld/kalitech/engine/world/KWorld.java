// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world;

import org.foxesworld.kalitech.engine.world.systems.KSystem;
import org.foxesworld.kalitech.engine.world.systems.SystemContext;
import org.foxesworld.kalitech.engine.world.systems.ThreadMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * KWorld
 *
 * Holds a set of {@link KSystem} instances and drives them each frame.
 * MAIN systems execute immediately on the world thread.
 * WORKER systems are submitted to {@link org.foxesworld.kalitech.engine.world.systems.SystemScheduler}
 * which may throttle by tick rate and backpressure.
 */
public final class KWorld {

    private final String name;

    private final List<Entry> systems = new ArrayList<>();

    private boolean started = false;

    public KWorld(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void addSystem(KSystem system, int order) {
        systems.add(new Entry(system, order));
        if (started) {
            throw new IllegalStateException("Cannot add system after world started");
        }
    }

    public void start(SystemContext ctx) {
        if (started) return;
        systems.sort(Comparator.comparingInt(e -> e.order));

        for (Entry e : systems) {
            ThreadMode m = e.system.threadMode();
            if (m == ThreadMode.WORKER_DEDICATED ||
                    m == ThreadMode.WORKER_STRIPED) {
                // Warm lane/runtime and run onStart on the owning worker thread.
                ctx.scheduler().ensureStarted(e.system, ctx);
            } else {
                e.system.onStart(ctx);
            }
        }

        started = true;
    }

    public void update(SystemContext ctx, float tpf) {
        if (!started) return;

        // MAIN systems: execute now on world thread
        for (Entry e : systems) {
            if (e.system.threadMode() == ThreadMode.MAIN) {
                e.system.onUpdate(ctx, tpf);
            }
        }

        // WORKER systems: scheduler decides due/backpressure
        for (Entry e : systems) {
            ThreadMode m = e.system.threadMode();
            if (m == ThreadMode.WORKER_DEDICATED ||
                    m == ThreadMode.WORKER_STRIPED) {
                ctx.scheduler().submitUpdate(e.system, ctx, tpf);
            }
        }
    }

    public void stop(SystemContext ctx) {
        if (!started) return;

        for (Entry e : systems) {
            ThreadMode m = e.system.threadMode();
            if (m == ThreadMode.WORKER_DEDICATED ||
                    m == ThreadMode.WORKER_STRIPED) {
                ctx.scheduler().stopSystem(e.system);
            } else {
                e.system.onStop(ctx);
            }
        }

        started = false;
    }

    private static final class Entry {
        final KSystem system;
        final int order;

        Entry(KSystem system, int order) {
            this.system = system;
            this.order = order;
        }
    }
}