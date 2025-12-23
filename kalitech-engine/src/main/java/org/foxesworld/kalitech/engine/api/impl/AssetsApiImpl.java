package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import org.graalvm.polyglot.HostAccess;
import org.foxesworld.kalitech.engine.api.AssetsApi;

import java.util.Objects;

public final class AssetsApiImpl implements AssetsApi {

    private final AssetManager assets;

    public AssetsApiImpl(AssetManager assets) {
        this.assets = Objects.requireNonNull(assets, "assets");
    }

    @HostAccess.Export
    @Override
    public String readText(String assetPath) {
        return assets.loadAsset(new AssetKey<>(assetPath));
    }
}