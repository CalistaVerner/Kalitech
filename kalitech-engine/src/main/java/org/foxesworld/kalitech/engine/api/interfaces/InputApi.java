package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;

public interface InputApi {
    // keyboard
    @HostAccess.Export boolean keyDown(String key); // "W", "SPACE", "ESC", "A", "LEFT", etc.

    // mouse
    @HostAccess.Export double mouseX();
    @HostAccess.Export double mouseY();
    @HostAccess.Export double mouseDX();   // delta since last frame
    @HostAccess.Export double mouseDY();
    @HostAccess.Export double wheelDelta(); // since last frame
    @HostAccess.Export boolean mouseDown(int button); // 0=left 1=right 2=middle
}