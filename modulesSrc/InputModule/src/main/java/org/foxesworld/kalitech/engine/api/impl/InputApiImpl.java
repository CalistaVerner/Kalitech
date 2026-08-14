/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.jme3.input.InputManager
 *  com.jme3.input.RawInputListener
 *  com.jme3.math.Vector2f
 *  org.foxesworld.kalitech.engine.api.contract.ApiCostHint
 *  org.foxesworld.kalitech.engine.api.contract.ApiFlag
 *  org.foxesworld.kalitech.engine.api.contract.ApiMethod
 *  org.foxesworld.kalitech.engine.api.contract.ApiThreadRule
 *  org.foxesworld.kalitech.engine.api.module.AbstractApiModule
 *  org.foxesworld.kalitech.engine.api.module.ApiContext
 *  org.foxesworld.kalitech.engine.script.lua.LuaExport
 */
package org.foxesworld.kalitech.engine.api.impl;

import com.jme3.input.InputManager;
import com.jme3.input.RawInputListener;
import com.jme3.math.Vector2f;
import java.util.Arrays;
import java.util.Objects;
import org.foxesworld.kalitech.engine.api.contract.ApiCostHint;
import org.foxesworld.kalitech.engine.api.contract.ApiFlag;
import org.foxesworld.kalitech.engine.api.contract.ApiMethod;
import org.foxesworld.kalitech.engine.api.contract.ApiThreadRule;
import org.foxesworld.kalitech.engine.api.interfaces.InputApi;
import org.foxesworld.kalitech.engine.api.module.AbstractApiModule;
import org.foxesworld.kalitech.engine.api.module.ApiContext;
import org.foxesworld.kalitech.engine.modules.input.CursorGrabController;
import org.foxesworld.kalitech.engine.modules.input.InputBindings;
import org.foxesworld.kalitech.engine.modules.input.InputFrame;
import org.foxesworld.kalitech.engine.modules.input.InputSnapshot;
import org.foxesworld.kalitech.engine.modules.input.LuaMarshalling;
import org.foxesworld.kalitech.engine.modules.input.KeyboardState;
import org.foxesworld.kalitech.engine.modules.input.MouseState;
import org.foxesworld.kalitech.engine.modules.input.RawCollector;
import org.foxesworld.kalitech.engine.script.lua.LuaExport;

