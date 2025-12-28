// FILE: resources/kalitech/builtin/Log.js
// Author: Calista Verner
"use strict";

function safeJson(v) {
    try { return JSON.stringify(v); } catch (_) {}
    try { return String(v); } catch (_) {}
    return "[unserializable]";
}

function normalizeArgs(args) {
    if (!args || args.length === 0) return "";
    if (args.length === 1) {
        const a = args[0];
        if (typeof a === "string") return a;
        if (a instanceof Error) return (a.stack || a.message || String(a));
        if (a && typeof a === "object") return safeJson(a);
        return String(a);
    }
    let out = "";
    for (let i = 0; i < args.length; i++) {
        const a = args[i];
        let s;
        if (typeof a === "string") s = a;
        else if (a instanceof Error) s = (a.stack || a.message || String(a));
        else if (a && typeof a === "object") s = safeJson(a);
        else s = String(a);
        out += (i ? " " : "") + s;
    }
    return out;
}

function makePrefix(scope) {
    const s = String(scope || "").trim();
    return s ? "[" + s + "] " : "";
}

function makeApi(engine /*, K */) {
    const log = (engine && engine.log && typeof engine.log === "function") ? engine.log() : null;

    function has(fn) { return !!(log && typeof log[fn] === "function"); }

    function write(levelFn, scope, args) {
        const msg = makePrefix(scope) + normalizeArgs(args);
        if (!log) return msg;
        try {
            if (has(levelFn)) log[levelFn](msg);
            else if (has("info")) log.info(msg);
        } catch (_) {}
        return msg;
    }

    function trace() { return write("trace", "", arguments); }
    function debug() { return write("debug", "", arguments); }
    function info()  { return write("info",  "", arguments); }
    function warn()  { return write("warn",  "", arguments); }
    function error() { return write("error", "", arguments); }
    function fatal() { return write("fatal", "", arguments); }

    function scoped(scopeName) {
        const scope = String(scopeName || "").trim();

        const s = Object.freeze({
            trace: function () { return write("trace", scope, arguments); },
            debug: function () { return write("debug", scope, arguments); },
            info:  function () { return write("info",  scope, arguments); },
            warn:  function () { return write("warn",  scope, arguments); },
            error: function () { return write("error", scope, arguments); },
            fatal: function () { return write("fatal", scope, arguments); },
            scope: scope
        });

        return s;
    }

    function enabled() { return !!log; }

    return Object.freeze({
        enabled,
        trace,
        debug,
        info,
        warn,
        error,
        fatal,
        child: scoped,
        scope: scoped,
        safeJson
    });
}

function create(engine, K) {
    if (!engine) throw new Error("[LOG] engine is required");
    return makeApi(engine, K);
}

create.META = {
    name: "log",
    globalName: "LOG",
    version: "1.0.0",
    description: "Rootkit wrapper for engine.log() with safe formatting + scoped child loggers",
    engineMin: "0.1.0"
};

module.exports = create;