// FILE: org/foxesworld/kalitech/engine/world/KWorld.java
// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world;

import org.foxesworld.kalitech.engine.world.systems.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class KWorld {

    private final String name;

    private final List<Entry> systems = new ArrayList<>();

    private KSystem[] mainSystems = new KSystem[0];
    private KSystem[] workerSystems = new KSystem[0];

    private boolean started = false;

    public KWorld(String name) {
        this.name = (name == null || name.isBlank()) ? "world" : name;
    }

    public String getName() {
        return name;
    }

    public boolean isStarted() {
        return started;
    }

    public void addSystem(KSystem system, int order) {
        if (started) throw new IllegalStateException("Cannot add system after world started");
        Objects.requireNonNull(system, "system");
        systems.add(new Entry(system, order));
    }

    public void start(SystemContext ctx) {
        if (started) return;
        Objects.requireNonNull(ctx, "ctx");

        systems.sort(Comparator.comparingInt(e -> e.order));

        final ArrayList<KSystem> mains = new ArrayList<>(systems.size());
        final ArrayList<KSystem> workers = new ArrayList<>(systems.size());

        for (Entry e : systems) {
            KSystem sys = e.system;
            ThreadMode m;
            try {
                m = sys.threadMode();
            } catch (Throwable t) {
                continue;
            }

            if (m == ThreadMode.WORKER_DEDICATED || m == ThreadMode.WORKER_STRIPED) workers.add(sys);
            else mains.add(sys);
        }

        mainSystems = mains.toArray(new KSystem[0]);
        workerSystems = workers.toArray(new KSystem[0]);

        for (KSystem sys : mainSystems) {
            try {
                sys.onStart(ctx);
            } catch (Throwable t) {
                ctx.log().error("[world:{}] system onStart failed: {}", name, sys, t);
            }
        }

        final SystemScheduler sch = ctx.scheduler();
        for (KSystem sys : workerSystems) {
            try {
                if (sch != null) sch.ensureStarted(sys, ctx);
                else sys.onStart(ctx);
            } catch (Throwable t) {
                ctx.log().error("[world:{}] worker start failed: {}", name, sys, t);
            }
        }

        started = true;
    }

    public void update(SystemContext ctx, float tpf) {
        if (!started) return;
        Objects.requireNonNull(ctx, "ctx");

        for (KSystem sys : mainSystems) {
            try {
                sys.onUpdate(ctx, tpf);
            } catch (Throwable t) {
                ctx.log().error("[world:{}] system onUpdate failed: {}", name, sys, t);
            }
        }

        final SystemScheduler sch = ctx.scheduler();
        for (KSystem sys : workerSystems) {
            try {
                if (sch != null) sch.submitUpdate(sys, ctx, tpf);
                else sys.onUpdate(ctx, tpf);
            } catch (Throwable t) {
                ctx.log().error("[world:{}] worker update failed: {}", name, sys, t);
            }
        }
    }

    public void stop(SystemContext ctx) {
        if (!started) return;
        Objects.requireNonNull(ctx, "ctx");

        final SystemScheduler sch = ctx.scheduler();
        for (KSystem sys : workerSystems) {
            try {
                if (sch != null) sch.stopSystem(sys);
                else sys.onStop(ctx);
            } catch (Throwable t) {
                ctx.log().error("[world:{}] worker stop failed: {}", name, sys, t);
            }
        }

        for (KSystem sys : mainSystems) {
            try {
                sys.onStop(ctx);
            } catch (Throwable t) {
                ctx.log().error("[world:{}] system onStop failed: {}", name, sys, t);
            }
        }

        started = false;
    }

    /**
     * FULL hot reload: stop all systems -> notify hot reload -> start all systems.
     * This guarantees init() is called again and all runtime singletons can be reset.
     */
    public void hotReload(SystemContext ctx, String reason) {
        Objects.requireNonNull(ctx, "ctx");

        if (started) {
            stop(ctx);
        }

        // After stop — safe to clear everything that lives across reloads.
        for (KSystem sys : mainSystems) {
            if (sys instanceof HotReloadableSystem hr) {
                try {
                    hr.onHotReload(ctx, reason);
                } catch (Throwable t) {
                    ctx.log().warn("[world:{}] hotReload reset failed: {}", name, sys, t);
                }
            }
        }
        for (KSystem sys : workerSystems) {
            if (sys instanceof HotReloadableSystem hr) {
                try {
                    hr.onHotReload(ctx, reason);
                } catch (Throwable t) {
                    ctx.log().warn("[world:{}] hotReload(worker) reset failed: {}", name, sys, t);
                }
            }
        }

        // Re-start all systems cleanly.
        start(ctx);
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