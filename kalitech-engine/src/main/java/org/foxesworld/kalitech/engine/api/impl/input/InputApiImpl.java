package org.foxesworld.kalitech.engine.api.impl.input;

import com.jme3.input.InputManager;
import com.jme3.math.Vector2f;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.InputApi;
import org.graalvm.polyglot.HostAccess;

import java.util.Arrays;

public final class InputApiImpl implements InputApi {

    private final EngineApiImpl engine;
    private final InputManager input;

    private long frameId = 0;

    private final InputFrame frame = new InputFrame();
    private final KeyboardState keyboard = new KeyboardState();
    private final MouseState mouse = new MouseState();
    private final CursorGrabController cursor;
    private final InputBindings bindings;

    public InputApiImpl(EngineApiImpl engineApi) {
        this.engine = engineApi;
        this.input = engineApi.getApp().getInputManager();

        this.cursor = new CursorGrabController(engineApi, input, mouse);
        this.bindings = new InputBindings(input, mouse, frame);

        this.input.addRawInputListener(new RawCollector(keyboard, mouse, frame));
        this.bindings.installMouseAxisMappings();

        engineApi.getApp().enqueue(() -> {
            try { input.setCursorVisible(true); } catch (Exception ignored) {}
            return null;
        });
    }

    @HostAccess.Export
    public Object consumeSnapshot() {
        refreshAbsoluteCursorBestEffort();

        keyboard.advanceFrame();

        mouse.ensureFallbackDeltaIfNeeded(cursor.isGrabbed(), frame.motionThisFrame());
        MouseState.Consumed c = mouse.consumeDeltasAndWheel();

        InputSnapshot snap = new InputSnapshot(
                frameId,
                System.nanoTime(),
                mouse.mouseX(), mouse.mouseY(),
                c.dx(), c.dy(),
                c.wheel(),
                mouse.peekMouseMask(),
                cursor.isGrabbed(),
                cursor.isCursorVisible(),
                keyboard.copyPressedKeyCodes(),
                Arrays.copyOf(keyboard.justPressed(), keyboard.justPressed().length),
                Arrays.copyOf(keyboard.justReleased(), keyboard.justReleased().length)
        );

        return snap;
    }

    @HostAccess.Export
    @Override
    public boolean keyDown(String key) {
        int code = keyboard.keyCode(key);
        bindings.ensureKeyMapping(code, keyboard);
        return keyboard.keyDown(code);
    }

    @HostAccess.Export
    @Override
    public boolean keyDown(int keyCode) {
        if (keyCode < 0) return false;
        bindings.ensureKeyMapping(keyCode, keyboard);
        return keyboard.keyDown(keyCode);
    }

    @HostAccess.Export
    @Override
    public int keyCode(String name) {
        int code = keyboard.keyCode(name);
        bindings.ensureKeyMapping(code, keyboard);
        return code;
    }

    @HostAccess.Export @Override public double mouseX() { return mouse.mouseX(); }
    @HostAccess.Export @Override public double mouseY() { return mouse.mouseY(); }

    @HostAccess.Export
    @Override
    public Object cursorPosition() {
        refreshAbsoluteCursorBestEffort();
        return JsMarshalling.vec2(mouse.mouseX(), mouse.mouseY());
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
        refreshAbsoluteCursorBestEffort();
        mouse.ensureFallbackDeltaIfNeeded(cursor.isGrabbed(), frame.motionThisFrame());
        return mouse.mouseDx();
    }

    @HostAccess.Export
    @Override
    public double mouseDy() {
        refreshAbsoluteCursorBestEffort();
        mouse.ensureFallbackDeltaIfNeeded(cursor.isGrabbed(), frame.motionThisFrame());
        return mouse.mouseDy();
    }

    @HostAccess.Export
    @Override
    public Object mouseDelta() {
        refreshAbsoluteCursorBestEffort();
        mouse.ensureFallbackDeltaIfNeeded(cursor.isGrabbed(), frame.motionThisFrame());
        return JsMarshalling.delta2(mouse.mouseDx(), mouse.mouseDy());
    }

    @HostAccess.Export
    @Override
    public Object consumeMouseDelta() {
        refreshAbsoluteCursorBestEffort();
        mouse.ensureFallbackDeltaIfNeeded(cursor.isGrabbed(), frame.motionThisFrame());
        MouseState.Consumed c = mouse.consumeDeltasOnly();
        return JsMarshalling.delta2(c.dx(), c.dy());
    }

    @HostAccess.Export @Override public double wheelDelta() { return mouse.peekWheel(); }

    @HostAccess.Export
    @Override
    public double consumeWheelDelta() {
        return mouse.consumeWheelOnly();
    }

    @HostAccess.Export
    @Override
    public boolean mouseDown(int button) {
        return mouse.mouseDown(button);
    }

    @HostAccess.Export
    @Override
    public void cursorVisible(boolean visible) {
        cursor.setCursorVisible(visible);
    }

    @HostAccess.Export @Override public boolean cursorVisible() { return cursor.isCursorVisible(); }

    @HostAccess.Export
    @Override
    public void grabMouse(boolean grab) {
        cursor.setGrabbed(grab);
        cursor.setCursorVisible(!grab);
        mouse.resetBaselines();
    }

    @HostAccess.Export
    @Override
    public boolean grabbed() {
        return cursor.isGrabbed();
    }

    @HostAccess.Export
    @Override
    public void endFrame() {
        refreshAbsoluteCursorBestEffort();
        frame.endFrame();
        frameId++;
    }

    private void refreshAbsoluteCursorBestEffort() {
        try {
            Vector2f c = input.getCursorPosition();
            if (c != null) mouse.setAbsolute(c.x, c.y);
        } catch (Exception ignored) {}
    }
}