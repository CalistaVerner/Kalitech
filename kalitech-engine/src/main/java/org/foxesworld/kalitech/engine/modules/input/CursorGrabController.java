package org.foxesworld.kalitech.engine.modules.input;

import com.jme3.input.InputManager;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;

public final class CursorGrabController {

    private final EngineApiImpl engine;
    private final InputManager input;
    private final MouseState mouse;

    private boolean cursorVisible = true;
    private boolean grabbed = false;

    public CursorGrabController(EngineApiImpl engine, InputManager input, MouseState mouse) {
        this.engine = engine;
        this.input = input;
        this.mouse = mouse;
    }

    public boolean isCursorVisible() {
        return cursorVisible;
    }

    public void setCursorVisible(boolean visible) {
        this.cursorVisible = visible;
        engine.getApp().enqueue(() -> {
            try {
                input.setCursorVisible(visible);
            } catch (Exception ignored) {
            }
            return null;
        });
    }

    public boolean isGrabbed() {
        return grabbed;
    }

    public void setGrabbed(boolean grab) {
        this.grabbed = grab;
        mouse.resetBaselines();
    }
}