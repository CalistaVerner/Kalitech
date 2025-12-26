package org.foxesworld.kalitech.engine.api.impl.input;

import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.MouseAxisTrigger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    InputBindings(InputManager input, MouseState mouse, InputFrame frame, BooleanSupplier debug) {
        this.input = input;
        this.mouse = mouse;
        this.frame = frame;
        this.debug = debug;
    }

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
                // не спамим: сама MouseState троттлит
                mouse.dbgDelta("axis", mouse.mouseDx(), mouse.mouseDy(), mouse.peekWheel(), true);
            }
        }
    };
}