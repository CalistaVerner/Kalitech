// FILE: assets/Scripts/entities/player.behavior.js
//
// Player per-entity behavior
// Contract:
//   init(api)
//   update(api, tpf)
//   destroy(api)
//
// api:
//   - api.engine() -> EngineApi
//   - api.entityId()
//   - api.log()
//   - api.entity()

// ------------------------------------------------------------
// Config (tweakable, no magic numbers in logic)
// ------------------------------------------------------------

const CFG = {
    moveSpeed: 6.0,          // units per second
    sprintMul: 1.7,
    mouseSensitivity: 0.002,
    maxPitch: 1.45           // ~83 degrees
};

// ------------------------------------------------------------
// Internal state (per-entity instance)
// ------------------------------------------------------------

let yaw = 0.0;
let pitch = 0.0;

let initialized = false;

// ------------------------------------------------------------
// Helpers
// ------------------------------------------------------------

function clamp(v, lo, hi) {
    return Math.max(lo, Math.min(hi, v));
}

function vec3(x = 0, y = 0, z = 0) {
    return { x, y, z };
}

// ------------------------------------------------------------
// Lifecycle
// ------------------------------------------------------------

module.exports.init = function (api) {
    const log = api.log();
    log.info("[player.behavior] init entity=" + api.entityId());

    // Optional: hide cursor / lock later if you add such API
    yaw = 0.0;
    pitch = 0.0;
    initialized = true;
};

module.exports.update = function (api, tpf) {
    if (!initialized) return;

    const engine = api.engine();
    const input  = engine.input();
    const ent    = api.entity();
    const id     = api.entityId();

    // ----------------------------------------------------------
    // Mouse look
    // ----------------------------------------------------------

    const dx = input.mouseDX();
    const dy = input.mouseDY();

    yaw   -= dx * CFG.mouseSensitivity;
    pitch -= dy * CFG.mouseSensitivity;
    pitch = clamp(pitch, -CFG.maxPitch, CFG.maxPitch);

    // Apply to camera (player is camera owner)
    engine.camera().setYawPitch(yaw, pitch);

    // ----------------------------------------------------------
    // Movement input (WASD)
    // ----------------------------------------------------------

    let mx = 0;
    let mz = 0;

    if (input.keyDown("W")) mz -= 1;
    if (input.keyDown("S")) mz += 1;
    if (input.keyDown("A")) mx -= 1;
    if (input.keyDown("D")) mx += 1;

    if (mx !== 0 || mz !== 0) {
        const sprint = input.keyDown("SHIFT");
        const speed = CFG.moveSpeed * (sprint ? CFG.sprintMul : 1.0);

        // Normalize movement
        const len = Math.hypot(mx, mz);
        mx /= len;
        mz /= len;

        // Move relative to camera yaw
        const sin = Math.sin(yaw);
        const cos = Math.cos(yaw);

        const vx = (mx * cos - mz * sin) * speed * tpf;
        const vz = (mz * cos + mx * sin) * speed * tpf;

        // --------------------------------------------------------
        // Apply movement to entity Transform
        // --------------------------------------------------------

        // Expected Transform shape:
        // { x, y, z, rotY? }  (rotY optional)
        const tr = ent.getComponent(id, "Transform") || vec3();

        tr.x += vx;
        tr.z += vz;
        tr.rotY = yaw; // keep entity facing direction

        ent.setComponent(id, "Transform", tr);
    }
};

module.exports.destroy = function (api) {
    api.log().info("[player.behavior] destroy entity=" + api.entityId());
};