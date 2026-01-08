// FILE: resources/kalitech/engine/bootstrap/Root.js
"use strict";

const ROOT_KEY = "__kalitech";

function getRoot() {
    if (!globalThis[ROOT_KEY]) globalThis[ROOT_KEY] = Object.create(null);
    return globalThis[ROOT_KEY];
}

function ensureRootState(K) {
    if (!K.modules) K.modules = Object.create(null);
    if (!K.instances) K.instances = Object.create(null);
    if (!K.meta) K.meta = Object.create(null);
    if (!K.instancesMeta) K.instancesMeta = Object.create(null);
    if (!K.moduleIds) K.moduleIds = Object.create(null);

    if (!K._engine) K._engine = null;
    if (!K._engineAttached) K._engineAttached = false;

    if (!K._deferred) K._deferred = [];
    if (!K._once) K._once = Object.create(null);

    if (!K.config) K.config = Object.create(null);

    if (!K.dataConfig) K.dataConfig = Object.create(null);
    if (!K.dataConfigApi) K.dataConfigApi = null;

    // NEW: controllers service
    if (!K.controllers) K.controllers = Object.create(null); // name -> { name, defs }
    if (!K.controllersApi) K.controllersApi = null;
    if (!K.controllersRegs) K.controllersRegs = [];

    return K;
}

module.exports = {getRoot, ensureRootState};