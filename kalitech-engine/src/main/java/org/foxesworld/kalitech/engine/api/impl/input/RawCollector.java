package org.foxesworld.kalitech.engine.api.impl.input;

import com.jme3.input.RawInputListener;
import com.jme3.input.event.*;

public final class RawCollector implements RawInputListener {

    private final KeyboardState keyboard;
    private final MouseState mouse;
    private final InputFrame frame;

    public RawCollector(KeyboardState keyboard, MouseState mouse, InputFrame frame) {
        this.keyboard = keyboard;
        this.mouse = mouse;
        this.frame = frame;
    }

    @Override public void beginInput() {}
    @Override public void endInput() {}

    @Override public void onJoyAxisEvent(JoyAxisEvent evt) {}
    @Override public void onJoyButtonEvent(JoyButtonEvent evt) {}
    @Override public void onTouchEvent(TouchEvent evt) {}

    @Override
    public void onKeyEvent(KeyInputEvent evt) {
        keyboard.onKeyEvent(evt.getKeyCode(), evt.isPressed());
    }

    @Override
    public void onMouseMotionEvent(MouseMotionEvent evt) {
        frame.markMotion();
        mouse.setAbsolute(evt.getX(), evt.getY());
        mouse.addDelta(evt.getDX(), evt.getDY());
        mouse.addWheel(evt.getDeltaWheel());
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent evt) {
        mouse.setMouseDown(evt.getButtonIndex(), evt.isPressed());
    }
}