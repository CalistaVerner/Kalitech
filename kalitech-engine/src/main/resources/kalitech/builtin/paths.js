// Author: Calista Verner
"use strict";

function resolve(parent, request) {
    const r = globalThis.__resolveId;
    if (typeof r !== "function") return null;
    return r(parent || "", request || "");
}

module.exports = { resolve };