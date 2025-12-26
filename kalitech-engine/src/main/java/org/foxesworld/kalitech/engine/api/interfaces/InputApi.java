package org.foxesworld.kalitech.engine.api.interfaces;

// Author: Calista Verner

import org.graalvm.polyglot.HostAccess;

public interface InputApi {

    // Keyboard
    @HostAccess.Export
    boolean keyDown(String key);

    // Mouse absolute
    @HostAccess.Export
    double mouseX();

    @HostAccess.Export
    double mouseY();

    @HostAccess.Export
    Object cursorPosition();

    // Mouse delta
    @HostAccess.Export
    double mouseDX();

    @HostAccess.Export
    double mouseDY();

    @HostAccess.Export
    default double mouseDx() { return mouseDX(); }

    @HostAccess.Export
    int keyCode(String name);

    @HostAccess.Export
    default double mouseDy() { return mouseDY(); }

    @HostAccess.Export
    Object mouseDelta();

    @HostAccess.Export
    Object consumeMouseDelta();

    // Wheel
    @HostAccess.Export
    double wheelDelta();

    @HostAccess.Export
    double consumeWheelDelta();

    // Mouse buttons
    @HostAccess.Export
    boolean mouseDown(int button);

    // Cursor / grab
    @HostAccess.Export
    void cursorVisible(boolean visible);

    @HostAccess.Export
    boolean cursorVisible();

    @HostAccess.Export
    void grabMouse(boolean grab);

    @HostAccess.Export
    boolean grabMouse();

    // Frame lifecycle
    @HostAccess.Export
    void endFrame();

    // Debug
    @HostAccess.Export
    void debug(boolean enabled);

    @HostAccess.Export
    boolean debug();
}