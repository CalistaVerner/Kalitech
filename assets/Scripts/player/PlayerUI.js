"use strict";

function num(v, fb) {
    v = +v;
    return Number.isFinite(v) ? v : fb;
}

function isObj(v) {
    return !!v && typeof v === "object" && !Array.isArray(v);
}

class PlayerUI {
    constructor(player) {
        this.player = player;

        const c = (player.cfg && player.cfg.ui) ? player.cfg.ui : Object.create(null);

        this.layerName = String(c.layerName || "debug-ui");
        this.anchor = String(c.anchor || "tl");
        this.mx = num(c.marginLeft, 10);
        this.my = num(c.marginTop, 10);

        this.w = num(c.w, 280);
        this.padX = num(c.padX, 12);
        this.padY = num(c.padY, 8);

        this.fontTitle = num(c.fontTitle, 16);
        this.fontLine = num(c.fontLine, 14);
        this.gap = num(c.lineGap, 4);

        this.style = isObj(c.style) ? c.style : {
            bg: {color: "#0b0f14", alpha: 0.65},
            border: {size: 1, color: "#8AA0B6", alpha: 0.45, radius: 8}
        };

        this.layer = null;
        this.panel = null;
    }

    create() {
        if (this.layer) return this;

        const HUD = this.player.d.hud;
        const layer = HUD.layer(this.layerName);
        this.layer = layer;

        this.panel = layer.panel({
            id: "debug.panel",
            w: this.w,
            h: 60,
            autoHeight: true,
            padX: this.padX,
            padY: this.padY,
            flow: { fontSize: this.fontLine, gap: this.gap },
            place: { anchor: this.anchor, x: this.mx, y: this.my },
            style: this.style
        });

        this.panel.stack("debug.title", "DEBUG", {fontSize: 18, color: "#FFFFFF"});
        this.panel.stack("debug.fps", "FPS: --", {fontSize: this.fontLine, color: "#FFFFFF"});
        this.panel.stack("debug.pos", "POS: --", {fontSize: this.fontLine, color: "#FFFFFF"});
        this.panel.stack("debug.camType", "CAM(type): --", {fontSize: this.fontLine, color: "#FFFFFF"});
        this.panel.stack("debug.camYaw", "CAM(yaw): --", {fontSize: this.fontLine, color: "#FFFFFF"});
        this.panel.stack("debug.camPitch", "CAM(pitch): --", {fontSize: this.fontLine, color: "#FFFFFF"});
        this.panel.stack("debug.worldTime", "WorldTime: --", {fontSize: this.fontLine, color: "#FFFFFF"});
        this.panel.stack("debug.timeRate", "timeRate: --", {fontSize: this.fontLine, color: "#FFFFFF"});

        if (typeof layer.relayout === "function") layer.relayout();
        this.refresh();
        return this;
    }

    refresh() {
        const layer = this.layer;
        if (!layer || typeof layer.setText !== "function") return;

        const f = this.player.frame;
        const p = f.pose;

        const camType = f.view.type;
        const camYaw = f.view.yaw;
        const camPitch = f.view.pitch;
        const worldTime = WORLD.getWorldTime();
        const eng = this.player.d.engine;
        const fps = (eng && typeof eng.fps === "function") ? (+eng.fps() || 0) : 0;

        layer.setText("debug.fps", "FPS: " + fps);
        layer.setText("debug.pos", "POS: " + p.x.toFixed(2) + " | " + p.y.toFixed(2) + " | " + p.z.toFixed(2));
        layer.setText("debug.camType", "CAM(type): " + camType);
        layer.setText("debug.camYaw", "CAM(yaw): " + camYaw);
        layer.setText("debug.camPitch", "CAM(pitch): " + camPitch);
        layer.setText("debug.worldTime", "WorldTime: " + worldTime.worldTime);
        layer.setText("debug.timeRate", "WorldTime: " + worldTime.timeRate);
    }

    destroy() {
        const layer = this.layer;
        if (!layer) return;
        if (typeof layer.destroy === "function") layer.destroy();
        this.layer = null;
        this.panel = null;
    }
}

module.exports = PlayerUI;