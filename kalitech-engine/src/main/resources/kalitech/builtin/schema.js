// Author: Calista Verner
"use strict";

function typeOf(v) {
    if (v === null) return "null";
    if (Array.isArray(v)) return "array";
    return typeof v;
}

// schema example:
// { enabled:"boolean", maxDistance:"number", mode:["click","hover"], nested:{ a:"string" } }
function validate(schema, obj, path = "") {
    const errors = [];

    function err(msg) { errors.push(path ? `${path}: ${msg}` : msg); }

    if (!schema || typeof schema !== "object") {
        return { ok: true, errors: [] };
    }

    for (const key of Object.keys(schema)) {
        const rule = schema[key];
        const val = obj ? obj[key] : undefined;
        const p = path ? `${path}.${key}` : key;

        if (typeof rule === "string") {
            if (typeOf(val) !== rule) errors.push(`${p}: expected ${rule}, got ${typeOf(val)}`);
            continue;
        }

        if (Array.isArray(rule)) {
            if (!rule.includes(val)) errors.push(`${p}: expected one of ${rule.join(", ")}, got ${String(val)}`);
            continue;
        }

        if (rule && typeof rule === "object") {
            if (typeOf(val) !== "object") errors.push(`${p}: expected object, got ${typeOf(val)}`);
            else {
                const sub = validate(rule, val, p);
                errors.push(...sub.errors);
            }
        }
    }

    return { ok: errors.length === 0, errors };
}

module.exports = { validate };