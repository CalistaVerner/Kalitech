package org.foxesworld.kalitech.engine.api.impl.input;

import com.jme3.input.InputManager;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;

final class CursorGrabController {

    private final EngineApiImpl engine;
    private final InputManager input;
    private final MouseState mouse;

    private boolean cursorVisible = true;
    private boolean grabbed = false;

    CursorGrabController(EngineApiImpl engine, InputManager input, MouseState mouse) {
        this.engine = engine;
        this.input = input;
        this.mouse = mouse;
    }

    boolean isCursorVisible() { return cursorVisible; }
    boolean isGrabbed() { return grabbed; }

    void setCursorVisible(boolean visible) {
        this.cursorVisible = visible;
        engine.getApp().enqueue(() -> {
            try { input.setCursorVisible(visible); } catch (Exception ignored) {}
            return null;
        });
    }

    void setGrabbed(boolean grab) {
        this.grabbed = grab;
        mouse.resetBaselines();
    }
}