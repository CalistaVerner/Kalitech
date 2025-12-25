// Author: Calista Verner
"use strict";

function fail(message, meta) {
    const err = new Error(message || "Assertion failed");
    if (meta !== undefined) err.meta = meta;
    throw err;
}

function ok(cond, message, meta) {
    if (!cond) fail(message, meta);
}

function equal(a, b, message) {
    if (a !== b) fail(message || `Expected ${a} === ${b}`, { a, b });
}

module.exports = { ok, equal, fail };
