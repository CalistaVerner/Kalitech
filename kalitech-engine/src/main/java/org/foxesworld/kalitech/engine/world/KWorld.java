// FILE: org/foxesworld/kalitech/engine/world/KWorld.java
// Author: KΛYLΛ
package org.foxesworld.kalitech.engine.world;

import org.foxesworld.kalitech.engine.api.EngineApiImpl;
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
            final KSystem sys = e.system;
            ThreadMode m;
            try {
                m = sys.threadMode();
            } catch (Throwable ignored) {
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
        ctx.events().pump();
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

    public void hotReload(SystemContext ctx, String reason) {
        Objects.requireNonNull(ctx, "ctx");
        final String why = (reason == null || reason.isBlank()) ? "F5" : reason;

        if (started) stop(ctx);

        // 1) global hooks (no hardcode: modules register themselves)
        try {
            ctx.hotReloadHub().fire(why);
        } catch (Throwable t) {
            ctx.log().warn("[world:{}] hotReload hub.fire failed: {}", name, t.toString());
        }

        // 2) per-system hooks (cache invalidation etc.)
        for (KSystem sys : mainSystems) {
            if (sys instanceof HotReloadableSystem hr) {
                try {
                    hr.onHotReload(ctx, why);
                } catch (Throwable ignored) {
                }
            }
        }
        for (KSystem sys : workerSystems) {
            if (sys instanceof HotReloadableSystem hr) {
                try {
                    hr.onHotReload(ctx, why);
                } catch (Throwable ignored) {
                }
            }
        }

        try {
            if (ctx.api() instanceof EngineApiImpl impl) {
                impl.__resetWorldState(why);
            }
        } catch (Throwable t) {
            ctx.log().warn("[world:{}] reset failed: {}", name, t.toString());
        }

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
