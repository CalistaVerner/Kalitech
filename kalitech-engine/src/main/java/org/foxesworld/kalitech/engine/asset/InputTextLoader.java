package org.foxesworld.kalitech.engine.asset;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class InputTextLoader implements AssetLoader {

    @Override
    public Object load(AssetInfo assetInfo) throws IOException {
        // Single pipeline (with consistent errors via describe()).
        return AssetIO.readText(assetInfo, StandardCharsets.UTF_8);
    }
}
