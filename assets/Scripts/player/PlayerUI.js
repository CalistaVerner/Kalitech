"use strict";

/**
 * @param {*} v
 * @param {number} fb
 * @returns {number}
 */
function num(v, fb) {
    v = +v;
    return Number.isFinite(v) ? v : fb;
}

/**
 * @param {*} v
 * @returns {boolean}
 */
function isObj(v) {
    return !!v && typeof v === "object" && !Array.isArray(v);
}

/**
 * Fast 2-decimal formatter for quantized integer (value * 100).
 * Produces stable output with minimal work, only when value changed.
 *
 * @param {number} q integer = round(value * 100)
 * @returns {string}
 */
function fmt2FromQ(q) {
    // q can be negative
    const neg = q < 0;
    if (neg) q = -q;

    const i = (q / 100) | 0;
    const f = q - i * 100;

    // Avoid arrays; simple concatenation is fine (called only on change).
    return (neg ? "-" : "") + i + "." + (f < 10 ? "0" : "") + f;
}

/**
 * Deterministic debug HUD for a player.
 *
 * Lifecycle contract:
 * - create(): allocate layer + all elements exactly once
 * - update(model): mutate values only (setText/Visible/Value), no element creation
 * - destroy(): destroy layer deterministically
 */
class PlayerUI {
    /**
     * @param {*} player engine-side player object
     */
    constructor(player) {
        this.player = player;

        const c = (player && player.cfg && player.cfg.ui) ? player.cfg.ui : Object.create(null);

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

        this.idPrefix = String(c.idPrefix || "player.debug");

        this.style = isObj(c.style) ? c.style : {
            bg: {color: "#0b0f14", alpha: 0.65},
            border: {size: 1, color: "#8AA0B6", alpha: 0.45, radius: 8}
        };

        this.layer = null;

        // Prebuilt stable ids (no string concat in update()).
        this._id = Object.freeze({
            panel: this.idPrefix + ".panel",
            title: this.idPrefix + ".title",
            fps: this.idPrefix + ".fps",
            pos: this.idPrefix + ".pos",
            camType: this.idPrefix + ".camType",
            camYaw: this.idPrefix + ".camYaw",
            camPitch: this.idPrefix + ".camPitch",
            worldTime: this.idPrefix + ".worldTime",
            timeRate: this.idPrefix + ".timeRate"
        });

        // Dirty-cache (numbers) + last rendered strings.
        this._cache = {
            fps: -1,

            posXq: 0x7fffffff,
            posYq: 0x7fffffff,
            posZq: 0x7fffffff,
            posStr: "",

            camType: "",
            camYaw: NaN,
            camPitch: NaN,

            worldTime: NaN,
            timeRate: NaN
        };
    }

    /**
     * Allocate and build UI once.
     * @returns {PlayerUI}
     */
    create() {
        if (this.layer) return this;

        const HUD = this.player && this.player.d && this.player.d.hud;
        if (!HUD) return this;

        const layer = HUD.layer(this.layerName);
        this.layer = layer;

        // Build once via spec; runtime updates are only setText/setValue/setVisible.
        layer.spec(this._buildSpec(), {relayout: true});

        // Force first full update.
        this._invalidateCache();

        this.update();
        return this;
    }

    /**
     * Pure declarative spec (create-time only).
     * @returns {object}
     */
    _buildSpec() {
        const id = this._id;

        return {
            type: "Panel",
            id: id.panel,
            w: this.w,
            h: 1,
            autoHeight: true,
            padX: this.padX,
            padY: this.padY,
            flow: {
                fontSize: this.fontLine,
                gap: this.gap
            },
            place: {
                anchor: this.anchor,
                x: this.mx,
                y: this.my
            },
            style: this.style,
            children: [
                {
                    type: "Text",
                    id: id.title,
                    text: "DEBUG",
                    fontSize: this.fontTitle,
                    color: "#FFFFFF"
                },
                {type: "Text", id: id.fps, text: "FPS: --"},
                {type: "Text", id: id.pos, text: "POS: --"},
                {type: "Text", id: id.camType, text: "CAM(type): --"},
                {type: "Text", id: id.camYaw, text: "CAM(yaw): --"},
                {type: "Text", id: id.camPitch, text: "CAM(pitch): --"},
                {type: "Text", id: id.worldTime, text: "WorldTime: --"},
                {type: "Text", id: id.timeRate, text: "timeRate: --"}
            ]
        };
    }

