package org.foxesworld.kalitech.engine.api.interfaces;

import org.graalvm.polyglot.HostAccess;

/**
 * Stable input interface exposed to scripting environments.
 * <p>
 * Provides access to keyboard and mouse state using explicit, frame-based queries.
 * The interface is designed to be deterministic, minimal, and suitable for
 * high-frequency gameplay code.
 */
public interface InputApi {

    /**
     * Returns whether the specified key is currently held down.
     *
     * @param key human-readable key name (e.g. "W", "SPACE", "ESCAPE")
     * @return true if the key is currently pressed
     */
    @HostAccess.Export
    boolean keyDown(String key);

    @HostAccess.Export
    boolean keyDown(int keyCode);

    /**
     * Resolves a human-readable key name to an engine-specific key code.
     *
     * @param name key name
     * @return key code, or a negative value if unknown
     */
    @HostAccess.Export
    int keyCode(String name);

    /**
     * Returns the current absolute mouse X position in window coordinates.
     *
     * @return mouse X position in pixels
     */
    @HostAccess.Export
    double mouseX();

    /**
     * Returns the current absolute mouse Y position in window coordinates.
     *
     * @return mouse Y position in pixels
     */
    @HostAccess.Export
    double mouseY();

    /**
     * Returns the accumulated mouse movement delta since the last frame boundary.
     *
     * @return horizontal mouse delta
     */
    @HostAccess.Export
    double mouseDx();

    /**
     * Returns the accumulated mouse movement delta since the last frame boundary.
     *
     * @return vertical mouse delta
     */
    @HostAccess.Export
    double mouseDy();

    @HostAccess.Export
    Object cursorPosition();

    @HostAccess.Export
    double mouseDX();

    @HostAccess.Export
    double mouseDY();

    @HostAccess.Export
    Object mouseDelta();

    /**
     * Consumes and returns the accumulated mouse delta for the current frame.
     * Subsequent calls within the same frame return zero.
     *
     * @return object containing { dx, dy }
     */
    @HostAccess.Export
    Object consumeMouseDelta();

    /**
     * Returns the accumulated mouse wheel delta for the current frame.
     *
     * @return wheel delta
     */
    @HostAccess.Export
    double wheelDelta();

    /**
     * Consumes and returns the mouse wheel delta for the current frame.
     * Subsequent calls within the same frame return zero.
     *
     * @return wheel delta
     */
    @HostAccess.Export
    double consumeWheelDelta();

    /**
     * Returns whether the specified mouse button is currently pressed.
     *
     * @param button mouse button index
     * @return true if the button is held down
     */
    @HostAccess.Export
    boolean mouseDown(int button);

    /**
     * Sets cursor visibility.
     *
     * @param visible whether the cursor should be visible
     */
    @HostAccess.Export
    void cursorVisible(boolean visible);

    /**
     * Returns whether the cursor is currently visible.
     *
     * @return true if the cursor is visible
     */
    @HostAccess.Export
    boolean cursorVisible();

    /**
     * Enables or disables mouse grab mode.
     *
     * @param grab whether the mouse should be grabbed
     */
    @HostAccess.Export
    void grabMouse(boolean grab);

    /**
     * Returns whether the mouse is currently grabbed.
     *
     * @return true if the mouse is grabbed
     */
    @HostAccess.Export
    boolean grabbed();

    /**
     * Advances the input frame boundary.
     * <p>
     * Must be called exactly once per engine frame after all input has been processed.
     */
    @HostAccess.Export
    void endFrame();
}