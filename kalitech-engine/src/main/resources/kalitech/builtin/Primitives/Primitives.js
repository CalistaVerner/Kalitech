// FILE: resources/kalitech/builtin/Primitives.js
// Author: Calista Verner
"use strict";

/**
 * Builtin primitives factory (OO wrapper).
 *
 * Returns Primitive objects with methods like:
 *   const g = primitives.box({...})
 *   g.setMaterial({def, params})
 */
module.exports = function primitivesFactory(K) {
    K = K || (globalThis.__kalitech || Object.create(null));
    if (!K.builtins) K.builtins = Object.create(null);

    function requireEngine() {
        const eng = (K.builtins && K.builtins.engine) || K._engine || globalThis.engine;
        if (!eng) throw new Error("[builtin/primitives] engine not attached");
        return eng;
    }

    function meshApi() {
        const eng = requireEngine();
        const m = eng.mesh && eng.mesh();
        if (!m || typeof m.create !== "function") {
            throw new Error("[builtin/primitives] engine.mesh().create(cfg) is required");
        }
        return m;
    }

    function surfaceApi() {
        const eng = requireEngine();
        const s = eng.surface && eng.surface();
        if (!s) throw new Error("[builtin/primitives] engine.surface() is required");
        return s;
    }

    function normalizeCfg(cfg) {
        return (cfg && typeof cfg === "object") ? cfg : {};
    }

    function withType(type, cfg) {
        const c = normalizeCfg(cfg);
        if (!c.type) c.type = type;
        else c.type = String(c.type);
        return c;
    }

    function toSurfaceRef(handleOrId) {
        if (handleOrId == null) return null;
        if (typeof handleOrId === "number") return handleOrId;
        if (typeof handleOrId === "object") return handleOrId;
        return handleOrId;
    }

    /**
     * @class Primitive
     */
    class Primitive {
        /**
         * @param {any} handle SurfaceHandle returned by engine.mesh().create
         * @param {string} type
         */
        constructor(handle, type) {
            this._h = handle;
            this._type = String(type || "");
        }

        /**
         * Returns raw surface handle.
         * @returns {any}
         */
        handle() {
            return this._h;
        }

        /**
         * Returns surface id if available (supports id() or id field).
         * @returns {number}
         */
        id() {
            const h = this._h;
            if (!h) return 0;
            try {
                const v = h.id;
                if (typeof v === "function") return +v.call(h) || 0;
                if (typeof v === "number") return v | 0;
            } catch (_) {}
            try {
                if (typeof h.id === "function") return +h.id() || 0;
            } catch (_) {}
            return 0;
        }

        /**
         * @returns {string}
         */
        type() {
            return this._type;
        }

        /**
         * Applies a material to this primitive.
         * Accepts MaterialHandle or {def, params}.
         *
         * @param {any} materialOrCfg
         * @returns {Primitive}
         */
        setMaterial(materialOrCfg) {
            const eng = requireEngine();
            const s = surfaceApi();
            if (typeof s.setMaterial !== "function") {
                throw new Error("[builtin/primitives] engine.surface().setMaterial(surfaceOrId, matOrCfg) is required");
            }

            let m = materialOrCfg;

            // Если пришёл plain JS cfg {def, params}, сначала создаём host MaterialHandle.
            // Это убирает зависимость от того, как Graal передаст объект в Java (Value/Proxy/Map).
            if (m && typeof m === "object" && !Array.isArray(m) && m.def) {
                const matApi = eng.material && eng.material();
                if (!matApi || typeof matApi.create !== "function") {
                    throw new Error("[builtin/primitives] engine.material().create(cfg) is required");
                }
                m = matApi.create(m);
            }

            // Можно поддержать удобный кейс: material name из MaterialsRegistry
            // g.setMaterial("grass") -> materials.getHandle("grass")
            if (typeof m === "string") {
                const reg = (K && K.builtins && K.builtins.materials) || globalThis.materials;
                if (!reg || typeof reg.getHandle !== "function") {
                    throw new Error("[builtin/primitives] materials registry is required for setMaterial(name)");
                }
                m = reg.getHandle(m);
            }

            s.setMaterial(toSurfaceRef(this._h), m);
            return this;
        }


        /**
         * Sets position (if surface API supports it).
         *
         * @param {any} pos
         * @returns {Primitive}
         */
        setPos(pos) {
            const s = surfaceApi();
            const ref = toSurfaceRef(this._h);

            if (typeof s.transform === "function") {
                s.transform(ref, { pos: pos });
                return this;
            }
            if (typeof s.setPos === "function") {
                s.setPos(ref, pos);
                return this;
            }
            throw new Error("[builtin/primitives] engine.surface().transform(...) or setPos(...) is required for setPos()");
        }

        /**
         * Sets transform (best-effort through surface API).
         *
         * @param {object} t {pos?, rot?, scale?}
         * @returns {Primitive}
         */
        setTransform(t) {
            const s = surfaceApi();
            const ref = toSurfaceRef(this._h);

            if (typeof s.transform === "function") {
                s.transform(ref, t);
                return this;
            }

            if (t && t.pos != null && typeof s.setPos === "function") s.setPos(ref, t.pos);
            if (t && t.rot != null && typeof s.setRot === "function") s.setRot(ref, t.rot);
            if (t && t.scale != null && typeof s.setScale === "function") s.setScale(ref, t.scale);

            return this;
        }

        /**
         * Destroys this surface (if surface API supports destroy/remove).
         *
         * @returns {boolean}
         */
        destroy() {
            const s = surfaceApi();
            const ref = toSurfaceRef(this._h);

            if (typeof s.destroy === "function") return !!s.destroy(ref);
            if (typeof s.remove === "function") { s.remove(ref); return true; }

            throw new Error("[builtin/primitives] engine.surface().destroy(...) or remove(...) is required for destroy()");
        }
    }

    function unshadedColor(rgba) {
        const c = Array.isArray(rgba) ? rgba : [1, 1, 1, 1];
        return {
            def: "Common/MatDefs/Misc/Unshaded.j3md",
            params: { Color: c }
        };
    }

    function physics(mass, opts) {
        const o = opts || {};
        const p = { mass: (mass != null ? mass : 0) };

        if (o.enabled != null) p.enabled = !!o.enabled;
        if (o.lockRotation != null) p.lockRotation = !!o.lockRotation;
        if (o.kinematic != null) p.kinematic = !!o.kinematic;

        if (o.friction != null) p.friction = o.friction;
        if (o.restitution != null) p.restitution = o.restitution;
        if (o.damping != null) p.damping = o.damping;

        if (o.collider != null) p.collider = o.collider;
        return p;
    }

    function create(cfg) {
        const c = normalizeCfg(cfg);
        const type = String(c.type || "");
        const h = meshApi().create(c);
        return new Primitive(h, type);
    }

    function box(cfg) {
        const c = withType("box", cfg);
        return new Primitive(meshApi().create(c), "box");
    }

    function cube(cfg) {
        const c = withType("box", cfg);
        return new Primitive(meshApi().create(c), "box");
    }

    function sphere(cfg) {
        const c = withType("sphere", cfg);
        return new Primitive(meshApi().create(c), "sphere");
    }

    function cylinder(cfg) {
        const c = withType("cylinder", cfg);
        return new Primitive(meshApi().create(c), "cylinder");
    }

    function capsule(cfg) {
        const c = withType("capsule", cfg);
        return new Primitive(meshApi().create(c), "capsule");
    }

    function many(list) {
        if (!Array.isArray(list)) throw new Error("[builtin/primitives] many(list): array required");
        const out = new Array(list.length);
        for (let i = 0; i < list.length; i++) {
            const c = normalizeCfg(list[i]);
            out[i] = new Primitive(meshApi().create(c), String(c.type || ""));
        }
        return out;
    }

    return Object.freeze({
        Primitive,
        create,
        box,
        cube,
        sphere,
        cylinder,
        capsule,
        many,
        unshadedColor,
        physics
    });
};