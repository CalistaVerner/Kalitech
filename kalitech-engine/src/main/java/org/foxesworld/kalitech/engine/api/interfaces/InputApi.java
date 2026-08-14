package org.foxesworld.kalitech.engine.api.interfaces;

import org.foxesworld.kalitech.engine.script.lua.LuaExport;

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
    @LuaExport
    boolean keyDown(String key);

    @LuaExport
    boolean keyDown(int keyCode);

    /**
     * Resolves a human-readable key name to an engine-specific key code.
     *
     * @param name key name
     * @return key code, or a negative value if unknown
     */
    @LuaExport
    int keyCode(String name);

    /**
     * Returns the current absolute mouse X position in window coordinates.
     *
     * @return mouse X position in pixels
     */
    @LuaExport
    double mouseX();

    /**
     * Returns the current absolute mouse Y position in window coordinates.
     *
     * @return mouse Y position in pixels
     */
    @LuaExport
    double mouseY();

    /**
     * Returns the accumulated mouse movement delta since the last frame boundary.
     *
     * @return horizontal mouse delta
     */
    @LuaExport
    double mouseDx();

    /**
     * Returns the accumulated mouse movement delta since the last frame boundary.
     *
     * @return vertical mouse delta
     */
    @LuaExport
    double mouseDy();

    @LuaExport
    Object cursorPosition();

    @LuaExport
    double mouseDX();

    @LuaExport
    double mouseDY();

    @LuaExport
    Object mouseDelta();

    /**
     * Consumes and returns the accumulated mouse delta for the current frame.
     * Subsequent calls within the same frame return zero.
     *
     * @return object containing { dx, dy }
     */
    @LuaExport
    Object consumeMouseDelta();

    /**
     * Returns the accumulated mouse wheel delta for the current frame.
     *
     * @return wheel delta
     */
    @LuaExport
    double wheelDelta();

    /**
     * Consumes and returns the mouse wheel delta for the current frame.
     * Subsequent calls within the same frame return zero.
     *
     * @return wheel delta
     */
    @LuaExport
    double consumeWheelDelta();

    /**
     * Returns whether the specified mouse button is currently pressed.
     *
     * @param button mouse button index
     * @return true if the button is held down
     */
    @LuaExport
    boolean mouseDown(int button);

    /**
     * Sets cursor visibility.
     *
     * @param visible whether the cursor should be visible
     */
    @LuaExport
    void cursorVisible(boolean visible);

    /**
     * Returns whether the cursor is currently visible.
     *
     * @return true if the cursor is visible
     */
    @LuaExport
    boolean cursorVisible();

    /**
     * Enables or disables mouse grab mode.
     *
     * @param grab whether the mouse should be grabbed
     */
    @LuaExport
    void grabMouse(boolean grab);

    /**
     * Returns whether the mouse is currently grabbed.
     *
     * @return true if the mouse is grabbed
     */
    @LuaExport
    boolean grabbed();

    /**
     * Advances the input frame boundary.
     * <p>
     * Must be called exactly once per engine frame after all input has been processed.
     */
    @LuaExport
    void endFrame();
}