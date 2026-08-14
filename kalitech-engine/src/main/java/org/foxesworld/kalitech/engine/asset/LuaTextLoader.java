package org.foxesworld.kalitech.engine.asset;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import org.foxesworld.kalitech.engine.script.LuaSyntaxVerifier;

import java.nio.charset.StandardCharsets;

/**
 * UTF-8 Lua asset loader with compile-time syntax validation.
 */
public final class LuaTextLoader implements AssetLoader {

    @Override
    public Object load(AssetInfo info) {
        if (info == null) throw new IllegalArgumentException("AssetInfo is null");
        String code = AssetIO.readText(info, StandardCharsets.UTF_8);
        String name = info.getKey() == null ? "<lua>" : info.getKey().getName();
        LuaSyntaxVerifier.verify(code, name);
        return code;
    }
}
