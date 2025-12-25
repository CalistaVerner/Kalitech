// FILE: Scripts/systems/editor.grid.system.js
// Author: Calista Verner
"use strict";

exports.meta = {
    id: "kalitech.system.editor.grid",
    version: "1.2.0",
    apiMin: "0.1.0",
    name: "Editor Grid Plane"
};

class EditorGridSystem {
    constructor() {
        this.state = {
            enabled: true,
            halfExtent: 200,
            size: 200,
            step: 1.0,
            majorStep: 10,
            y: 0.02,
            opacity: 0.85,
            minorColor: { r: 0.05, g: 0.06, b: 0.08 },
            majorColor: { r: 0.10, g: 0.12, b: 0.16 },
            minorThickness: 0.02,
            majorThickness: 0.06
        };

        this.created = false;
        this.handle = null;
        this.warned = false;
        this.lastKey = "";
    }

    norm01(v) {
        return clamp(Number(v) || 0, 0.0, 1.0);
    }

    sanitize() {
        let he = Number(this.state.halfExtent || this.state.size || 200);
        he = clamp(he, 10, 500000);

        this.state.halfExtent = he;
        this.state.size = he;

        this.state.step = clamp(Number(this.state.step) || 1.0, 0.01, 100000);
        this.state.majorStep = clamp((Math.round(Number(this.state.majorStep) || 10) | 0), 1, 1000000);
        this.state.y = clamp(Number(this.state.y) || 0.02, -500000, 500000);
        this.state.opacity = clamp(Number(this.state.opacity) || 0.85, 0.0, 1.0);

        if (!this.state.minorColor) this.state.minorColor = { r: 0.05, g: 0.06, b: 0.08 };
        if (!this.state.majorColor) this.state.majorColor = { r: 0.10, g: 0.12, b: 0.16 };

        this.state.minorColor = {
            r: this.norm01(this.state.minorColor.r),
            g: this.norm01(this.state.minorColor.g),
            b: this.norm01(this.state.minorColor.b)
        };

        this.state.majorColor = {
            r: this.norm01(this.state.majorColor.r),
            g: this.norm01(this.state.majorColor.g),
            b: this.norm01(this.state.majorColor.b)
        };

        this.state.minorThickness = clamp(Number(this.state.minorThickness) || 0.02, 0.0005, 10.0);
        this.state.majorThickness = clamp(Number(this.state.majorThickness) || 0.06, 0.0005, 10.0);
    }

    key() {
        const s = this.state;
        const he = Number(s.halfExtent || s.size || 200);
        return [
            s.enabled ? 1 : 0,
            he,
            Number(s.step),
            Number(s.majorStep),
            Number(s.y),
            Number(s.opacity),
            this.norm01(s.minorColor.r), this.norm01(s.minorColor.g), this.norm01(s.minorColor.b),
            this.norm01(s.majorColor.r), this.norm01(s.majorColor.g), this.norm01(s.majorColor.b),
            Number(s.minorThickness),
            Number(s.majorThickness)
        ].join("|");
    }

    buildCfg() {
        const s = this.state;
        return {
            halfExtent: s.halfExtent,
            step: s.step,
            majorStep: s.majorStep,
            y: s.y,
            opacity: s.opacity,
            minorColor: { r: s.minorColor.r, g: s.minorColor.g, b: s.minorColor.b },
            majorColor: { r: s.majorColor.r, g: s.majorColor.g, b: s.majorColor.b },
            minorThickness: s.minorThickness,
            majorThickness: s.majorThickness
        };
    }

    editorLines() {
        try { return engine.editorLines(); } catch (_) { return null; }
    }

    tryCreate(log) {
        if (this.created) return true;

        const el = this.editorLines();
        if (!el) {
            if (!this.warned) {
                this.warned = true;
                log.warn("[editor.grid] editorLines backend not available yet");
            }
            return false;
        }

        try {
            this.handle = el.createGridPlane(this.buildCfg());
            this.created = true;
            this.lastKey = this.key();
            log.info("[editor.grid] created handle=" + String(this.handle));
            return true;
        } catch (e) {
            if (!this.warned) {
                this.warned = true;
                log.warn("[editor.grid] editorLines backend not available yet: " + String(e));
            }
            return false;
        }
    }

    tryDestroy(log, reason) {
        if (!this.created) return;

        const el = this.editorLines();
        if (el) {
            try { el.destroy(this.handle); } catch (_) {}
        }

        log.info("[editor.grid] destroyed reason=" + String(reason || "unknown"));
        this.created = false;
        this.handle = null;
    }

    recreateIfChanged(log) {
        const k = this.key();
        if (!this.created) return;
        if (k === this.lastKey) return;

        this.tryDestroy(log, "reconfigure");
        this.tryCreate(log);
    }

    init(ctx) {
        const log = engine.log();
        try {
            const cfg = (ctx && ctx.config) ? ctx.config : ctx;
            if (cfg) this.state = deepMerge(this.state, cfg);
        } catch (_) {}

        this.sanitize();
        if (this.state.enabled) this.tryCreate(log);
    }

    update() {
        if (!this.state.enabled) return;

        const log = engine.log();

        if (!this.created) {
            this.tryCreate(log);
            return;
        }

        this.recreateIfChanged(log);
    }

    destroy(reason) {
        this.tryDestroy(engine.log(), reason);
    }

    serialize() {
        return this.state;
    }

    deserialize(s) {
        if (!s) return;
        this.state = deepMerge(this.state, s);
        this.sanitize();
        this.lastKey = "";
    }
}

exports.create = function () {
    return new EditorGridSystem();
};