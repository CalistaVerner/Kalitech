package org.foxesworld.kalitech.engine.api.impl.input;

import com.jme3.input.RawInputListener;
import com.jme3.input.event.*;

final class RawCollector implements RawInputListener {

    private final KeyboardState keyboard;
    private final MouseState mouse;
    private final InputFrame frame;

    RawCollector(KeyboardState keyboard, MouseState mouse, InputFrame frame) {
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
        mouse.onRawMotion(
                evt.getX(),
                evt.getY(),
                evt.getDX(),
                evt.getDY(),
                evt.getDeltaWheel()
        );
    }

    @Override
    public void onMouseButtonEvent(MouseButtonEvent evt) {
        mouse.setMouseDown(evt.getButtonIndex(), evt.isPressed());
    }
}