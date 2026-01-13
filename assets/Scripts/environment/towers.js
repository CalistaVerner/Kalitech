"use strict";

function isFn(f) {
    return typeof f === "function";
}

function resolveEngineApi(ctx) {
    if (!ctx || !ctx.engine || !isFn(ctx.engine.api)) {
        throw new Error("[towers] ctx.engine.api() is required");
    }
    const api = ctx.engine.api();
    if (!api) throw new Error("[towers] ctx.engine.api() returned null");
    return api;
}

/**
 * randNum(min, max, opts?)
 */
function randNum(min, max, opts) {
    if (!Number.isFinite(min) || !Number.isFinite(max)) {
        throw new Error("randNum(min,max): min/max must be finite numbers");
    }
    if (max < min) {
        const t = min;
        min = max;
        max = t;
    }

    opts = opts || Object.create(null);
    const rng = (typeof opts.rng === "function") ? opts.rng : Math.random;
    const isInt = !!opts.int;
    const step = Number(opts.step) || 0;

    let v = rng() * (max - min) + min;
    if (step > 0) v = Math.round(v / step) * step;

    if (isInt) {
        v = Math.floor(v);
        if (v > max) v = max;
        if (v < min) v = min;
    }
    return v;
}

function buildEm(engine, state) {
    if (!state.created) state.created = [];

    const TOTAL = 70;
    const TOWERS = 6;
    const BOXES_PER_TOWER = Math.floor(TOTAL / TOWERS);

    const BASE_X = 120;
    const BASE_Z = -300;
    const TOWER_SPACING = 8;

    const density = 4.5;
    const weightBias = 0.6;

    let boxId = 0;

    for (let t = 0; t < TOWERS; t++) {
        const x = BASE_X + t * TOWER_SPACING;
        const z = BASE_Z;

        const sizes = [];
        for (let i = 0; i < BOXES_PER_TOWER; i++) {
            sizes.push(randNum(1, 5));
        }
        sizes.sort((a, b) => b - a);

        let y = 0;

        for (let i = 0; i < sizes.length; i++) {
            const size = sizes[i];

            y += size / 2;

            const mass =
                density *
                Math.pow(size, 3) *
                (1 + size * weightBias);


            const h = ENGINE.mesh
                .box$()
                .size(size)
                .name("box-" + (boxId++))
                .pos(x, y, z)
                .material(MAT.getMaterial("box"))
                .physics(mass, {lockRotation: false})
                .create();

            // store handle/uuid for later destroy
            state.created.push(h);

            y += size / 2;
        }
    }
}

module.exports = {
    _state: null,

    init(ctx) {
        const E = resolveEngineApi(ctx);

        this._state = {created: []};
        buildEm(E, this._state);
    },

    update(ctx, tpf) {
    },

    destroy() {
    }
};