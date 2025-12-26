package org.foxesworld.kalitech.engine.world.systems;

// Author: Calista Verner

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.objects.PhysicsRigidBody;
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
import org.foxesworld.kalitech.engine.world.systems.camera.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CameraSystem implements KSystem {

    private static final Logger log = LogManager.getLogger(CameraSystem.class);

    private final CameraState state;
    private final PhysicsSpace physics;

    private InputManager input;

    // input accumulators
    private float mouseDx = 0f, mouseDy = 0f;
    private float wheel = 0f;

    // was there real mouse axis events this frame?
    private boolean mouseAxisEvent = false;

    // cursor fallback
    private final Vector2f lastCursor = new Vector2f();
    private boolean cursorInit = false;

    // movement axis values (rebuilt each update from key states)
    private float mx = 0f, my = 0f, mz = 0f;

    // canonicalized active mode
    private String activeMode = "";

    // F-keys state
    private boolean f1Down = false, f2Down = false, f3Down = false;
    private boolean f1Latch = false, f2Latch = false, f3Latch = false;

    // movement key states
    private boolean wDown, sDown, aDown, dDown, qDown, eDown;

    private final List<String> mappings = new ArrayList<>();

    // controllers
    private final LookController look = new LookController();
    private final FreeCameraController free;
    private final FirstPersonCameraController fp;
    private final ThirdPersonCameraController tp;

    public CameraSystem(CameraState state, PhysicsSpace physics) {
        this.state = Objects.requireNonNull(state, "state");
        this.physics = Objects.requireNonNull(physics, "physics");

        BodyPositionProvider bodyPos = new BodyPositionProvider() {
            private final Vector3f tmp = new Vector3f();

            @Override
            public boolean getBodyPosition(int bodyId, Vector3f out) {
                PhysicsRigidBody b = findRigidBodyById(bodyId);
                if (b == null) return false;
                b.getPhysicsLocation(tmp);
                out.set(tmp);
                return true;
            }
        };

        ThirdPersonCameraController.RaycastProvider raycast = (from, to) -> {
            List<PhysicsRayTestResult> hits = physics.rayTest(from, to);
            if (hits == null || hits.isEmpty()) return 1f;
            float best = 1f;
            for (PhysicsRayTestResult h : hits) {
                float f = h.getHitFraction();
                if (f < best) best = f;
            }
            return best;
        };

        this.free = new FreeCameraController(look);
        this.fp = new FirstPersonCameraController(look, bodyPos);
        this.tp = new ThirdPersonCameraController(look, bodyPos, raycast);
    }

    public void applyConfig(Value cfg) {
        if (cfg == null || cfg.isNull()) return;

        state.mode = str(cfg, "mode", state.mode);

        state.speed = clamp(num(cfg, "speed", state.speed), 0.0, 10_000.0);
        state.accel = clamp(num(cfg, "accel", state.accel), 0.0, 1_000.0);
        state.drag = clamp(num(cfg, "drag", state.drag), 0.0, 1_000.0);
        state.smoothing = clamp(num(cfg, "smoothing", state.smoothing), 0.0, 1.0);
        state.lookSensitivity = clamp(num(cfg, "lookSensitivity", state.lookSensitivity), 0.0001, 10.0);
        state.invertY = bool(cfg, "invertY", state.invertY);
        state.invertX = bool(cfg, "invertX", state.invertX);
        state.enabled = bool(cfg, "enabled", state.enabled);
    }

    @Override
    public void onStart(SystemContext ctx) {
        this.input = ctx.app().getInputManager();
        registerMappings(input);

        String[] names = mappings.toArray(String[]::new);
        input.addListener(actionListener, names);
        input.addListener(analogListener, names);

        cursorInit = false;
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

        activeMode = "";
        look.reset();

        f1Down = f2Down = f3Down = false;
        f1Latch = f2Latch = f3Latch = false;

        wDown = sDown = aDown = dDown = qDown = eDown = false;
        mx = my = mz = 0f;

        mouseDx = mouseDy = wheel = 0f;
        mouseAxisEvent = false;
        cursorInit = false;

        log.info("CameraSystem stopped");
    }

    @Override
    public void onUpdate(SystemContext ctx, float tpf) {
        if (!state.enabled) {
            consumeDeltas();
            mx = my = mz = 0f;
            return;
        }

        // mode hotkeys
        if (edge(KeyInput.KEY_F1)) state.mode = "free";
        if (edge(KeyInput.KEY_F2)) state.mode = "firstPerson";
        if (edge(KeyInput.KEY_F3)) state.mode = "thirdPerson";

        final String mode = normalizeMode(state.mode);

        CameraController c = switch (mode) {
            case "free" -> free;
            case "firstperson" -> fp;
            case "thirdperson" -> tp;
            default -> free;
        };

        if (!mode.equals(activeMode)) {
            onExit(activeMode, ctx);
            activeMode = mode;
            onEnter(mode, ctx);
            log.info("[camera] mode={}", mode);
        }

        // rebuild movement axes from key states
        mx = (dDown ? 1f : 0f) + (aDown ? -1f : 0f);
        my = (eDown ? 1f : 0f) + (qDown ? -1f : 0f);
        mz = (wDown ? 1f : 0f) + (sDown ? -1f : 0f);

        // --- mouse fallback: if no Analog events this frame, derive from cursor position
        if (!mouseAxisEvent) {
            Vector2f cur = input != null ? input.getCursorPosition() : null;
            if (cur != null) {
                if (!cursorInit) {
                    lastCursor.set(cur);
                    cursorInit = true;
                } else {
                    float dx = cur.x - lastCursor.x;
                    float dy = cur.y - lastCursor.y;
                    lastCursor.set(cur);

                    // same direction convention as MouseAxisTrigger:
                    // X right positive, Y up positive (cursor goes down with +Y usually),
                    // so we invert dy to feel natural (move mouse up => look up).
                    mouseDx += dx;
                    mouseDy += -dy;
                }
            }
        }

        // feed inputs to controllers
        free.mouseDx = mouseDx;
        free.mouseDy = mouseDy;
        free.moveX = mx;
        free.moveY = my;
        free.moveZ = mz;

        fp.mouseDx = mouseDx;
        fp.mouseDy = mouseDy;

        tp.mouseDx = mouseDx;
        tp.mouseDy = mouseDy;
        tp.wheelDelta = wheel;

        c.update(ctx, state, tpf);

        // optional periodic debug (1/sec)
        if ((ctx.app().getTimer().getTimeInSeconds() % 1.0f) < tpf) {
            log.info("[camdbg] dx={} dy={} mx={} my={} mz={} wheel={} mouseAxisEvent={}",
                    mouseDx, mouseDy, mx, my, mz, wheel, mouseAxisEvent);
        }

        consumeDeltas();
    }

    private void consumeDeltas() {
        mouseDx = mouseDy = wheel = 0f;
        mouseAxisEvent = false;
    }

    private void onEnter(String mode, SystemContext ctx) {
        switch (mode) {
            case "free" -> free.onEnter(ctx, state);
            case "firstperson" -> fp.onEnter(ctx, state);
            case "thirdperson" -> tp.onEnter(ctx, state);
            default -> {}
        }
    }

    private void onExit(String mode, SystemContext ctx) {
        switch (mode) {
            case "free" -> free.onExit(ctx, state);
            case "firstperson" -> fp.onExit(ctx, state);
            case "thirdperson" -> tp.onExit(ctx, state);
            default -> {}
        }
    }

    // ---------------- input mapping ----------------

    private final ActionListener actionListener = (name, pressed, tpf) -> {
        switch (name) {
            case "kt.cam.mode.free" -> f1Down = pressed;
            case "kt.cam.mode.fp"   -> f2Down = pressed;
            case "kt.cam.mode.tp"   -> f3Down = pressed;

            case "kt.cam.w" -> wDown = pressed;
            case "kt.cam.s" -> sDown = pressed;
            case "kt.cam.a" -> aDown = pressed;
            case "kt.cam.d" -> dDown = pressed;
            case "kt.cam.q" -> qDown = pressed;
            case "kt.cam.e" -> eDown = pressed;
        }
    };

    private final AnalogListener analogListener = (name, value, tpf) -> {
        switch (name) {
            case "kt.cam.mx+" -> { mouseDx += value; mouseAxisEvent = true; }
            case "kt.cam.mx-" -> { mouseDx -= value; mouseAxisEvent = true; }
            case "kt.cam.my+" -> { mouseDy += value; mouseAxisEvent = true; }
            case "kt.cam.my-" -> { mouseDy -= value; mouseAxisEvent = true; }
            case "kt.cam.wh+" -> wheel += value;
            case "kt.cam.wh-" -> wheel -= value;
        }
    };

    private void registerMappings(InputManager in) {
        // movement
        add(in, "kt.cam.w", new KeyTrigger(KeyInput.KEY_W));
        add(in, "kt.cam.s", new KeyTrigger(KeyInput.KEY_S));
        add(in, "kt.cam.a", new KeyTrigger(KeyInput.KEY_A));
        add(in, "kt.cam.d", new KeyTrigger(KeyInput.KEY_D));
        add(in, "kt.cam.q", new KeyTrigger(KeyInput.KEY_Q));
        add(in, "kt.cam.e", new KeyTrigger(KeyInput.KEY_E));

        // look + wheel
        add(in, "kt.cam.mx+", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        add(in, "kt.cam.mx-", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        add(in, "kt.cam.my+", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        add(in, "kt.cam.my-", new MouseAxisTrigger(MouseInput.AXIS_Y, true));

        add(in, "kt.cam.wh+", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, false));
        add(in, "kt.cam.wh-", new MouseAxisTrigger(MouseInput.AXIS_WHEEL, true));

        // mode hotkeys
        add(in, "kt.cam.mode.free", new KeyTrigger(KeyInput.KEY_F1));
        add(in, "kt.cam.mode.fp",   new KeyTrigger(KeyInput.KEY_F2));
        add(in, "kt.cam.mode.tp",   new KeyTrigger(KeyInput.KEY_F3));
    }

    private void add(InputManager in, String name, Trigger t) {
        mappings.add(name);
        in.addMapping(name, t);
    }

    // edge detector for F-keys based on ActionListener-fed states
    private boolean edge(int key) {
        return switch (key) {
            case KeyInput.KEY_F1 -> { boolean e = f1Down && !f1Latch; f1Latch = f1Down; yield e; }
            case KeyInput.KEY_F2 -> { boolean e = f2Down && !f2Latch; f2Latch = f2Down; yield e; }
            case KeyInput.KEY_F3 -> { boolean e = f3Down && !f3Latch; f3Latch = f3Down; yield e; }
            default -> false;
        };
    }

    private static String normalizeMode(String raw) {
        String m = (raw == null ? "free" : raw).trim().toLowerCase();

        // JS часто шлёт camelCase:
        if (m.equals("firstperson")) return "firstperson";
        if (m.equals("thirdperson")) return "thirdperson";

        // aliases
        if (m.equals("fly")) return "free";
        if (m.equals("free")) return "free";
        if (m.equals("first_person") || m.equals("first-person") || m.equals("firstpersoncamera")) return "firstperson";
        if (m.equals("third_person") || m.equals("third-person") || m.equals("thirdpersoncamera")) return "thirdperson";

        return m;
    }

    // -------- resolve rigid body by userObject --------
    private PhysicsRigidBody findRigidBodyById(int bodyId) {
        if (bodyId <= 0) return null;

        // IMPORTANT: rigidBody.setUserObject(Integer.valueOf(bodyId));
        for (PhysicsRigidBody b : physics.getRigidBodyList()) {
            Object u = b.getUserObject();
            if (u instanceof Integer && ((Integer) u) == bodyId) return b;
            if (u != null && String.valueOf(bodyId).equals(u.toString())) return b;
        }
        return null;
    }

    // helpers
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