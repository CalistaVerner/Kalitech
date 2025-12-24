// FILE: ScriptModuleValidator.java
package org.foxesworld.kalitech.engine.script.contract;

import org.graalvm.polyglot.Value;

import static org.foxesworld.kalitech.engine.script.contract.ScriptModuleContract.*;

public final class ScriptModuleValidator {

    public Descriptor validate(String moduleId, Value exports) {
        if (exports == null || exports.isNull()) {
            throw fail(moduleId, "exports is null");
        }

        Value metaV = member(exports, "meta");
        if (metaV == null || metaV.isNull() || !metaV.hasMembers()) {
            throw fail(moduleId, "exports.meta must be an object");
        }

        String id = asString(member(metaV, "id"), null);
        if (id == null || id.isBlank()) throw fail(moduleId, "exports.meta.id must be a non-empty string");

        String ver = asString(member(metaV, "version"), "0.0.0");

        Value system = member(exports, "system");   // optional
        Value prefabs = member(exports, "prefabs"); // optional
        Value dispose = member(exports, "dispose"); // optional

        if (dispose != null && !dispose.isNull() && !dispose.canExecute()) {
            throw fail(moduleId, "exports.dispose must be a function if present");
        }

        if (system != null && !system.isNull()) {
            boolean ok = system.canExecute() || system.hasMembers(); // factory or object
            if (!ok) throw fail(moduleId, "exports.system must be a function or an object");
        }

        return new Descriptor(moduleId, new Meta(id, ver), exports, system, prefabs, dispose);
    }

    private static Value member(Value obj, String name) {
        if (obj == null || obj.isNull()) return null;
        if (!obj.hasMember(name)) return null;
        Value v = obj.getMember(name);
        return (v == null || v.isNull()) ? null : v;
    }

    private static String asString(Value v, String def) {
        if (v == null || v.isNull()) return def;
        try { return v.asString(); } catch (Exception ignored) { return def; }
    }

    private static IllegalArgumentException fail(String moduleId, String msg) {
        return new IllegalArgumentException("Module contract violation: " + moduleId + " :: " + msg);
    }
}