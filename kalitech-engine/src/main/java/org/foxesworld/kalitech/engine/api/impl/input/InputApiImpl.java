package org.foxesworld.kalitech.engine.api.impl.input;

// Author: Calista Verner

import com.jme3.input.InputManager;
import com.jme3.math.Vector2f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.InputApi;
import org.graalvm.polyglot.HostAccess;

import java.util.Arrays;

public final class InputApiImpl implements InputApi {

    private static final Logger log = LogManager.getLogger(InputApiImpl.class);

    private final EngineApiImpl engine;
    private final InputManager input;
    private long frameId = 0;

    private final InputFrame frame = new InputFrame();
    private final KeyboardState keyboard = new KeyboardState();
    private final MouseState mouse = new MouseState();
    private final CursorGrabController cursor;
    private final InputBindings bindings;

    private volatile boolean debug = false;

    public InputApiImpl(EngineApiImpl engineApi) {
        this.engine = engineApi;
        this.input = engineApi.getApp().getInputManager();

        this.cursor = new CursorGrabController(engineApi, input, mouse, this::isDebug);
        this.bindings = new InputBindings(input, mouse, frame, this::isDebug);

        // 1) RAW collector (keys/buttons + motion if backend provides)
        this.input.addRawInputListener(new RawCollector(keyboard, mouse, frame));

        // 2) Axis mappings (THE FIX for mouse deltas)
        this.bindings.installMouseAxisMappings();

        // init cursor visible on render thread
        engineApi.getApp().enqueue(() -> {
            try { input.setCursorVisible(true); }
            catch (Exception e) { log.warn("[input] setCursorVisible(true) failed: {}", e.toString()); }
            return null;
        });

        log.info("[input] InputApiImpl attached (KEY_MAX={})", keyboard.keyMax());
        log.info("[input] implClass={}", this.getClass().getName());
    }

    @HostAccess.Export
    public Object consumeSnapshot() {
        refreshAbsoluteCursorBestEffort();

        // ✅ фиксируем клавиатурный кадр здесь (justPressed/Released/keysDown)
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

        return snap.toJs();
    }

    // -------- Keyboard --------

    @HostAccess.Export
    @Override
    public boolean keyDown(String key) {
        int code = keyboard.keyCode(key);
        // AAA++: ставим mapping on-demand, чтобы legacy JS (который дергает только keyDown) работал
        bindings.ensureKeyMapping(code, keyboard);
        return keyboard.keyDown(code);
    }

    @HostAccess.Export
    @Override
    public int keyCode(String name) {
        int code = keyboard.keyCode(name);
        bindings.ensureKeyMapping(code, keyboard);
        return code;
    }

    // -------- Mouse absolute --------

    @HostAccess.Export @Override public double mouseX() { return mouse.mouseX(); }
    @HostAccess.Export @Override public double mouseY() { return mouse.mouseY(); }

    @HostAccess.Export
    @Override
    public Object cursorPosition() {
        refreshAbsoluteCursorBestEffort();
        return JsMarshalling.vec2(mouse.mouseX(), mouse.mouseY());
    }

    @Override
    public double mouseDX() {
        return 0;
    }

    @Override
    public double mouseDY() {
        return 0;
    }

    // -------- Mouse delta --------

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

    @Override
    public Object mouseDelta() {
        return null;
    }

    @HostAccess.Export
    @Override
    public Object consumeMouseDelta() {
        refreshAbsoluteCursorBestEffort();
        mouse.ensureFallbackDeltaIfNeeded(cursor.isGrabbed(), frame.motionThisFrame());
        MouseState.Consumed c = mouse.consumeDeltasOnly();
        return JsMarshalling.delta2(c.dx(), c.dy());
    }

    // -------- Wheel --------

    @HostAccess.Export @Override public double wheelDelta() { return mouse.peekWheel(); }

    @HostAccess.Export
    @Override
    public double consumeWheelDelta() {
        return mouse.consumeWheelOnly();
    }

    // -------- Buttons --------

    @HostAccess.Export
    @Override
    public boolean mouseDown(int button) {
        return mouse.mouseDown(button);
    }

    // -------- Cursor / grab --------

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
        // gameplay: grab => hide cursor
        cursor.setCursorVisible(!grab);
        // reset baselines to avoid jump
        mouse.resetBaselines();
    }

    @HostAccess.Export @Override public boolean grabMouse() { return cursor.isGrabbed(); }

    // -------- Frame lifecycle --------

    @HostAccess.Export
    @Override
    public void endFrame() {
        refreshAbsoluteCursorBestEffort();
        frame.endFrame();  // only resets motionThisFrame
        frameId++;
    }

    // -------- Debug --------

    @HostAccess.Export
    @Override
    public void debug(boolean enabled) {
        this.debug = enabled;
        log.info("[input] debug={}", enabled);
    }

    @HostAccess.Export
    @Override
    public boolean debug() { return debug; }

    private boolean isDebug() { return debug; }

    private void refreshAbsoluteCursorBestEffort() {
        try {
            Vector2f c = input.getCursorPosition();
            if (c != null) mouse.setAbsolute(c.x, c.y);
        } catch (Exception ignored) {}
    }
}