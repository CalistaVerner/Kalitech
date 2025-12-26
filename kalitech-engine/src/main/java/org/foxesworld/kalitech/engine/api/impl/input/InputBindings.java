package org.foxesworld.kalitech.engine.api.impl.input;

import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

final class InputBindings {

    private static final Logger log = LogManager.getLogger(InputBindings.class);

    // mapping names (stable, engine-private)
    private static final String MAP_MOUSE_X_POS = "__kt_mouse_x_pos";
    private static final String MAP_MOUSE_X_NEG = "__kt_mouse_x_neg";
    private static final String MAP_MOUSE_Y_POS = "__kt_mouse_y_pos";
    private static final String MAP_MOUSE_Y_NEG = "__kt_mouse_y_neg";
    private static final String MAP_WHEEL_POS   = "__kt_wheel_pos";
    private static final String MAP_WHEEL_NEG   = "__kt_wheel_neg";

    private final InputManager input;
    private final MouseState mouse;
    private final InputFrame frame;
    private final BooleanSupplier debug;

    // --- keyboard mappings (on-demand, AAA++) ---
    private final Map<String, Integer> keyMap = new ConcurrentHashMap<>();
    private final AtomicBoolean keyListenerInstalled = new AtomicBoolean(false);
    private volatile KeyboardState keyboardRef;

    InputBindings(InputManager input, MouseState mouse, InputFrame frame, BooleanSupplier debug) {
        this.input = input;
        this.mouse = mouse;
        this.frame = frame;
        this.debug = debug;
    }

    /**
     * Ensure KeyTrigger mapping exists for this KeyInput code.
     *
     * Why: on some backends / focus+grab combos RawInputListener key events can be unreliable,
     * while ActionListener mappings keep working.
     */
    void ensureKeyMapping(int keyCode, KeyboardState keyboard) {
        if (keyCode < 0) return;

        // listener uses this reference
        this.keyboardRef = keyboard;

        final String map = "__kt_key_" + keyCode;
        keyMap.put(map, keyCode);

        try {
            if (!input.hasMapping(map)) {
                input.addMapping(map, new KeyTrigger(keyCode));
            }

            // jME accepts repeated addListener calls; it just registers more mapping names.
            input.addListener(keyListener, map);

            if (keyListenerInstalled.compareAndSet(false, true)) {
                log.info("[input] key listener installed (ActionListener)");
            }

            // keep existing log behavior (useful while debugging)
            log.info("[input] key mapping installed code={} map={}", keyCode, map);
        } catch (Exception e) {
            log.warn("[input] failed to install key mapping code={}: {}", keyCode, e.toString());
        }
    }

    private final ActionListener keyListener = new ActionListener() {
        @Override
        public void onAction(String name, boolean isPressed, float tpf) {
            KeyboardState kb = keyboardRef;
            if (kb == null) return;

            Integer code = keyMap.get(name);
            if (code == null) return;

            kb.onKeyEvent(code, isPressed);
        }
    };

    // ---------------- Mouse Axis mappings (THE FIX) ----------------

    void installMouseAxisMappings() {
        try {
            if (!input.hasMapping(MAP_MOUSE_X_POS)) input.addMapping(MAP_MOUSE_X_POS, new MouseAxisTrigger(MouseInput.AXIS_X, false));
            if (!input.hasMapping(MAP_MOUSE_X_NEG)) input.addMapping(MAP_MOUSE_X_NEG, new MouseAxisTrigger(MouseInput.AXIS_X, true));

            if (!input.hasMapping(MAP_MOUSE_Y_POS)) input.addMapping(MAP_MOUSE_Y_POS, new MouseAxisTrigger(MouseInput.AXIS_Y, false));
            if (!input.hasMapping(MAP_MOUSE_Y_NEG)) input.addMapping(MAP_MOUSE_Y_NEG, new MouseAxisTrigger(MouseInput.AXIS_Y, true));

            if (!input.hasMapping(MAP_WHEEL_POS)) input.addMapping(MAP_WHEEL_POS, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
            if (!input.hasMapping(MAP_WHEEL_NEG)) input.addMapping(MAP_WHEEL_NEG, new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));

            input.addListener(axisListener,
                    MAP_MOUSE_X_POS, MAP_MOUSE_X_NEG,
                    MAP_MOUSE_Y_POS, MAP_MOUSE_Y_NEG,
                    MAP_WHEEL_POS, MAP_WHEEL_NEG
            );

            log.info("[input] mouse axis mappings installed (MouseAxisTrigger)");
        } catch (Exception e) {
            log.warn("[input] failed to install mouse axis mappings: {}", e.toString());
        }
    }

    private final AnalogListener axisListener = new AnalogListener() {
        @Override
        public void onAnalog(String name, float value, float tpf) {
            frame.markMotion();

            if (MAP_MOUSE_X_POS.equals(name)) mouse.addDelta(value, 0);
            else if (MAP_MOUSE_X_NEG.equals(name)) mouse.addDelta(-value, 0);

            else if (MAP_MOUSE_Y_POS.equals(name)) mouse.addDelta(0, value);
            else if (MAP_MOUSE_Y_NEG.equals(name)) mouse.addDelta(0, -value);

            else if (MAP_WHEEL_POS.equals(name)) mouse.addWheel(value);
            else if (MAP_WHEEL_NEG.equals(name)) mouse.addWheel(-value);

            if (debug.getAsBoolean()) {
                // MouseState throttles internally
                mouse.dbgDelta("axis", mouse.mouseDx(), mouse.mouseDy(), mouse.peekWheel(), true);
            }
        }
    };
}