    /**
     * Backward-compatible alias (old call sites can keep using refresh()).
     */
    refresh() {
        this.update();
    }

    /**
     * Update values only. No element creation.
     * Uses dirty-check to minimize string allocations and Java calls.
     *
     * @param {*} model optional external model; if not provided uses player.frame
     */
    update(model) {
        const layer = this.layer;
        if (!layer) return;

        const player = this.player;
        const frame = model || (player && player.frame);
        if (!frame) return;

        const pose = frame.pose;
        const view = frame.view;

        // FPS
        const eng = player && player.d && player.d.engine;
        const fps = (eng && typeof eng.fps === "function") ? (eng.fps() | 0) : 0;
        if (fps !== this._cache.fps) {
            this._cache.fps = fps;
            layer.setText(this._id.fps, "FPS: " + fps);
        }

        // Position (quantize *100 and update only on change)
        if (pose) {
            const xq = Math.round(pose.x * 100);
            const yq = Math.round(pose.y * 100);
            const zq = Math.round(pose.z * 100);

            if (xq !== this._cache.posXq || yq !== this._cache.posYq || zq !== this._cache.posZq) {
                this._cache.posXq = xq;
                this._cache.posYq = yq;
                this._cache.posZq = zq;

                const s = "POS: " + fmt2FromQ(xq) + " | " + fmt2FromQ(yq) + " | " + fmt2FromQ(zq);
                this._cache.posStr = s;
                layer.setText(this._id.pos, s);
            }
        }

        // Camera type / yaw / pitch
        if (view) {
            const camType = String(view.type);
            if (camType !== this._cache.camType) {
                this._cache.camType = camType;
                layer.setText(this._id.camType, "CAM(type): " + camType);
            }

            const yaw = +view.yaw;
            if (Number.isFinite(yaw) && yaw !== this._cache.camYaw) {
                this._cache.camYaw = yaw;
                layer.setText(this._id.camYaw, "CAM(yaw): " + yaw);
            }

            const pitch = +view.pitch;
            if (Number.isFinite(pitch) && pitch !== this._cache.camPitch) {
                this._cache.camPitch = pitch;
                layer.setText(this._id.camPitch, "CAM(pitch): " + pitch);
            }
        }

        // World time (guard if WORLD is absent)
        if (typeof WORLD !== "undefined" && WORLD && typeof WORLD.getWorldTime === "function") {
            const wt = WORLD.getWorldTime();
            if (wt) {
                const worldTime = +wt.worldTime;
                const timeRate = +wt.timeRate;

                if (Number.isFinite(worldTime) && worldTime !== this._cache.worldTime) {
                    this._cache.worldTime = worldTime;
                    layer.setText(this._id.worldTime, "WorldTime: " + worldTime);
                }
                if (Number.isFinite(timeRate) && timeRate !== this._cache.timeRate) {
                    this._cache.timeRate = timeRate;
                    layer.setText(this._id.timeRate, "timeRate: " + timeRate);
                }
            }
        }
    }

    /**
     * Destroy whole layer deterministically.
     */
    destroy() {
        if (!this.layer) return;
        this.layer.destroy();
        this.layer = null;
        this._invalidateCache();
    }

    /**
     * Force next update to push all values.
     */
    _invalidateCache() {
        const c = this._cache;
        c.fps = -1;

        c.posXq = 0x7fffffff;
        c.posYq = 0x7fffffff;
        c.posZq = 0x7fffffff;
        c.posStr = "";

        c.camType = "";
        c.camYaw = NaN;
        c.camPitch = NaN;

        c.worldTime = NaN;
        c.timeRate = NaN;
    }
}

module.exports = PlayerUI;