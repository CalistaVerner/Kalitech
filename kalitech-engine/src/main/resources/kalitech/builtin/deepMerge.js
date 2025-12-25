// Author: Calista Verner
"use strict";

function isObj(x) { return x && typeof x === "object" && !Array.isArray(x); }

function deepMerge(target, ...sources) {
    const out = isObj(target) ? { ...target } : {};
    for (const src of sources) {
        if (!isObj(src)) continue;
        for (const k of Object.keys(src)) {
            const sv = src[k];
            const tv = out[k];
            if (isObj(tv) && isObj(sv)) out[k] = deepMerge(tv, sv);
            else out[k] = sv;
        }
    }
    return out;
}

module.exports = deepMerge;