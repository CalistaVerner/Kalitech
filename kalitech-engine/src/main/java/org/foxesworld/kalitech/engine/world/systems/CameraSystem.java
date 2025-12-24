package org.foxesworld.kalitech.engine.world.systems;

import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.*;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.graalvm.polyglot.Value;
import org.foxesworld.kalitech.engine.api.impl.CameraState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CameraSystem implements KSystem {

    private static final Logger log = LogManager.getLogger(CameraSystem.class);

    private final CameraState state;
    private final FlyCameraController fly = new FlyCameraController();

    private InputManager input;

    private final Vector3f move = new Vector3f();
    private final Vector2f lookDelta = new Vector2f();

    private final List<String> mappings = new ArrayList<>();
    private final ActionListener actionListener = this::onAction;
    private final AnalogListener analogListener = this::onAnalog;

    public CameraSystem(CameraState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public void applyConfig(Value cfg) {
        if (cfg == null || cfg.isNull()) return;

        String mode = str(cfg, "mode", null);
        if (mode != null && !mode.isBlank()) state.mode = mode.trim().toLowerCase();

        state.speed = clamp(num(cfg, "speed", state.speed), 0.1, 10_000.0);
        state.accel = clamp(num(cfg, "accel", state.accel), 0.0, 1_000.0);
        state.drag = clamp(num(cfg, "drag", state.drag), 0.0, 1_000.0);
        state.smoothing = clamp(num(cfg, "smoothing", state.smoothing), 0.0, 1.0);
        state.lookSensitivity = clamp(num(cfg, "lookSensitivity", state.lookSensitivity), 0.001, 10.0);
        state.invertY = bool(cfg, "invertY", state.invertY);
        state.enabled = bool(cfg, "enabled", state.enabled);
    }

    @Override
    public void onStart(SystemContext ctx) {
        input = ctx.app().getInputManager();

        registerMappings(input);
        String[] names = mappings.toArray(String[]::new);

        input.addListener(actionListener, names);
        input.addListener(analogListener, names);

        log.info("CameraSystem started (mode={}, enabled={})", state.mode, state.enabled);
    }

    @Override
    public void onStop(SystemContext ctx) {
        if (input != null) {
            try { input.removeListener(actionListener); } catch (Exception ignored) {}
            try { input.removeListener(analogListener); } catch (Exception ignored) {}

            for (String m : mappings) {
                try { input.deleteMapping(m); } catch (Exception ignored) {}
            }

            mappings.clear();
            input = null;
        }

        fly.reset();
        move.set(0, 0, 0);
        lookDelta.set(0, 0);

        log.info("CameraSystem stopped");
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        if (!state.enabled) {
            lookDelta.set(0, 0);
            return;
        }

        if ("fly".equals(state.mode)) {
            fly.update(ctx, state, move, lookDelta, tpf);
        }

        lookDelta.set(0, 0);
    }

    // ---------------- input ----------------

    private void registerMappings(InputManager in) {
        add(in, "kt.cam.fwd",  new KeyTrigger(KeyInput.KEY_W));
        add(in, "kt.cam.back", new KeyTrigger(KeyInput.KEY_S));
        add(in, "kt.cam.left", new KeyTrigger(KeyInput.KEY_A));
        add(in, "kt.cam.right",new KeyTrigger(KeyInput.KEY_D));
        add(in, "kt.cam.up",   new KeyTrigger(KeyInput.KEY_E));
        add(in, "kt.cam.down", new KeyTrigger(KeyInput.KEY_Q));

        add(in, "kt.cam.lookX+", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        add(in, "kt.cam.lookX-", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        add(in, "kt.cam.lookY+", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        add(in, "kt.cam.lookY-", new MouseAxisTrigger(MouseInput.AXIS_Y, true));
    }

    private void add(InputManager in, String name, Trigger t) {
        mappings.add(name);
        in.addMapping(name, t);
    }

    private void onAction(String name, boolean isPressed, float tpf) {
        float v = isPressed ? 1f : 0f;
        switch (name) {
            case "kt.cam.fwd"  -> move.z =  v;  // W
            case "kt.cam.back" -> move.z = -v;  // S
            case "kt.cam.left" -> move.x = -v;  // A
            case "kt.cam.right"-> move.x =  v;  // D
            case "kt.cam.up"   -> move.y =  v;
            case "kt.cam.down" -> move.y = -v;
        }
    }

    private void onAnalog(String name, float value, float tpf) {
        switch (name) {
            case "kt.cam.lookX+" -> lookDelta.x += value;
            case "kt.cam.lookX-" -> lookDelta.x -= value;
            case "kt.cam.lookY+" -> lookDelta.y += value;
            case "kt.cam.lookY-" -> lookDelta.y -= value;
        }
    }

    // ---------------- cfg helpers ----------------

    private static boolean bool(Value v, String k, boolean def) {
        try { return v != null && v.hasMember(k) ? v.getMember(k).asBoolean() : def; }
        catch (Exception e) { return def; }
    }

    private static double num(Value v, String k, double def) {
        try { return v != null && v.hasMember(k) ? v.getMember(k).asDouble() : def; }
        catch (Exception e) { return def; }
    }

    private static String str(Value v, String k, String def) {
        try { return v != null && v.hasMember(k) ? v.getMember(k).asString() : def; }
        catch (Exception e) { return def; }
    }

    private static double clamp(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}