package org.foxesworld.kalitech.engine.script;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.Closeable;

public final class GraalScriptRuntime implements Closeable {

    private static final Logger log = LogManager.getLogger(GraalScriptRuntime.class);

    private final Context ctx;

    public GraalScriptRuntime() {
        log.info("Initializing GraalScriptRuntime...");

        HostAccess hostAccess = HostAccess.newBuilder(HostAccess.NONE)
                .allowAccessAnnotatedBy(HostAccess.Export.class)
                .build();

        this.ctx = Context.newBuilder("js")
                .allowAllAccess(false)
                .allowHostAccess(hostAccess)
                .allowHostClassLookup(className -> false)
                .option("engine.WarnInterpreterOnly", "false")
                .build();

        log.info("GraalJS context created");
    }

    public ScriptModule loadModule(String name, String code) {
        log.info("Loading JS module: {}", name);

        try {
            Source src = Source.newBuilder("js", code, name).build();
            log.debug("JS source built ({} chars)", code.length());

            Value obj = ctx.eval(src);
            log.debug("JS source evaluated");

            if (obj == null || obj.isNull()) {
                log.error("JS module returned null: {}", name);
                throw new IllegalStateException("JS module returned null: " + name);
            }

            if (!obj.hasMembers()) {
                log.error("JS module has no members: {}", name);
                throw new IllegalStateException("JS module must return an object with init/update/destroy: " + name);
            }

            log.info("JS module loaded successfully: {}", name);
            return new GraalScriptModule(obj);

        } catch (Exception e) {
            log.error("Failed to load JS module: {}", name, e);
            throw new RuntimeException("Failed to load JS module: " + name, e);
        }
    }

    private static final class GraalScriptModule implements ScriptModule {
        private final Value module;

        private GraalScriptModule(Value module) {
            this.module = module;
        }

        @Override
        public void init(Object api) {
            callIfExists("init", api);
        }

        @Override
        public void update(Object api, float tpf) {
            callIfExists("update", api, tpf);
        }

        @Override
        public void destroy(Object api) {
            callIfExists("destroy", api);
        }

        private void callIfExists(String fn, Object... args) {
            if (!module.hasMember(fn)) return;
            Value f = module.getMember(fn);
            if (f == null || !f.canExecute()) return;
            f.execute(args);
        }
    }

    @Override
    public void close() {
        log.info("Closing GraalScriptRuntime");
        ctx.close(true);
    }
}