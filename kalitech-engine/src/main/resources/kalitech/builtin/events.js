// Author: Calista Verner
"use strict";

function once(fn) {
    let done = false;
    return function (...args) {
        if (done) return;
        done = true;
        return fn.apply(this, args);
    };
}

function debounce(fn, ms) {
    let t = null;
    return function (...args) {
        if (t) clearTimeout(t);
        t = setTimeout(() => { t = null; fn.apply(this, args); }, ms | 0);
    };
}

module.exports = { once, debounce };