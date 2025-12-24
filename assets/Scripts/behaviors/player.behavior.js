// Author: Calista Verner
const CFG = {
    moveSpeed: 6.0,
    sprintMul: 1.7,
    mouseSensitivity: 0.002,
    maxPitch: 1.45
};

let yaw = 0.0;
let pitch = 0.0;

function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }

module.exports.init = function (api) {
    api.log().info("[player] init id=" + api.entityId());
    yaw = 0.0;
    pitch = 0.0;
};

module.exports.update = function (api, tpf) {
    const engine = api.engine();
    const input = engine.input();
    const ent = api.entity();
    const id = api.entityId();

    const dx = input.mouseDX();
    const dy = input.mouseDY();

    yaw   -= dx * CFG.mouseSensitivity;
    pitch -= dy * CFG.mouseSensitivity;
    pitch = clamp(pitch, -CFG.maxPitch, CFG.maxPitch);

    engine.camera().setYawPitch(yaw, pitch);

    let mx = 0, mz = 0;
    if (input.keyDown("W")) mz -= 1;
    if (input.keyDown("S")) mz += 1;
    if (input.keyDown("A")) mx -= 1;
    if (input.keyDown("D")) mx += 1;

    if (mx === 0 && mz === 0) return;

    const sprint = input.keyDown("SHIFT");
    const speed = CFG.moveSpeed * (sprint ? CFG.sprintMul : 1.0);

    const len = Math.hypot(mx, mz);
    mx /= len; mz /= len;

    const sin = Math.sin(yaw);
    const cos = Math.cos(yaw);

    const vx = (mx * cos - mz * sin) * speed * tpf;
    const vz = (mz * cos + mx * sin) * speed * tpf;

    const tr = ent.getComponent(id, "Transform") || { x: 0, y: 0, z: 0 };
    tr.x += vx;
    tr.z += vz;
    tr.rotY = yaw;
    ent.setComponent(id, "Transform", tr);
};

module.exports.destroy = function (api) {
    api.log().info("[player] destroy id=" + api.entityId());
};