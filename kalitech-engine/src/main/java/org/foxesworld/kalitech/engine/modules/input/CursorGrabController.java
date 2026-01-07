package org.foxesworld.kalitech.engine.modules.input;

import com.jme3.input.InputManager;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;

import java.util.Objects;

public final class CursorGrabController {

    private final EngineApiImpl engine;
    private final InputManager input;
    private final MouseState mouse;

    private boolean cursorVisible = true;
    private boolean grabbed = false;

    public CursorGrabController(EngineApiImpl engine, InputManager input, MouseState mouse) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.input = Objects.requireNonNull(input, "input");
        this.mouse = Objects.requireNonNull(mouse, "mouse");
    }

    public boolean isCursorVisible() {
        return cursorVisible;
    }

    public void setCursorVisible(boolean visible) {
        if (this.cursorVisible == visible) return;
        this.cursorVisible = visible;

        engine.getApp().enqueue(() -> {
            input.setCursorVisible(visible);
            return null;
        });
    }

    public boolean isGrabbed() {
        return grabbed;
    }

    public void setGrabbed(boolean grab) {
        if (this.grabbed == grab) return;
        this.grabbed = grab;
        mouse.resetBaselines();
    }
}