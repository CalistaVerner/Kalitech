const META = {
    name: "Input",
    globalName: "INP",
    version: "1.0.0",
    engineMin: "0.0.0",
};

function _isObj(x) { return x && typeof x === "object"; }
function _num(x, def = 0) { x = Number(x); return Number.isFinite(x) ? x : def; }
function _bool(x) { return !!x; }

function _keyNameNormalize(name) {
    if (name == null) return "";
    return String(name).trim();
}

function _arrToSet(arr) {
    try {
        const out = new Set();
        if (!arr) return out;
        const n = arr.length >>> 0;
        for (let i = 0; i < n; i++) out.add(arr[i]);
        return out;
    } catch {
        return new Set();
    }
}

module.exports = function InputModule(engine, K) {
    const input = engine && typeof engine.input === "function" ? engine.input() : null;
    if (!input) throw new Error("Input module: engine.input() is required");

    let _lastSnap = null;
    let _lastJustPressed = new Set();
    let _lastJustReleased = new Set();

    function _vec2(x, y) { return { x: _num(x), y: _num(y) }; }
    function _delta2(dx, dy) { return { dx: _num(dx), dy: _num(dy) }; }

    function _readSnapshot() {
        const snap = input.consumeSnapshot();
        _lastSnap = snap || null;

        try {
            const jp = snap && (snap.justPressed || snap.justPressedKeyCodes || snap.justPressedCodes);
            const jr = snap && (snap.justReleased || snap.justReleasedKeyCodes || snap.justReleasedCodes);
            _lastJustPressed = _arrToSet(jp);
            _lastJustReleased = _arrToSet(jr);
        } catch {
            _lastJustPressed = new Set();
            _lastJustReleased = new Set();
        }

        return _lastSnap;
    }

    const api = {
        META,

        consumeSnapshot() { return _readSnapshot(); },

        keyDown(key) {
            if (typeof key === "string") return !!input.keyDown(_keyNameNormalize(key));
            return !!input.keyDown(_num(key, -1) | 0);
        },

        keyCode(name) {
            return input.keyCode(_keyNameNormalize(name)) | 0;
        },

        mouseX() { return _num(input.mouseX()); },
        mouseY() { return _num(input.mouseY()); },

        cursorPosition() {
            const v = input.cursorPosition();
            if (_isObj(v) && "x" in v && "y" in v) return _vec2(v.x, v.y);
            return _vec2(api.mouseX(), api.mouseY());
        },

        mouseDx() { return _num(input.mouseDx()); },
        mouseDy() { return _num(input.mouseDy()); },

        mouseDX() { return _num(input.mouseDX()); },
        mouseDY() { return _num(input.mouseDY()); },

        mouseDelta() {
            const d = input.mouseDelta();
            if (_isObj(d) && ("dx" in d || "x" in d)) return _delta2(d.dx ?? d.x, d.dy ?? d.y);
            return _delta2(api.mouseDx(), api.mouseDy());
        },

        consumeMouseDelta() {
            const d = input.consumeMouseDelta();
            if (_isObj(d) && ("dx" in d || "x" in d)) return _delta2(d.dx ?? d.x, d.dy ?? d.y);
            return _delta2(0, 0);
        },

        wheelDelta() { return _num(input.wheelDelta()); },
        consumeWheelDelta() { return _num(input.consumeWheelDelta()); },

        mouseDown(button) { return !!input.mouseDown(_num(button, 0) | 0); },

        cursorVisible(v) {
            if (arguments.length === 0) return !!input.cursorVisible();
            input.cursorVisible(_bool(v));
            return api;
        },

        grabMouse(grab) {
            input.grabMouse(_bool(grab));
            return api;
        },

        grabbed() { return !!input.grabbed(); },

        endFrame() {
            input.endFrame();
            return api;
        },

        beginFrame() {
            return _readSnapshot();
        },

        poll() {
            const snap = _readSnapshot();
            input.endFrame();
            return snap;
        },

        lastSnapshot() { return _lastSnap; },

        pressed(key) {
            const code = (typeof key === "string") ? api.keyCode(key) : (_num(key, -1) | 0);
            return _lastJustPressed.has(code);
        },

        released(key) {
            const code = (typeof key === "string") ? api.keyCode(key) : (_num(key, -1) | 0);
            return _lastJustReleased.has(code);
        },

        mousePos() { return api.cursorPosition(); },
        delta() { return api.mouseDelta(); },
    };

    return api;
};

module.exports.META = META;
