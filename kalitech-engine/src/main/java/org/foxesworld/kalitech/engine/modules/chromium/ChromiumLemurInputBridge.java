package org.foxesworld.kalitech.engine.modules.chromium;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.*;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.Panel;
import org.cef.browser.CefBrowser;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Routes jME input to a JCEF browser using Lemur Panel as hit-test surface.
 *
 * Mouse coords:
 * - jME cursor is bottom-left origin.
 * - Chromium expects top-left origin in its UI component.
 */
public final class ChromiumLemurInputBridge implements ActionListener, AnalogListener {

    private static final String MAP_MOUSE_X = "chromium.mouse.x";
    private static final String MAP_MOUSE_Y = "chromium.mouse.y";
    private static final String MAP_WHEEL = "chromium.wheel";
    private static final String MAP_MB_LEFT = "chromium.mb.left";
    private static final String MAP_MB_RIGHT = "chromium.mb.right";
    private static final String MAP_MB_MIDDLE = "chromium.mb.middle";

    private final InputManager input;
    private final Panel panel;

    private final ChromiumCefEventDispatcher dispatcher;
    private final Component awtTarget;

    private final Set<String> mappings = new HashSet<>();

    private boolean captured;
    private boolean focused;

    private int lastMouseX;
    private int lastMouseY;

    public ChromiumLemurInputBridge(InputManager input, Panel panel, CefBrowser browser) {
        this.input = Objects.requireNonNull(input, "input");
        this.panel = Objects.requireNonNull(panel, "panel");
        Objects.requireNonNull(browser, "browser");

        this.dispatcher = new ChromiumCefEventDispatcher(browser);
        this.awtTarget = dispatcher.target();
    }

    public void install() {
        addMapping(MAP_MOUSE_X,
                new MouseAxisTrigger(MouseInput.AXIS_X, false),
                new MouseAxisTrigger(MouseInput.AXIS_X, true));
        addMapping(MAP_MOUSE_Y,
                new MouseAxisTrigger(MouseInput.AXIS_Y, false),
                new MouseAxisTrigger(MouseInput.AXIS_Y, true));

        addMapping(MAP_MB_LEFT, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        addMapping(MAP_MB_RIGHT, new MouseButtonTrigger(MouseInput.BUTTON_RIGHT));
        addMapping(MAP_MB_MIDDLE, new MouseButtonTrigger(MouseInput.BUTTON_MIDDLE));

        addMapping(MAP_WHEEL,
                new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false),
                new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));

        input.addListener(this, mappings.toArray(new String[0]));

