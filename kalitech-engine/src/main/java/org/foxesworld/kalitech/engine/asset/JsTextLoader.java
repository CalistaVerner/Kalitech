package org.foxesworld.kalitech.engine.asset;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;
import org.foxesworld.kalitech.engine.script.JsSyntaxVerifier;

import java.nio.charset.StandardCharsets;

public final class JsTextLoader implements AssetLoader {

    @Override
    public Object load(AssetInfo info) {
        if (info == null) throw new IllegalArgumentException("AssetInfo is null");

        String code = AssetIO.readText(info, StandardCharsets.UTF_8);

        String name = (info.getKey() != null) ? info.getKey().getName() : "<js>";
        JsSyntaxVerifier.verify(code, name);

        return code;
    }
}