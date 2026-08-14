package org.foxesworld.kalitech.engine.script;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.lib.jse.JsePlatform;

/**
 * Compiles Lua text without executing it.
 */
public final class LuaSyntaxVerifier {

    private static final ThreadLocal<Globals> VALIDATORS =
            ThreadLocal.withInitial(JsePlatform::standardGlobals);

    private LuaSyntaxVerifier() {
    }

    public static void verify(String luaCode, String virtualName) {
        if (luaCode == null) throw new IllegalArgumentException("luaCode is null");
        String name = virtualName == null || virtualName.isBlank()
                ? "<lua>"
                : virtualName;
        try {
            VALIDATORS.get().load(luaCode, name);
        } catch (LuaError error) {
            throw new IllegalArgumentException(
                    "Lua syntax error in " + name + ": " + error.getMessage(),
                    error
            );
        }
    }
}
