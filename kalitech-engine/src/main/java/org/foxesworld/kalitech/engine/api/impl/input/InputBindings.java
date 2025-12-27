package org.foxesworld.kalitech.engine.api.impl.input;

import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;

import java.util.HashMap;
import java.util.Map;

final class InputBindings {

    private static final String MAP_MOUSE_X_POS = "__kt_mouse_x_pos";
    private static final String MAP_MOUSE_X_NEG = "__kt_mouse_x_neg";
    private static final String MAP_MOUSE_Y_POS = "__kt_mouse_y_pos";
    private static final String MAP_MOUSE_Y_NEG = "__kt_mouse_y_neg";
    private static final String MAP_WHEEL_POS   = "__kt_wheel_pos";
    private static final String MAP_WHEEL_NEG   = "__kt_wheel_neg";

    private final InputManager input;
    private final MouseState mouse;
    private final InputFrame frame;

    private final Map<String, Integer> keyMap = new HashMap<>(128);
    private boolean keyListenerInstalled = false;
    private KeyboardState keyboardRef;

    InputBindings(InputManager input, MouseState mouse, InputFrame frame) {
        this.input = input;
        this.mouse = mouse;
        this.frame = frame;
    }

    void ensureKeyMapping(int keyCode, KeyboardState keyboard) {
        if (keyCode < 0) return;

        this.keyboardRef = keyboard;

        final String map = "__kt_key_" + keyCode;
        if (!keyMap.containsKey(map)) keyMap.put(map, keyCode);

        try {
            if (!input.hasMapping(map)) input.addMapping(map, new KeyTrigger(keyCode));

            if (!keyListenerInstalled) {
                input.addListener(keyListener, map);
                keyListenerInstalled = true;
            } else {
                input.addListener(keyListener, map);
            }
        } catch (Exception ignored) {}
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
        } catch (Exception ignored) {}
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
        }
    };
}