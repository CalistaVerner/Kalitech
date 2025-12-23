package org.foxesworld.kalitech.script;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.Map;

public final class GraalScriptService implements ScriptService {

    @Override
    public Object eval(String languageId, String code, Map<String, Object> bindings) {
        try (Context ctx = Context.newBuilder(languageId)
                .allowAllAccess(false)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {

            if (bindings != null) {
                for (var e : bindings.entrySet()) {
                    ctx.getBindings(languageId).putMember(e.getKey(), e.getValue());
                }
            }

            Value result = ctx.eval(languageId, code);
            return result.isNull() ? null : result.as(Object.class);
        }
    }
}