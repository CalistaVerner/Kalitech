package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.input.InputManager;
import com.jme3.math.Vector2f;
import org.foxesworld.kalitech.engine.api.interfaces.InputApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.modules.input.*;
import org.graalvm.polyglot.HostAccess;

import java.util.Arrays;

public final class InputApiImpl extends AbstractApiModule implements InputApi {

    private InputManager input;

    private long frameId = 0;

    private final InputFrame frame = new InputFrame();
    private final KeyboardState keyboard = new KeyboardState();
    private final MouseState mouse = new MouseState();

    private CursorGrabController cursor;
    private InputBindings bindings;
    private RawCollector rawListener;

    public InputApiImpl() {
        super("input", "Input", "1.0.0");
    }

    @Override
    public void attach(org.foxesworld.kalitech.engine.api.module.ApiContext ctx) {
        super.attach(ctx);

        this.input = ctx.app.getInputManager();

        this.cursor = new CursorGrabController(ctx.engine, input, mouse);
        this.bindings = new InputBindings(input, mouse, frame);

        this.rawListener = new RawCollector(keyboard, mouse, frame);
        this.input.addRawInputListener(rawListener);
        this.bindings.installMouseAxisMappings();

        ctx.app.enqueue(() -> {
            try { input.setCursorVisible(true); } catch (Exception ignored) {}
            return null;
        });
    }

    @Override
    public void detach() {
        try {
            if (input != null && rawListener != null) {
                try {
                    input.removeRawInputListener(rawListener);
                } catch (Throwable ignored) {
                }
            }
        } finally {
            rawListener = null;
            bindings = null;
            cursor = null;
            input = null;
            super.detach();
        }
    }

    @HostAccess.Export
    @Deprecated
    public Object consumeSnapshot() {
        return profiled(() -> {
            refreshAbsoluteCursorBestEffort();

            keyboard.advanceFrame();

            mouse.ensureFallbackDeltaIfNeeded(cursor != null && cursor.isGrabbed(), frame.motionThisFrame());
            MouseState.Consumed c = mouse.consumeDeltasAndWheel();

            return new InputSnapshot(
                    frameId,
                    System.nanoTime(),
                    mouse.mouseX(), mouse.mouseY(),
                    c.dx(), c.dy(),
                    c.wheel(),
                    mouse.peekMouseMask(),
                    cursor != null && cursor.isGrabbed(),
                    cursor != null && cursor.isCursorVisible(),
                    keyboard.copyPressedKeyCodes(),
                    Arrays.copyOf(keyboard.justPressed(), keyboard.justPressed().length),
                    Arrays.copyOf(keyboard.justReleased(), keyboard.justReleased().length)
            );
        });
    }

    @HostAccess.Export
    @Override
    public boolean keyDown(String key) {
        return profiled(() -> {
            int code = keyboard.keyCode(key);
            if (bindings != null) bindings.ensureKeyMapping(code);
            return keyboard.keyDown(code);
        });
    }

    @HostAccess.Export
    @Override
    public boolean keyDown(int keyCode) {
        return profiled(() -> {
            if (keyCode < 0) return false;
            if (bindings != null) bindings.ensureKeyMapping(keyCode);
            return keyboard.keyDown(keyCode);
        });
    }

    @HostAccess.Export
    @Override
    public int keyCode(String name) {
        return profiled(() -> {
            int code = keyboard.keyCode(name);
            if (bindings != null) bindings.ensureKeyMapping(code);
            return code;
        });
    }

    @HostAccess.Export @Override public double mouseX() { return mouse.mouseX(); }
    @HostAccess.Export @Override public double mouseY() { return mouse.mouseY(); }

    @HostAccess.Export
    @Override
    public Object cursorPosition() {
        return profiled(() -> {
            refreshAbsoluteCursorBestEffort();
            return JsMarshalling.vec2(mouse.mouseX(), mouse.mouseY());
        });
    }

    @HostAccess.Export
    @Override
    public double mouseDX() {
        return mouseDx();
    }

    @HostAccess.Export
    @Override
    public double mouseDY() {
        return mouseDy();
    }

    @HostAccess.Export
    @Override
    public double mouseDx() {
        return profiled(() -> {
            refreshAbsoluteCursorBestEffort();
            mouse.ensureFallbackDeltaIfNeeded(cursor != null && cursor.isGrabbed(), frame.motionThisFrame());
            return mouse.mouseDx();
        });
    }

    @HostAccess.Export
    @Override
    public double mouseDy() {
        return profiled(() -> {
            refreshAbsoluteCursorBestEffort();
            mouse.ensureFallbackDeltaIfNeeded(cursor != null && cursor.isGrabbed(), frame.motionThisFrame());
            return mouse.mouseDy();
        });
    }

    @HostAccess.Export
    @Override
    public Object mouseDelta() {
        return profiled(() -> {
            refreshAbsoluteCursorBestEffort();
            mouse.ensureFallbackDeltaIfNeeded(cursor != null && cursor.isGrabbed(), frame.motionThisFrame());
            return JsMarshalling.delta2(mouse.mouseDx(), mouse.mouseDy());
        });
    }

    @HostAccess.Export
    @Override
    public Object consumeMouseDelta() {
        return profiled(() -> {
            refreshAbsoluteCursorBestEffort();
            mouse.ensureFallbackDeltaIfNeeded(cursor != null && cursor.isGrabbed(), frame.motionThisFrame());
            MouseState.Consumed c = mouse.consumeDeltasOnly();
            return JsMarshalling.delta2(c.dx(), c.dy());
        });
    }

    @HostAccess.Export @Override public double wheelDelta() { return mouse.peekWheel(); }

    @HostAccess.Export
    @Override
    public double consumeWheelDelta() {
        return profiled(mouse::consumeWheelOnly);
    }

    @HostAccess.Export
    @Override
    public boolean mouseDown(int button) {
        return profiled(() -> mouse.mouseDown(button));
    }

    @HostAccess.Export
    @Override
    public void cursorVisible(boolean visible) {
        profiledVoid(() -> {
            if (cursor != null) cursor.setCursorVisible(visible);
        });
    }

    @HostAccess.Export
    @Override
    public boolean cursorVisible() {
        return profiled(() -> cursor != null && cursor.isCursorVisible());
    }

    @HostAccess.Export
    @Override
    public void grabMouse(boolean grab) {
        profiledVoid(() -> {
            if (cursor == null) return;
            cursor.setGrabbed(grab);
            cursor.setCursorVisible(!grab);
            mouse.resetBaselines();
        });
    }

    @HostAccess.Export
    @Override
    public boolean grabbed() {
        return profiled(() -> cursor != null && cursor.isGrabbed());
    }

    @HostAccess.Export
    @Override
    public void endFrame() {
        profiledVoid(() -> {
            refreshAbsoluteCursorBestEffort();
            frame.endFrame();
            frameId++;
        });
    }

    private void refreshAbsoluteCursorBestEffort() {
        try {
            if (input == null) return;
            Vector2f c = input.getCursorPosition();
            if (c != null) mouse.setAbsolute(c.x, c.y);
        } catch (Exception ignored) {}
    }
}