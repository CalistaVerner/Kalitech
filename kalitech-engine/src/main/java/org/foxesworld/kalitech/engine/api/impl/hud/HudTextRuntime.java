// FILE: org/foxesworld/kalitech/engine/api/impl/hud/HudTextRuntime.java
package org.foxesworld.kalitech.engine.api.impl.hud;

import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.impl.hud.font.HudSdfFontManager;

import java.util.concurrent.atomic.AtomicReference;

final class HudTextRuntime {
    private static final AtomicReference<HudSdfFontManager> FONT = new AtomicReference<>();

    static void bindFontManager(HudSdfFontManager mgr) {
        FONT.set(mgr);
    }

    static HudSdfFontManager fontManagerOrNull(EngineApiImpl engine) {
        return FONT.get();
    }
}