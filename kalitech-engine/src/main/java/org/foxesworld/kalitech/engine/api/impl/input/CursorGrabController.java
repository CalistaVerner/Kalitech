package org.foxesworld.kalitech.engine.api.impl.input;

import com.jme3.input.InputManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;

import java.util.function.BooleanSupplier;

final class CursorGrabController {

    private static final Logger log = LogManager.getLogger(CursorGrabController.class);

    private final EngineApiImpl engine;
    private final InputManager input;
    private final MouseState mouse;
    private final BooleanSupplier debug;

    private volatile boolean cursorVisible = true;
    private volatile boolean grabbed = false;

    CursorGrabController(EngineApiImpl engine, InputManager input, MouseState mouse, BooleanSupplier debug) {
        this.engine = engine;
        this.input = input;
        this.mouse = mouse;
        this.debug = debug;
    }

    boolean isCursorVisible() { return cursorVisible; }
    boolean isGrabbed() { return grabbed; }

    void setCursorVisible(boolean visible) {
        this.cursorVisible = visible;

        engine.getApp().enqueue(() -> {
            try { input.setCursorVisible(visible); }
            catch (Exception e) { log.warn("[input] setCursorVisible({}) failed: {}", visible, e.toString()); }
            return null;
        });

        if (debug.getAsBoolean()) log.info("[input] cursorVisible={}", visible);
    }

    void setGrabbed(boolean grab) {
        this.grabbed = grab;

        // reset baselines (so no jump)
        mouse.resetBaselines();

        if (debug.getAsBoolean()) log.info("[input] grabbed={}", grab);
    }
}