package org.foxesworld.kalitech.engine.api.impl.input;

// Author: Calista Verner

import com.jme3.input.InputManager;
import com.jme3.math.Vector2f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.foxesworld.kalitech.engine.api.EngineApiImpl;
import org.foxesworld.kalitech.engine.api.interfaces.InputApi;
import org.graalvm.polyglot.HostAccess;

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

        // Raw listener: keys/buttons/motion (если backend отдаёт)
        this.input.addRawInputListener(new RawCollector(keyboard, mouse, frame));

        // Axis mappings (MouseAxisTrigger)
        this.bindings.installMouseAxisMappings();

        // init cursor visible on render thread
        engineApi.getApp().enqueue(() -> {
            try { input.setCursorVisible(true); }
            catch (Exception e) { log.warn("[input] setCursorVisible(true) failed: {}", e.toString()); }
            return null;
        });

        log.info("[input] InputApiImpl attached (KEY_MAX={})", keyboard.keyMax());
    }

    @HostAccess.Export
    public Object consumeSnapshot() {
        // 1) обновим abs позицию best-effort
        refreshAbsoluteCursorBestEffort();

        // 2) если grab и не было motion в этом кадре — fallback из абсолютной
        mouse.ensureFallbackDeltaIfNeeded(cursor.isGrabbed(), frame.motionThisFrame());

        // 3) атомарно заберём deltas + wheel
        MouseState.Consumed c = mouse.consumeDeltasAndWheel();

        // 4) снимем keysDown (immutable copy)
        int[] keysDown = keyboard.copyPressedKeyCodes();

        // 5) соберём immutable snapshot
        InputSnapshot snap = new InputSnapshot(
                frameId,
                System.nanoTime(),

                mouse.mouseX(), mouse.mouseY(),
                c.dx, c.dy,
                c.wheel,

                mouse.peekMouseMask(),
                cursor.isGrabbed(),
                cursor.isCursorVisible(),

                keyboard.keysDown(),
                keyboard.justPressed(),
                keyboard.justReleased()
        );

        return snap.toJs();
    }

    // -------- Keyboard --------

    @HostAccess.Export
    @Override
    public boolean keyDown(String key) {
        return keyboard.keyDown(key);
    }

    // -------- Mouse absolute --------

    @HostAccess.Export @Override public double mouseX() { return mouse.mouseX(); }
    @HostAccess.Export @Override public double mouseY() { return mouse.mouseY(); }

    @HostAccess.Export
    @Override
    public Object cursorPosition() {
        // best-effort refresh from InputManager
        refreshAbsoluteCursorBestEffort();
        return JsMarshalling.vec2(mouse.mouseX(), mouse.mouseY());
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
    public int keyCode(String name) {
        return keyboard.keyCode(name);
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
        return JsMarshalling.delta(mouse.mouseDx(), mouse.mouseDy());
    }

    // legacy aliases if some scripts call these
    @HostAccess.Export public double mouseDX() { return mouse.mouseDx(); }
    @HostAccess.Export public double mouseDY() { return mouse.mouseDy(); }

    @HostAccess.Export
    @Override
    public Object consumeMouseDelta() {
        refreshAbsoluteCursorBestEffort();
        mouse.ensureFallbackDeltaIfNeeded(cursor.isGrabbed(), frame.motionThisFrame());
        MouseState.Delta d = mouse.consumeMouseDelta();
        if (debug) mouse.dbgDelta("consumeMouseDelta", d.dx, d.dy, mouse.peekWheel(), frame.motionThisFrame());
        return JsMarshalling.delta(d.dx, d.dy);
    }

    // -------- Wheel --------

    @HostAccess.Export @Override public double wheelDelta() { return mouse.peekWheel(); }

    @HostAccess.Export
    @Override
    public double consumeWheelDelta() {
        double w = mouse.consumeWheel();
        if (debug) mouse.dbgDelta("consumeWheelDelta", mouse.mouseDx(), mouse.mouseDy(), w, frame.motionThisFrame());
        return w;
    }

    // -------- Mouse buttons --------

    @HostAccess.Export
    @Override
    public boolean mouseDown(int button) {
        return mouse.mouseDown(button);
    }

    // -------- Cursor / grab --------

    @HostAccess.Export @Override public void cursorVisible(boolean visible) { cursor.setCursorVisible(visible); }
    @HostAccess.Export @Override public boolean cursorVisible() { return cursor.isCursorVisible(); }

    @HostAccess.Export
    @Override
    public void grabMouse(boolean grab) {
        cursor.setGrabbed(grab);
        // gameplay policy: grab => hide cursor
        cursor.setCursorVisible(!grab);
        // reset baselines to avoid jump
        mouse.resetBaselines();
    }

    @HostAccess.Export @Override public boolean grabMouse() { return cursor.isGrabbed(); }

    // -------- Frame lifecycle --------


    @Override
    public void endFrame() {
        refreshAbsoluteCursorBestEffort();

        // finalize keyboard transitions
        keyboard.endFrame();

        frame.endFrame();
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