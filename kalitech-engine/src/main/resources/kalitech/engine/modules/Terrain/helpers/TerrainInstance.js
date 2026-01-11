// FILE: resources/kalitech/builtin/terrain/TerrainInstance.js
"use strict";

class TerrainInstance {
    constructor(api, surface) {
        if (!api) throw new Error("[TERR] api is required");
        if (!surface) throw new Error("[TERR] surface handle is required");

        this._api = api;
        this.surface = surface;

        Object.freeze(this);
    }

    // ------------------------------------------------------------
    // Query
    // ------------------------------------------------------------

    heightAt(x, z, world = true) {
        return this._api.heightAt(this.surface, x, z, world);
    }

    normalAt(x, z, world = true) {
        return this._api.normalAt(this.surface, x, z, world);
    }

    // ------------------------------------------------------------
    // Edit
    // ------------------------------------------------------------

    setHeight(x, z, h, world = true) {
        return this._api.setHeight(this.surface, x, z, h, world);
    }

    adjustHeight(x, z, delta, world = true) {
        return this._api.adjustHeight(this.surface, x, z, delta, world);
    }

    setHeightmap(heights, size, rebuild = true) {
        return this._api.setHeightmap(this.surface, heights, size, rebuild);
    }

    heightmap() {
        return this._api.heightmap(this.surface);
    }

    rebuild() {
        return this._api.rebuild(this.surface);
    }

    // ------------------------------------------------------------
    // Visual / misc
    // ------------------------------------------------------------

    material(mat) {
        return this._api.material(this.surface, mat);
    }

    uv(cfg) {
        return this._api.uv(this.surface, cfg);
    }

    lod(cfg) {
        return this._api.lod(this.surface, cfg);
    }

    scale(xz, cfg) {
        return this._api.scale(this.surface, xz, cfg);
    }

    attach(entityId) {
        return this._api.attach(this.surface, entityId);
    }

    detach() {
        return this._api.detach(this.surface);
    }
}

module.exports = {TerrainInstance};