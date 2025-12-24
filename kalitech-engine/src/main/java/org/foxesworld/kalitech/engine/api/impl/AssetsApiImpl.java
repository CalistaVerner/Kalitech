// FILE: AssetsApiImpl.java
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.AssetsApi;
import org.graalvm.polyglot.HostAccess;

public final class AssetsApiImpl implements AssetsApi {

    private final AssetManager assets;

    public AssetsApiImpl(EngineApiImpl engineApi) {
        this.assets = engineApi.getAssets();
    }

    @HostAccess.Export
    @Override
    public String readText(String assetPath) {
        if (assetPath == null || assetPath.isBlank()) {
            throw new IllegalArgumentException("assets.readText(path): path is empty");
        }
        Object obj = assets.loadAsset(new AssetKey<>(assetPath.trim()));
        if (obj == null) return null;
        return (obj instanceof String s) ? s : String.valueOf(obj);
    }
}