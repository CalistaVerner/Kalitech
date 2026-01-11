// FILE: resources/kalitech/builtin/Debug.js
// Author: Calista Verner
"use strict";

/**
 * DEBUG: safe, scoped, convenient wrapper over engine.debug()
 *
 * Goals:
 *  - same "rootkit" style as Log.js (safe formatting, scoped children, frozen API)
 *  - zero-crash behavior (never throw during debug draw)
 *  - ergonomic helpers: line/ray/axes/box/sphere/circle/polyline/grid
 *  - defaults with chainable setters: ttl/depth/alpha
 *
 * Contract expectations from Engine:
 *  - engine.debug() returns object with methods:
 *      enabled(boolean), enabled(), clear(), tick(tpf), line(cfg), ray(cfg), axes(cfg)
 *    and optionally: box(cfg), sphere(cfg), circle(cfg), polyline(cfg), grid(cfg)
 */

function safeJson(v) {
    try {
        return JSON.stringify(v);
    } catch (_) {
    }
    try {
        return String(v);
    } catch (_) {
    }
    return "[unserializable]";
}

function isObj(v) {
    return !!v && typeof v === "object";
}

function clamp01(x) {
    x = Number(x);
    if (!(x === x)) return 0;
    if (x < 0) return 0;
    if (x > 1) return 1;
    return x;
}

// -----------------------------
// Vec/Color normalizers
// -----------------------------

function v3(x, y, z, dx, dy, dz) {
    // Accept: [x,y,z], {x,y,z}, or scalars
    if (x == null) return [dx || 0, dy || 0, dz || 0];

    if (Array.isArray(x)) {
        return [
            Number(x[0] ?? (dx || 0)) || 0,
            Number(x[1] ?? (dy || 0)) || 0,
            Number(x[2] ?? (dz || 0)) || 0
        ];
    }

    if (isObj(x) && typeof x.x === "number") {
        return [
            Number(x.x) || 0,
            Number(x.y) || 0,
            Number(x.z) || 0
        ];
    }

    return [
        Number(x ?? (dx || 0)) || 0,
        Number(y ?? (dy || 0)) || 0,
        Number(z ?? (dz || 0)) || 0
    ];
}

function rgba(r, g, b, a, dr, dg, db, da) {
    // Accept: [r,g,b,a?], {r,g,b,a?}, or scalars
    if (r == null) return [dr ?? 1, dg ?? 1, db ?? 1, da ?? 1];

    if (Array.isArray(r)) {
        return [
            clamp01(r[0] ?? (dr ?? 1)),
            clamp01(r[1] ?? (dg ?? 1)),
            clamp01(r[2] ?? (db ?? 1)),
            clamp01(r[3] ?? (da ?? 1))
        ];
    }

    if (isObj(r) && (typeof r.r === "number" || typeof r.g === "number" || typeof r.b === "number")) {
        return [
            clamp01(r.r ?? (dr ?? 1)),
            clamp01(r.g ?? (dg ?? 1)),
            clamp01(r.b ?? (db ?? 1)),
            clamp01(r.a ?? (da ?? 1))
        ];
    }

    return [
        clamp01(r ?? (dr ?? 1)),
        clamp01(g ?? (dg ?? 1)),
        clamp01(b ?? (db ?? 1)),
        clamp01(a ?? (da ?? 1))
    ];
}

// -----------------------------
// Engine binding / safe caller
// -----------------------------

function makePrefix(scope) {
    const s = String(scope || "").trim();
    return s ? "[" + s + "] " : "";
}

