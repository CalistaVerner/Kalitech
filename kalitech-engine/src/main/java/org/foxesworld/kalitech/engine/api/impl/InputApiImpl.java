package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.input.InputManager;
import com.jme3.math.Vector2f;
import org.foxesworld.kalitech.engine.api.interfaces.InputApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.modules.input.*;
import org.graalvm.polyglot.HostAccess;

import java.util.Arrays;
import java.util.Objects;

/**
 * Input API.
 *
 * <p>Contract:
 * <ul>
 *   <li>Raw input is collected via {@link RawCollector}.</li>
 *   <li>Mouse deltas are resilient to missing raw motion events.</li>
 *   <li>Cursor grab/visibility is controlled via {@link CursorGrabController}.</li>
 * </ul>
 */
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
    public void attach(ApiContext ctx) {
        super.attach(ctx);

        Objects.requireNonNull(ctx.app, "ctx.app");
        this.input = Objects.requireNonNull(ctx.app.getInputManager(), "ctx.app.inputManager");

        this.cursor = new CursorGrabController(ctx.engine, input, mouse);
        this.bindings = new InputBindings(input, mouse, frame);

        this.rawListener = new RawCollector(keyboard, mouse, frame);
        this.input.addRawInputListener(rawListener);
        this.bindings.installMouseAxisMappings();

        onJmeVoid("input.cursorVisible(true)", () -> {
            InputManager im = input;
            if (im != null) im.setCursorVisible(true);
        });
    }

    @Override
    public void detach() {
        try {
            InputManager im = input;
            RawCollector rl = rawListener;
            if (im != null && rl != null) {
                im.removeRawInputListener(rl);
            }
        } finally {
            rawListener = null;
            bindings = null;
            cursor = null;
            input = null;
            super.detach();
        }
    }

    /**
     * Legacy snapshot contract retained for compatibility with older scripts.
     * Prefer granular getters + {@link #endFrame()}.
     */
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
            InputBindings b = bindings;
            if (b != null) b.ensureKeyMapping(code);
            return keyboard.keyDown(code);
        });
    }

    @HostAccess.Export
    @Override
    public boolean keyDown(int keyCode) {
        return profiled(() -> {
            if (keyCode < 0) return false;
            InputBindings b = bindings;
            if (b != null) b.ensureKeyMapping(keyCode);
            return keyboard.keyDown(keyCode);
        });
    }

    @HostAccess.Export
    @Override
    public int keyCode(String name) {
        return profiled(() -> {
            int code = keyboard.keyCode(name);
            InputBindings b = bindings;
            if (b != null) b.ensureKeyMapping(code);
            return code;
        });
    }

    @HostAccess.Export
    @Override
    public double mouseX() {
        return mouse.mouseX();
    }

    @HostAccess.Export
    @Override
    public double mouseY() {
        return mouse.mouseY();
    }

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

    @HostAccess.Export
    @Override
    public double wheelDelta() {
        return mouse.peekWheel();
    }

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
            CursorGrabController c = cursor;
            if (c != null) c.setCursorVisible(visible);
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
            CursorGrabController c = cursor;
            if (c == null) return;

            c.setGrabbed(grab);
            c.setCursorVisible(!grab);
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
        InputManager im = input;
        if (im == null) return;

        try {
            Vector2f c = im.getCursorPosition();
            if (c != null) mouse.setAbsolute(c.x, c.y);
        } catch (Throwable t) {
            if (log != null && log.isDebugEnabled()) {
                log.debug("[input] cursor refresh failed", t);
            }
        }
    }
}