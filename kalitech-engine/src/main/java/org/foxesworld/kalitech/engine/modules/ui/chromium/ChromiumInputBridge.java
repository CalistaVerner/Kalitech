package org.foxesworld.kalitech.engine.modules.ui.chromium;

import org.cef.browser.CefBrowser;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.Objects;

/**
 * Forwards engine input to Chromium UI component (AWT event dispatch).
 */
public final class ChromiumInputBridge {

    private final Component target;

    public ChromiumInputBridge(CefBrowser browser) {
        Objects.requireNonNull(browser, "browser");
        this.target = Objects.requireNonNull(browser.getUIComponent(), "browser.getUIComponent()");
    }

    public void mouseMove(int x, int y, int modifiers) {
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(),
                modifiers, x, y, 0, false));
    }

    public void mouseButton(int x, int y, int button, boolean down, int modifiers) {
        int id = down ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_RELEASED;
        target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(),
                modifiers, x, y, 1, false, button));
    }

    public void mouseWheel(int x, int y, int wheelRotation, int modifiers) {
        target.dispatchEvent(new MouseWheelEvent(target, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                modifiers, x, y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, wheelRotation));
    }

    public void key(int keyCode, char keyChar, boolean down, int modifiers) {
        int id = down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED;
        target.dispatchEvent(new KeyEvent(target, id, System.currentTimeMillis(),
                modifiers, keyCode, keyChar));
    }

    public void keyTyped(char ch, int modifiers) {
        target.dispatchEvent(new KeyEvent(target, KeyEvent.KEY_TYPED, System.currentTimeMillis(),
                modifiers, KeyEvent.VK_UNDEFINED, ch));
    }
}