function makeApi(engine /*, K */) {
    const dbg = (engine && engine.debug && typeof engine.debug === "function") ? engine.debug() : null;

    function has(fn) {
        return !!(dbg && typeof dbg[fn] === "function");
    }

    function call(fn, cfgOrArg) {
        if (!dbg) return null;
        try {
            if (!has(fn)) return null;
            return dbg[fn](cfgOrArg);
        } catch (_) {
            return null;
        }
    }

    function call0(fn) {
        if (!dbg) return null;
        try {
            if (!has(fn)) return null;
            return dbg[fn]();
        } catch (_) {
            return null;
        }
    }

    function callN(fn /*, ...args */) {
        if (!dbg) return null;
        try {
            if (!has(fn)) return null;
            return dbg[fn].apply(dbg, Array.prototype.slice.call(arguments, 1));
        } catch (_) {
            return null;
        }
    }

    // -----------------------------
    // Defaults + scoped state
    // -----------------------------

    function makeState(scopeName, parentState) {
        const scope = String(scopeName || "").trim();
        const prefix = makePrefix(scope);

        // state is mutable, API is frozen
        const S = {
            ttl: (parentState && parentState.ttl != null) ? parentState.ttl : 0.15,
            depthTest: (parentState && parentState.depthTest != null) ? parentState.depthTest : true,
            alpha: (parentState && parentState.alpha != null) ? parentState.alpha : 1.0,

            // optional global knobs
            depthWrite: (parentState && parentState.depthWrite != null) ? parentState.depthWrite : undefined
        };

        function withCommon(cfg, ttl, depthTest, alpha, depthWrite) {
            const out = cfg || {};

            // stamp scope as tag (only for debugging while reading configs; engine ignores unknown fields)
            // out.tag = prefix ? prefix.slice(0, -1) : ""; // optional

            const t = (ttl != null) ? Number(ttl) : Number(S.ttl);
            out.ttl = (t > 0) ? t : 0.0;

            out.depthTest = (depthTest != null) ? !!depthTest : !!S.depthTest;

            const a = (alpha != null) ? Number(alpha) : Number(S.alpha);
            if (a === a) {
                // set alpha to color if exists; otherwise leave (engine defaults)
                // we apply by multiplying if color provided in helpers below
                out._alpha = a; // internal hint for helpers
            }

            if (depthWrite != null) out.depthWrite = !!depthWrite;
            else if (S.depthWrite != null) out.depthWrite = !!S.depthWrite;

            return out;
        }

        // -----------------------------
        // Public API (scope)
        // -----------------------------

        function enabled(v) {
            if (v == null) return !!call0("enabled");
            callN("enabled", !!v);
            return !!v;
        }

        function clear() {
            call0("clear");
        }

        function tick(tpf) {
            callN("tick", Number(tpf) || 0);
        }

        // chainable state setters
        function setTTL(sec) {
            S.ttl = Number(sec);
            if (!(S.ttl === S.ttl)) S.ttl = 0.0;
            return api;
        }

        function setDepth(v) {
            S.depthTest = !!v;
            return api;
        }

        function setAlpha(a) {
            S.alpha = Number(a);
            if (!(S.alpha === S.alpha)) S.alpha = 1.0;
            return api;
        }

        function setDepthWrite(v) {
            S.depthWrite = (v == null) ? undefined : !!v;
            return api;
        }

        function line(a, b, color, ttl, depthTest, alpha) {
            const cfg = withCommon({
                a: v3(a, null, null, null, 0, 0, 0),
                b: v3(b, null, null, null, 0, 1, 0)
            }, ttl, depthTest, alpha);

            const c = rgba(color, null, null, null, 1, 1, 0, 1);
            const aa = (cfg._alpha != null) ? cfg._alpha : c[3];
            cfg.color = [c[0], c[1], c[2], clamp01(aa)];

            // if user passes just scope string as "color" by mistake - protect:
            if (typeof color === "string") cfg.color = [1, 1, 0, clamp01(cfg._alpha ?? 1)];

            // strip internal hint
            delete cfg._alpha;

            // prefix is not printed (this is draw API), but you can keep it in cfg for tracing
            // cfg.scope = prefix;

            call("line", cfg);
            return api;
        }

        function ray(origin, dir, len, color, ttl, depthTest, alpha, arrow, arrowSize) {
            const cfg = withCommon({
                origin: v3(origin, null, null, null, 0, 0, 0),
                dir: v3(dir, null, null, null, 0, 1, 0),
                len: (len != null) ? Number(len) : 1.0,
                arrow: (arrow != null) ? !!arrow : true
            }, ttl, depthTest, alpha);

            if (arrowSize != null) cfg.arrowSize = Number(arrowSize);

            const c = rgba(color, null, null, null, 1, 1, 0, 1);
            const aa = (cfg._alpha != null) ? cfg._alpha : c[3];
            cfg.color = [c[0], c[1], c[2], clamp01(aa)];

            delete cfg._alpha;
            call("ray", cfg);
            return api;
        }

        function axes(pos, size, ttl, depthTest) {
            const cfg = withCommon({
                pos: v3(pos, null, null, null, 0, 0, 0),
                size: (size != null) ? Number(size) : 1.0
            }, ttl, depthTest, null);

            delete cfg._alpha;
            call("axes", cfg);
            return api;
        }

        // Optional shapes (only call if engine supports them)
        function box(center, sizeOrHalf, color, ttl, depthTest, alpha, rotOrEulerDeg) {
            const cfg = withCommon({
                center: v3(center, null, null, null, 0, 0, 0)
            }, ttl, depthTest, alpha);

            // accept either {half:[...]} or size vector passed
            cfg.size = v3(sizeOrHalf, null, null, null, 1, 1, 1);

            // rotOrEulerDeg: quat [x,y,z,w] or eulerDeg [x,y,z]
            if (rotOrEulerDeg != null) {
                if (Array.isArray(rotOrEulerDeg) && rotOrEulerDeg.length >= 4) cfg.rot = rotOrEulerDeg;
                else cfg.eulerDeg = v3(rotOrEulerDeg, null, null, null, 0, 0, 0);
            }

            const c = rgba(color, null, null, null, 0.95, 0.95, 0.95, 1);
            const aa = (cfg._alpha != null) ? cfg._alpha : c[3];
            cfg.color = [c[0], c[1], c[2], clamp01(aa)];

            delete cfg._alpha;
            call("box", cfg);
            return api;
        }

        function sphere(center, radius, color, ttl, depthTest, alpha, segments) {
            const cfg = withCommon({
                center: v3(center, null, null, null, 0, 0, 0),
                radius: (radius != null) ? Number(radius) : 1.0,
                segments: (segments != null) ? (Number(segments) | 0) : 24
            }, ttl, depthTest, alpha);

            const c = rgba(color, null, null, null, 0.9, 0.9, 0.9, 1);
            const aa = (cfg._alpha != null) ? cfg._alpha : c[3];
            cfg.color = [c[0], c[1], c[2], clamp01(aa)];

            delete cfg._alpha;
            call("sphere", cfg);
            return api;
        }

        function circle(center, normal, radius, color, ttl, depthTest, alpha, segments) {
            const cfg = withCommon({
                center: v3(center, null, null, null, 0, 0, 0),
                normal: v3(normal, null, null, null, 0, 1, 0),
                radius: (radius != null) ? Number(radius) : 1.0,
                segments: (segments != null) ? (Number(segments) | 0) : 24
            }, ttl, depthTest, alpha);

            const c = rgba(color, null, null, null, 0.9, 0.9, 0.9, 1);
            const aa = (cfg._alpha != null) ? cfg._alpha : c[3];
            cfg.color = [c[0], c[1], c[2], clamp01(aa)];

            delete cfg._alpha;
            call("circle", cfg);
            return api;
        }

        function polyline(points, closed, color, ttl, depthTest, alpha) {
            const cfg = withCommon({
                points: points || [],
                closed: !!closed
            }, ttl, depthTest, alpha);

            const c = rgba(color, null, null, null, 0.9, 0.9, 0.9, 1);
            const aa = (cfg._alpha != null) ? cfg._alpha : c[3];
            cfg.color = [c[0], c[1], c[2], clamp01(aa)];

            delete cfg._alpha;
            call("polyline", cfg);
            return api;
        }

        function grid(center, halfSize, step, ttl, depthTest, colorMajor, colorMinor, majorEvery) {
            const cfg = withCommon({
                center: v3(center, null, null, null, 0, 0.01, 0),
                halfSize: (halfSize != null) ? Number(halfSize) : 10,
                step: (step != null) ? Number(step) : 1,
                majorEvery: (majorEvery != null) ? (Number(majorEvery) | 0) : 5
            }, ttl, depthTest, null);

            if (colorMinor != null) cfg.colorMinor = rgba(colorMinor, null, null, null, 0.25, 0.35, 0.45, 0.55);
            if (colorMajor != null) cfg.colorMajor = rgba(colorMajor, null, null, null, 0.55, 0.65, 0.75, 0.85);

            delete cfg._alpha;
            call("grid", cfg);
            return api;
        }

        function child(childScope) {
            return makeState(prefix + String(childScope || "").trim(), S).api;
        }

        function supported() {
            if (!dbg) return Object.freeze({});
            const out = {};
            const fns = ["enabled", "clear", "tick", "line", "ray", "axes", "box", "sphere", "circle", "polyline", "grid"];
            for (let i = 0; i < fns.length; i++) out[fns[i]] = has(fns[i]);
            return Object.freeze(out);
        }

        const api = Object.freeze({
            // core
            enabled,
            clear,
            tick,

            // defaults (chainable)
            setTTL,
            setDepth,
            setAlpha,
            setDepthWrite,

            // primitives
            line,
            ray,
            axes,

            // shapes (optional)
            box,
            sphere,
            circle,
            polyline,
            grid,

            // misc
            child,
            scope: child,
            supported,

            // meta
            scopeName: scope,
            prefix: prefix,
            safeJson
        });

        return {api, state: S};
    }

    const root = makeState("", null);

    function enabled() {
        return !!dbg;
    }

    return Object.freeze({
        enabled,
        // root instance methods directly
        enabledDraw: root.api.enabled, // alias (rarely needed)
        clear: root.api.clear,
        tick: root.api.tick,

        // chainable defaults on root
        setTTL: root.api.setTTL,
        setDepth: root.api.setDepth,
        setAlpha: root.api.setAlpha,
        setDepthWrite: root.api.setDepthWrite,

        // root draw methods
        line: root.api.line,
        ray: root.api.ray,
        axes: root.api.axes,
        box: root.api.box,
        sphere: root.api.sphere,
        circle: root.api.circle,
        polyline: root.api.polyline,
        grid: root.api.grid,

        // scoped child
        child: root.api.child,
        scope: root.api.child,

        // feature probe
        supported: root.api.supported,

        safeJson
    });
}

function create(engine, K) {
    if (!engine) throw new Error("[DEBUG] engine is required");
    return makeApi(engine, K);
}

create.META = {
    moduleId: "debug",
    globalName: "DEBUG",
    version: "1.0.0",
    description: "Rootkit wrapper for engine.debug() with safe configs + scoped child drawers + ergonomic helpers",
    engineMin: "0.1.0"
};

module.exports = create;