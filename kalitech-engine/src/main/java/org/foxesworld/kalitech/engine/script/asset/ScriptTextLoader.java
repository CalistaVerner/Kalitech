package org.foxesworld.kalitech.engine.script.asset;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class ScriptTextLoader implements AssetLoader {

    @Override
    public Object load(AssetInfo assetInfo) throws IOException {
        try (InputStream in = assetInfo.openStream()) {
            byte[] bytes = in.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}