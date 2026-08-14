/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.input.InputManager
 *  com.jme3.input.controls.AnalogListener
 *  com.jme3.input.controls.InputListener
 *  com.jme3.input.controls.KeyTrigger
 *  com.jme3.input.controls.MouseAxisTrigger
 *  com.jme3.input.controls.Trigger
 */
package org.foxesworld.kalitech.engine.modules.input;

import com.jme3.input.InputManager;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.InputListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.input.controls.Trigger;
import org.foxesworld.kalitech.engine.modules.input.InputFrame;
import org.foxesworld.kalitech.engine.modules.input.MouseState;

public final class InputBindings {
    private static final String MAP_MOUSE_X_POS = "__kt_mouse_x_pos";
    private static final String MAP_MOUSE_X_NEG = "__kt_mouse_x_neg";
    private static final String MAP_MOUSE_Y_POS = "__kt_mouse_y_pos";
    private static final String MAP_MOUSE_Y_NEG = "__kt_mouse_y_neg";
    private static final String MAP_WHEEL_POS = "__kt_wheel_pos";
    private static final String MAP_WHEEL_NEG = "__kt_wheel_neg";
    private final InputManager input;
    private final MouseState mouse;
    private final InputFrame frame;
    private final AnalogListener axisListener = new AnalogListener(){

        public void onAnalog(String name, float value, float tpf) {
            InputBindings.this.frame.markMotion();
            if (InputBindings.MAP_MOUSE_X_POS.equals(name)) {
                InputBindings.this.mouse.addDelta(value, 0.0);
            } else if (InputBindings.MAP_MOUSE_X_NEG.equals(name)) {
                InputBindings.this.mouse.addDelta(-value, 0.0);
            } else if (InputBindings.MAP_MOUSE_Y_POS.equals(name)) {
                InputBindings.this.mouse.addDelta(0.0, value);
            } else if (InputBindings.MAP_MOUSE_Y_NEG.equals(name)) {
                InputBindings.this.mouse.addDelta(0.0, -value);
            } else if (InputBindings.MAP_WHEEL_POS.equals(name)) {
                InputBindings.this.mouse.addWheel(value);
            } else if (InputBindings.MAP_WHEEL_NEG.equals(name)) {
                InputBindings.this.mouse.addWheel(-value);
            }
        }
    };

    public InputBindings(InputManager input, MouseState mouse, InputFrame frame) {
        this.input = input;
        this.mouse = mouse;
        this.frame = frame;
        this.installMouseAxisMappings();
        try {
            input.addListener((InputListener)this.axisListener, new String[]{MAP_MOUSE_X_POS, MAP_MOUSE_X_NEG, MAP_MOUSE_Y_POS, MAP_MOUSE_Y_NEG, MAP_WHEEL_POS, MAP_WHEEL_NEG});
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public void ensureKeyMapping(int keyCode) {
        if (keyCode < 0) {
            return;
        }
        String map = "__kt_key_" + keyCode;
        try {
            if (!this.input.hasMapping(map)) {
                this.input.addMapping(map, new Trigger[]{new KeyTrigger(keyCode)});
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }

    public void installMouseAxisMappings() {
        try {
            if (!this.input.hasMapping(MAP_MOUSE_X_POS)) {
                this.input.addMapping(MAP_MOUSE_X_POS, new Trigger[]{new MouseAxisTrigger(0, false)});
            }
            if (!this.input.hasMapping(MAP_MOUSE_X_NEG)) {
                this.input.addMapping(MAP_MOUSE_X_NEG, new Trigger[]{new MouseAxisTrigger(0, true)});
            }
            if (!this.input.hasMapping(MAP_MOUSE_Y_POS)) {
                this.input.addMapping(MAP_MOUSE_Y_POS, new Trigger[]{new MouseAxisTrigger(1, false)});
            }
            if (!this.input.hasMapping(MAP_MOUSE_Y_NEG)) {
                this.input.addMapping(MAP_MOUSE_Y_NEG, new Trigger[]{new MouseAxisTrigger(1, true)});
            }
            if (!this.input.hasMapping(MAP_WHEEL_POS)) {
                this.input.addMapping(MAP_WHEEL_POS, new Trigger[]{new MouseAxisTrigger(2, false)});
            }
            if (!this.input.hasMapping(MAP_WHEEL_NEG)) {
                this.input.addMapping(MAP_WHEEL_NEG, new Trigger[]{new MouseAxisTrigger(2, true)});
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
    }
}