        addKey(KeyInput.KEY_ESCAPE);
        addKey(KeyInput.KEY_RETURN);
        addKey(KeyInput.KEY_BACK);
        addKey(KeyInput.KEY_SPACE);
        addKey(KeyInput.KEY_LEFT);
        addKey(KeyInput.KEY_RIGHT);
        addKey(KeyInput.KEY_UP);
        addKey(KeyInput.KEY_DOWN);
        addKey(KeyInput.KEY_LSHIFT);
        addKey(KeyInput.KEY_RSHIFT);
        addKey(KeyInput.KEY_LCONTROL);
        addKey(KeyInput.KEY_RCONTROL);
        addKey(KeyInput.KEY_LMENU);
        addKey(KeyInput.KEY_RMENU);
    }

    public void uninstall() {
        for (String m : mappings) {
            if (input.hasMapping(m)) {
                input.deleteMapping(m);
            }
        }
        input.removeListener(this);
        mappings.clear();
        captured = false;
        focused = false;
        dispatcher.setFocus(false);
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (name.startsWith("chromium.key.")) {
            if (!focused) {
                return;
            }
            int jmeKey = Integer.parseInt(name.substring("chromium.key.".length()));
            KeyEvent evt = toAwtKeyEvent(jmeKey, isPressed);
            if (evt != null) {
                dispatcher.dispatch(evt);
            }
            return;
        }

        if (name.equals(MAP_MB_LEFT)) {
            handleMouseButton(MouseEvent.BUTTON1, isPressed);
            return;
        }
        if (name.equals(MAP_MB_RIGHT)) {
            handleMouseButton(MouseEvent.BUTTON3, isPressed);
            return;
        }
        if (name.equals(MAP_MB_MIDDLE)) {
            handleMouseButton(MouseEvent.BUTTON2, isPressed);
            return;
        }
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        if (name.equals(MAP_MOUSE_X) || name.equals(MAP_MOUSE_Y)) {
            int mx = (int) input.getCursorPosition().x;
            int my = (int) input.getCursorPosition().y;
            lastMouseX = mx;
            lastMouseY = my;

            boolean inside = hitTest(mx, my);
            if (!captured && !inside) {
                return;
            }

            int[] local = toPanelLocal(mx, my);
            if (local == null) {
                return;
            }

            MouseEvent move = new MouseEvent(
                    awtTarget,
                    MouseEvent.MOUSE_MOVED,
                    System.currentTimeMillis(),
                    0,
                    local[0],
                    local[1],
                    0,
                    false
            );
            dispatcher.dispatch(move);
            return;
        }

        if (name.equals(MAP_WHEEL)) {
            boolean inside = hitTest(lastMouseX, lastMouseY);
            if (!captured && !inside) {
                return;
            }

            int[] local = toPanelLocal(lastMouseX, lastMouseY);
            if (local == null) {
                return;
            }

            int rot = value > 0 ? -1 : 1;
            MouseWheelEvent wheel = new MouseWheelEvent(
                    awtTarget,
                    MouseEvent.MOUSE_WHEEL,
                    System.currentTimeMillis(),
                    0,
                    local[0],
                    local[1],
                    0,
                    false,
                    MouseWheelEvent.WHEEL_UNIT_SCROLL,
                    1,
                    rot
            );
            dispatcher.dispatch(wheel);
        }
    }

    private void handleMouseButton(int awtButton, boolean down) {
        boolean inside = hitTest(lastMouseX, lastMouseY);
        if (!captured && !inside) {
            return;
        }

        if (down) {
            captured = true;
            focused = true;
            dispatcher.setFocus(true);
        } else {
            captured = false;
        }

        int[] local = toPanelLocal(lastMouseX, lastMouseY);
        if (local == null) {
            return;
        }

        int id = down ? MouseEvent.MOUSE_PRESSED : MouseEvent.MOUSE_RELEASED;
        MouseEvent btn = new MouseEvent(
                awtTarget,
                id,
                System.currentTimeMillis(),
                0,
                local[0],
                local[1],
                1,
                false,
                awtButton
        );
        dispatcher.dispatch(btn);

        if (down) {
            MouseEvent click = new MouseEvent(
                    awtTarget,
                    MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(),
                    0,
                    local[0],
                    local[1],
                    1,
                    false,
                    awtButton
            );
            dispatcher.dispatch(click);
        }
    }

    private boolean hitTest(int mouseX, int mouseY) {
        Vector3f wpos = panel.getWorldTranslation();
        Vector3f size = panel.getPreferredSize();

        float left = wpos.x;
        float top = wpos.y;
        float right = left + size.x;
        float bottom = top - size.y;

        return mouseX >= left && mouseX <= right && mouseY >= bottom && mouseY <= top;
    }

    /**
     * Converts jME screen coords to Chromium coords relative to the panel:
     * x: left->right
     * y: top->bottom
     */
    private int[] toPanelLocal(int mouseX, int mouseY) {
        Vector3f wpos = panel.getWorldTranslation();
        Vector3f size = panel.getPreferredSize();

        float left = wpos.x;
        float top = wpos.y;

        int lx = Math.round(mouseX - left);
        int ly = Math.round(top - mouseY);

        if (lx < 0 || ly < 0) return null;
        if (lx > Math.round(size.x) || ly > Math.round(size.y)) return null;

        return new int[]{lx, ly};
    }

    private void addMapping(String name, Trigger... triggers) {
        if (!input.hasMapping(name)) {
            input.addMapping(name, triggers);
        }
        mappings.add(name);
    }

    private void addKey(int jmeKeyCode) {
        String map = "chromium.key." + jmeKeyCode;
        addMapping(map, new KeyTrigger(jmeKeyCode));
    }

    private KeyEvent toAwtKeyEvent(int jmeKey, boolean down) {
        int awtKey = switch (jmeKey) {
            case KeyInput.KEY_ESCAPE -> KeyEvent.VK_ESCAPE;
            case KeyInput.KEY_RETURN -> KeyEvent.VK_ENTER;
            case KeyInput.KEY_BACK -> KeyEvent.VK_BACK_SPACE;
            case KeyInput.KEY_SPACE -> KeyEvent.VK_SPACE;
            case KeyInput.KEY_LEFT -> KeyEvent.VK_LEFT;
            case KeyInput.KEY_RIGHT -> KeyEvent.VK_RIGHT;
            case KeyInput.KEY_UP -> KeyEvent.VK_UP;
            case KeyInput.KEY_DOWN -> KeyEvent.VK_DOWN;
            case KeyInput.KEY_LSHIFT, KeyInput.KEY_RSHIFT -> KeyEvent.VK_SHIFT;
            case KeyInput.KEY_LCONTROL, KeyInput.KEY_RCONTROL -> KeyEvent.VK_CONTROL;
            case KeyInput.KEY_LMENU, KeyInput.KEY_RMENU -> KeyEvent.VK_ALT;
            default -> KeyEvent.VK_UNDEFINED;
        };

        int id = down ? KeyEvent.KEY_PRESSED : KeyEvent.KEY_RELEASED;
        return new KeyEvent(awtTarget, id, System.currentTimeMillis(), 0, awtKey, KeyEvent.CHAR_UNDEFINED);
    }
}