package org.foxesworld.kalitech.engine.world.systems;

import org.foxesworld.kalitech.engine.script.ScriptRuntime;
import org.graalvm.polyglot.Value;

import java.util.Objects;

public final class JsWorldSystem implements KSystem {

    private final String module;
    private final Object cfg;
    private final String runtimeProfile;

    private volatile Value exports;
    private volatile Value instance;

    public JsWorldSystem(String module, Object cfg, String runtimeProfile) {
        this.module = Objects.requireNonNull(module, "module").trim();
        if (this.module.isEmpty()) throw new IllegalArgumentException("module is blank");
        this.cfg = cfg;
        this.runtimeProfile = (runtimeProfile == null || runtimeProfile.isBlank()) ? "world" : runtimeProfile.trim();
    }

    private static void callOptional(Value obj, String member, Object... args) {
        if (obj == null || obj.isNull()) return;
        if (!obj.hasMember(member)) return;

        final Value fn = obj.getMember(member);
        if (fn == null || fn.isNull() || !fn.canExecute()) {
            throw new IllegalStateException("Member exists but not executable: " + member);
        }
        fn.execute(args);
    }

    private static void callRequired(Value obj, String member, Object... args) {
        if (obj == null || obj.isNull()) throw new IllegalStateException("JS instance is null");
        if (!obj.hasMember(member)) throw new IllegalStateException("JS instance missing required method: " + member);

        final Value fn = obj.getMember(member);
        if (fn == null || fn.isNull() || !fn.canExecute()) {
            throw new IllegalStateException("Required method not executable: " + member);
        }
        fn.execute(args);
    }

    @Override
    public void onStart(SystemContext ctx) {
        ensureInstance(ctx);
        callOptional(instance, "init");
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        ensureInstance(ctx);
        callRequired(instance, "update", tpf);
    }

    @Override
    public void onStop(SystemContext ctx) {
        final Value inst = this.instance;
        if (inst != null) callOptional(inst, "destroy");
        this.instance = null;
        this.exports = null;
    }

    private void ensureInstance(SystemContext ctx) {
        if (instance != null) return;

        final ScriptRuntime rt = Objects.requireNonNull(
                ctx.runtime(runtimeProfile),
                "JsWorldSystem requires ScriptRuntime for profile=" + runtimeProfile
        );

        final Value exp = Objects.requireNonNull(rt.require(module), "ScriptRuntime.require returned null: " + module);
        this.exports = exp;

        if (!exp.hasMember("create")) {
            throw new IllegalStateException("JS module must export create(ctx,cfg): " + module);
        }

        final Value create = exp.getMember("create");
        if (create == null || !create.canExecute()) {
            throw new IllegalStateException("JS module export 'create' is not executable: " + module);
        }

        final Value inst = create.execute(ctx, cfg);

        if (inst == null || inst.isNull()) {
            throw new IllegalStateException("JS create(ctx,cfg) returned null instance: " + module);
        }

        this.instance = inst;
    }
}