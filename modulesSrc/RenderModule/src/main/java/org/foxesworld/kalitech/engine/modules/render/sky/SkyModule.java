/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.app.SimpleApplication
 *  com.jme3.asset.AssetManager
 *  org.apache.logging.log4j.Logger
 *  org.foxesworld.kalitech.engine.script.lua.LuaValueRef
 */
package org.foxesworld.kalitech.engine.modules.render.sky;

import com.jme3.app.SimpleApplication;
import com.jme3.asset.AssetManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.modules.render.sky.SkyDomeConfig;
import org.foxesworld.kalitech.engine.modules.render.sky.SkyDomeRenderer;
import org.foxesworld.kalitech.engine.script.lua.LuaValueRef;

public final class SkyModule {
    private final SkyDomeRenderer dome;

    public SkyModule(SimpleApplication app, AssetManager assets, Logger log) {
        this.dome = new SkyDomeRenderer(app, assets, log);
    }

    public void setEnabled(boolean enabled) {
        this.dome.setEnabled(enabled);
    }

    public void skyDomeClear() {
        this.dome.clearAll();
    }

    public void skyDomeTexClear() {
        this.dome.clearTextures();
    }

    public void skyDomeTexA(String asset) {
        this.dome.setTextureA(asset);
    }

    public void skyDomeTexB(String asset) {
        this.dome.setTextureB(asset);
    }

    public void skyDomeCfg(LuaValueRef cfg) {
        this.dome.applyConfig(SkyDomeConfig.from(cfg));
    }
}

