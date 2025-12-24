package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.graalvm.polyglot.HostAccess;
import org.foxesworld.kalitech.engine.api.interfaces.AssetsApi;

import java.util.Objects;

public final class AssetsApiImpl implements AssetsApi {

    private final AssetManager assets;

    public AssetsApiImpl(EngineApiImpl engineApi) {
        this.assets = engineApi.getAssets();
    }

    @HostAccess.Export
    @Override
    public String readText(String assetPath) {
        return assets.loadAsset(new AssetKey<>(assetPath));
    }
}