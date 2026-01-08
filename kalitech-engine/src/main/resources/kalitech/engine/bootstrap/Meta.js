// FILE: resources/kalitech/engine/bootstrap/Meta.js
"use strict";

const {isObj} = require("./Util.js");
const {tryReadKaliModFromFsNearModule, tryReadKaliModFromAssets} = require("./Assets.js");

function normalizeMeta(exp, fallbackName, moduleId, engine) {
    const fromFs = moduleId ? tryReadKaliModFromFsNearModule(moduleId) : null;
    const fromAssets = (!fromFs && engine && moduleId) ? tryReadKaliModFromAssets(engine, moduleId) : null;
    const fromExport = (exp && exp.META && isObj(exp.META)) ? exp.META : null;

    const src = fromFs || fromAssets || fromExport;

    const name = (src && src.name) ? String(src.name) : String(fallbackName || "");
    const globalName = (src && src.globalName) ? String(src.globalName) : "";
    const version = (src && src.version) ? String(src.version) : "0.0.0";
    const description = (src && src.description) ? String(src.description) : "";
    const engineMin = (src && src.engineMin) ? String(src.engineMin) : "";
    return {name, globalName, version, description, engineMin};
}

module.exports = {normalizeMeta};