/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.input.RawInputListener
 *  com.jme3.input.event.JoyAxisEvent
 *  com.jme3.input.event.JoyButtonEvent
 *  com.jme3.input.event.KeyInputEvent
 *  com.jme3.input.event.MouseButtonEvent
 *  com.jme3.input.event.MouseMotionEvent
 *  com.jme3.input.event.TouchEvent
 */
package org.foxesworld.kalitech.engine.modules.input;

import com.jme3.input.RawInputListener;
import com.jme3.input.event.JoyAxisEvent;
import com.jme3.input.event.JoyButtonEvent;
import com.jme3.input.event.KeyInputEvent;
import com.jme3.input.event.MouseButtonEvent;
import com.jme3.input.event.MouseMotionEvent;
import com.jme3.input.event.TouchEvent;
import org.foxesworld.kalitech.engine.modules.input.InputFrame;
import org.foxesworld.kalitech.engine.modules.input.KeyboardState;
import org.foxesworld.kalitech.engine.modules.input.MouseState;

public final class RawCollector
implements RawInputListener {
    private final KeyboardState keyboard;
    private final MouseState mouse;
    private final InputFrame frame;

    public RawCollector(KeyboardState keyboard, MouseState mouse, InputFrame frame) {
        this.keyboard = keyboard;
        this.mouse = mouse;
        this.frame = frame;
    }

    public void beginInput() {
    }

    public void endInput() {
    }

    public void onJoyAxisEvent(JoyAxisEvent evt) {
    }

    public void onJoyButtonEvent(JoyButtonEvent evt) {
    }

    public void onTouchEvent(TouchEvent evt) {
    }

    public void onKeyEvent(KeyInputEvent evt) {
        int code = evt.getKeyCode();
        if (code == 0 && evt.getKeyChar() == ' ') {
            code = 57;
        }
        if (evt.isPressed() || evt.isRepeating()) {
            this.keyboard.onKeyEvent(code, true);
        } else if (evt.isReleased()) {
            this.keyboard.onKeyEvent(code, false);
        } else {
            this.keyboard.onKeyEvent(code, evt.isPressed());
        }
    }

    public void onMouseMotionEvent(MouseMotionEvent evt) {
        this.frame.markMotion();
        this.mouse.setAbsolute(evt.getX(), evt.getY());
        this.mouse.addDelta(evt.getDX(), evt.getDY());
        this.mouse.addWheel(evt.getDeltaWheel());
    }

    public void onMouseButtonEvent(MouseButtonEvent evt) {
        this.mouse.setMouseDown(evt.getButtonIndex(), evt.isPressed());
    }
}