public final class InputApiImpl
extends AbstractApiModule
implements InputApi {
    private final InputFrame frame = new InputFrame();
    private final KeyboardState keyboard = new KeyboardState();
    private final MouseState mouse = new MouseState();
    private InputManager input;
    private long frameId = 0L;
    private CursorGrabController cursor;
    private InputBindings bindings;
    private RawCollector rawListener;

    public InputApiImpl() {
        super("input", "Input", "1.0.0");
    }

    public void attach(ApiContext ctx) {
        super.attach(ctx);
        Objects.requireNonNull(ctx.app, "ctx.app");
        this.input = Objects.requireNonNull(ctx.app.getInputManager(), "ctx.app.inputManager");
        this.cursor = new CursorGrabController(ctx.engine, this.input, this.mouse);
        this.bindings = new InputBindings(this.input, this.mouse, this.frame);
        this.rawListener = new RawCollector(this.keyboard, this.mouse, this.frame);
        this.input.addRawInputListener((RawInputListener)this.rawListener);
        this.bindings.installMouseAxisMappings();
        this.onJmeVoid("input.cursorVisible(true)", () -> {
            InputManager im = this.input;
            if (im != null) {
                im.setCursorVisible(true);
            }
        });
    }

    public void detach() {
        try {
            InputManager im = this.input;
            RawCollector rl = this.rawListener;
            if (im != null && rl != null) {
                im.removeRawInputListener((RawInputListener)rl);
            }
        }
        finally {
            this.rawListener = null;
            this.bindings = null;
            this.cursor = null;
            this.input = null;
            super.detach();
        }
    }

    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public Object consumeSnapshot() {
        return this.profiled(() -> {
            this.refreshAbsoluteCursorBestEffort();
            this.keyboard.advanceFrame();
            this.mouse.ensureFallbackDeltaIfNeeded(this.cursor != null && this.cursor.isGrabbed(), this.frame.motionThisFrame());
            MouseState.Consumed c = this.mouse.consumeDeltasAndWheel();
            return new InputSnapshot(this.frameId, System.nanoTime(), this.mouse.mouseX(), this.mouse.mouseY(), c.dx(), c.dy(), c.wheel(), this.mouse.peekMouseMask(), this.cursor != null && this.cursor.isGrabbed(), this.cursor != null && this.cursor.isCursorVisible(), this.keyboard.copyPressedKeyCodes(), Arrays.copyOf(this.keyboard.justPressed(), this.keyboard.justPressed().length), Arrays.copyOf(this.keyboard.justReleased(), this.keyboard.justReleased().length));
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public boolean keyDown(String key) {
        return (Boolean)this.profiled(() -> {
            int code = this.keyboard.keyCode(key);
            InputBindings b = this.bindings;
            if (b != null) {
                b.ensureKeyMapping(code);
            }
            return this.keyboard.keyDown(code);
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public boolean keyDown(int keyCode) {
        return (Boolean)this.profiled(() -> {
            if (keyCode < 0) {
                return false;
            }
            InputBindings b = this.bindings;
            if (b != null) {
                b.ensureKeyMapping(keyCode);
            }
            return this.keyboard.keyDown(keyCode);
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public int keyCode(String name) {
        return (Integer)this.profiled(() -> {
            int code = this.keyboard.keyCode(name);
            InputBindings b = this.bindings;
            if (b != null) {
                b.ensureKeyMapping(code);
            }
            return code;
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public double mouseX() {
        return this.mouse.mouseX();
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public double mouseY() {
        return this.mouse.mouseY();
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public Object cursorPosition() {
        return this.profiled(() -> {
            this.refreshAbsoluteCursorBestEffort();
            return LuaMarshalling.vec2(this.mouse.mouseX(), this.mouse.mouseY());
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public double mouseDX() {
        return this.mouseDx();
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public double mouseDY() {
        return this.mouseDy();
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public double mouseDx() {
        return (Double)this.profiled(() -> {
            this.refreshAbsoluteCursorBestEffort();
            this.mouse.ensureFallbackDeltaIfNeeded(this.cursor != null && this.cursor.isGrabbed(), this.frame.motionThisFrame());
            return this.mouse.mouseDx();
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public double mouseDy() {
        return (Double)this.profiled(() -> {
            this.refreshAbsoluteCursorBestEffort();
            this.mouse.ensureFallbackDeltaIfNeeded(this.cursor != null && this.cursor.isGrabbed(), this.frame.motionThisFrame());
            return this.mouse.mouseDy();
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public Object mouseDelta() {
        return this.profiled(() -> {
            this.refreshAbsoluteCursorBestEffort();
            this.mouse.ensureFallbackDeltaIfNeeded(this.cursor != null && this.cursor.isGrabbed(), this.frame.motionThisFrame());
            return LuaMarshalling.delta2(this.mouse.mouseDx(), this.mouse.mouseDy());
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public Object consumeMouseDelta() {
        return this.profiled(() -> {
            this.refreshAbsoluteCursorBestEffort();
            this.mouse.ensureFallbackDeltaIfNeeded(this.cursor != null && this.cursor.isGrabbed(), this.frame.motionThisFrame());
            MouseState.Consumed c = this.mouse.consumeDeltasOnly();
            return LuaMarshalling.delta2(c.dx(), c.dy());
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public double wheelDelta() {
        return this.mouse.peekWheel();
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public double consumeWheelDelta() {
        return (Double)this.profiled(this.mouse::consumeWheelOnly);
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public boolean mouseDown(int button) {
        return (Boolean)this.profiled(() -> this.mouse.mouseDown(button));
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void cursorVisible(boolean visible) {
        this.profiledVoid(() -> {
            CursorGrabController c = this.cursor;
            if (c != null) {
                c.setCursorVisible(visible);
            }
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public boolean cursorVisible() {
        return (Boolean)this.profiled(() -> this.cursor != null && this.cursor.isCursorVisible());
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void grabMouse(boolean grab) {
        this.profiledVoid(() -> {
            CursorGrabController c = this.cursor;
            if (c == null) {
                return;
            }
            c.setGrabbed(grab);
            c.setCursorVisible(!grab);
            this.mouse.resetBaselines();
        });
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public boolean grabbed() {
        return (Boolean)this.profiled(() -> this.cursor != null && this.cursor.isGrabbed());
    }

    @Override
    @LuaExport
    @ApiMethod(thread=ApiThreadRule.ANY, sync=false, flags={ApiFlag.SANDBOX_ALLOWED}, cost=ApiCostHint.NORMAL)
    public void endFrame() {
        this.profiledVoid(() -> {
            this.refreshAbsoluteCursorBestEffort();
            this.frame.endFrame();
            ++this.frameId;
        });
    }

    private void refreshAbsoluteCursorBestEffort() {
        block4: {
            InputManager im = this.input;
            if (im == null) {
                return;
            }
            try {
                Vector2f c = im.getCursorPosition();
                if (c != null) {
                    this.mouse.setAbsolute(c.x, c.y);
                }
            }
            catch (Throwable t) {
                if (this.log == null || !this.log.isDebugEnabled()) break block4;
                this.log.debug("[input] cursor refresh failed", t);
            }
        }
    }
}

