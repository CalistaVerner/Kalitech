// FILE: resources/kalitech/builtin/Log.js
// Author: Calista Verner
"use strict";

function safeJson(v) {
    try {
        return JSON.stringify(v);
    } catch (_) {
    }
    try {
        return String(v);
    } catch (_) {
    }
    return "[unserializable]";
}

function isThrowableLike(v) {
    if (!v) return false;

    try {
        if (v instanceof Error) return true;
    } catch (_) {
    }

    const t = typeof v;
    if (t !== "object" && t !== "function") return false;

    try {
        const stack = v.stack;
        if (typeof stack === "string" && stack.length > 0) return true;
    } catch (_) {
    }

    try {
        const name = v.name;
        const msg = v.message;
        if (typeof name === "string" && name.length > 0 && typeof msg === "string") return true;
    } catch (_) {
    }

    // Host exceptions (PolyglotException/Throwable) sometimes expose Java-ish surface
    try {
        if (typeof v.getClass === "function") return true;
    } catch (_) {
    }

    return false;
}

function throwableToText(e) {
    if (!e) return "null";

    try {
        if (e instanceof Error) return (e.stack || e.message || String(e));
    } catch (_) {
    }

    try {
        if (typeof e.stack === "string" && e.stack) return e.stack;
    } catch (_) {
    }

    try {
        if (typeof e.message === "string" && e.message) return e.message;
    } catch (_) {
    }

    try {
        return String(e);
    } catch (_) {
    }

    return "[unserializable-exception]";
}

function valueToText(v) {
    if (v == null) return "null";

    const t = typeof v;
    if (t === "string") return v;
    if (t === "number" || t === "boolean" || t === "bigint") return String(v);

    if (isThrowableLike(v)) return throwableToText(v);

    if (t === "object") return safeJson(v);

    try {
        return String(v);
    } catch (_) {
    }
    return "[unserializable]";
}

function joinArgs(args, from, toExclusive) {
    let out = "";
    for (let i = from; i < toExclusive; i++) {
        out += (i > from ? " " : "") + valueToText(args[i]);
    }
    return out;
}

function makePrefix(scope) {
    const s = String(scope || "").trim();
    return s ? "[" + s + "] " : "";
}

function makeApi(engine /*, K */) {
    const log = (engine && engine.log && typeof engine.log === "function") ? engine.log() : null;

    function has(fn) {
        return !!(log && typeof log[fn] === "function");
    }

    function call1(levelFn, msg) {
        if (!log) return;
        if (has(levelFn)) log[levelFn](msg);
        else if (has("info")) log.info(msg);
    }

    /**
     * Important: we NEVER call log[levelFn](msg, err) here.
     * Java side may expose only 1-arity overload and Graal interop may throw uncatchable arity errors.
     */
    function write(levelFn, scope, args) {
        const prefix = makePrefix(scope);

        if (!args || args.length === 0) {
            const msg0 = prefix;
            try {
                call1(levelFn, msg0);
            } catch (_) {
            }
            return msg0;
        }

        // If last arg is throwable-like, append it on a new line.
        if (args.length >= 2 && isThrowableLike(args[args.length - 1])) {
            const head = prefix + joinArgs(args, 0, args.length - 1);
            const err = args[args.length - 1];
            const msg = head + "\n" + throwableToText(err);

            try {
                call1(levelFn, msg);
            } catch (_) {
            }
            return msg;
        }

        const msg = prefix + joinArgs(args, 0, args.length);
        try {
            call1(levelFn, msg);
        } catch (_) {
        }
        return msg;
    }

    function trace() {
        return write("trace", "", arguments);
    }

    function debug() {
        return write("debug", "", arguments);
    }

    function info() {
        return write("info", "", arguments);
    }

    function warn() {
        return write("warn", "", arguments);
    }

    function error() {
        return write("error", "", arguments);
    }

    function fatal() {
        return write("fatal", "", arguments);
    }

    function scoped(scopeName) {
        const scope = String(scopeName || "").trim();
        return Object.freeze({
            trace: function () {
                return write("trace", scope, arguments);
            },
            debug: function () {
                return write("debug", scope, arguments);
            },
            info: function () {
                return write("info", scope, arguments);
            },
            warn: function () {
                return write("warn", scope, arguments);
            },
            error: function () {
                return write("error", scope, arguments);
            },
            fatal: function () {
                return write("fatal", scope, arguments);
            },
            scope: scope
        });
    }

    function enabled() {
        return !!log;
    }

    return Object.freeze({
        enabled: enabled,
        trace: trace,
        debug: debug,
        info: info,
        warn: warn,
        error: error,
        fatal: fatal,
        child: scoped,
        scope: scoped,
        safeJson: safeJson
    });
}

function create(engine, K) {
    if (!engine) throw new Error("[LOG] engine is required");
    return makeApi(engine, K);
}

create.META = {
    moduleId: "log",
    globalName: "LOG",
    version: "1.2.0",
    description: "Rootkit wrapper for engine.log() with safe formatting + scoped child loggers",
    engineMin: "0.1.0"
};

module.exports = create;