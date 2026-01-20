package org.foxesworld.kalitech.engine.modules.chromium;

import org.cef.browser.CefBrowser;

import java.awt.AWTEvent;
import java.awt.Component;
import java.util.Objects;

/**
 * Dispatches AWT input events into JCEF through the browser UI component.
 */
public final class ChromiumCefEventDispatcher {

    private final CefBrowser browser;
    private final Component target;

    public ChromiumCefEventDispatcher(CefBrowser browser) {
        this.browser = Objects.requireNonNull(browser, "browser");
        this.target = Objects.requireNonNull(browser.getUIComponent(), "browser.getUIComponent()");
    }

    public Component target() {
        return target;
    }

    public void dispatch(AWTEvent event) {
        Objects.requireNonNull(event, "event");
        target.dispatchEvent(event);
    }

    public void setFocus(boolean focused) {
        try {
            browser.setFocus(focused);
        } catch (Exception ignored) {
        }
    }
}