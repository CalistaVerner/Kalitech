(function () {
    const $ = (id) => document.getElementById(id);

    const logEl = $("log");
    function log(line) {
        const t = new Date().toLocaleTimeString();
        const div = document.createElement("div");
        div.innerHTML = `<b>[${t}]</b> ${line}`;
        logEl.appendChild(div);
        logEl.scrollTop = logEl.scrollHeight;
    }

    function setConn(state, color) {
        const el = $("conn");
        el.textContent = state;
        el.style.color = color || "";
    }

    // clock
    setInterval(() => {
        $("time").textContent = new Date().toLocaleTimeString();
    }, 250);

    // Buttons
    $("btnPing").addEventListener("click", () => {
        // We cannot call engine directly from here (yet).
        // We send a message to engine, engine should respond back.
        postToEngine({ type: "ui.ping", payload: { t: Date.now() } });
        log("Sent <span class='mono'>ui.ping</span> to engine");
    });

    $("btnToast").addEventListener("click", () => {
        postToEngine({ type: "ui.toast", payload: { text: "Hello from HUD", t: Date.now() } });
        log("Sent <span class='mono'>ui.toast</span> to engine");
    });

    // --- Bridge (engine -> UI) ---
    // You will wire this from JCEF by executing JS like:
    //   window.__kalitech_onMessage({type:"...", payload:{...}})
    window.__kalitech_onMessage = function (msg) {
        try {
            $("lastMsg").textContent = JSON.stringify(msg);
            if (msg?.type === "engine.ready") {
                $("worldName").textContent = msg.payload?.world ?? "main";
                $("worldMode").textContent = msg.payload?.mode ?? "game";
                setConn("UI: connected", "rgba(70,200,120,.95)");
                log("Engine says: ready");
            } else if (msg?.type === "engine.fps") {
                $("fps").textContent = String(msg.payload?.fps ?? "--");
            } else if (msg?.type === "engine.pong") {
                setConn("UI: pong", "rgba(255,200,60,.95)");
                log("Engine pong: " + (msg.payload?.ok ? "ok" : "—"));
                setTimeout(() => setConn("UI: connected", "rgba(70,200,120,.95)"), 650);
            } else if (msg?.type === "engine.toast") {
                log("Toast: " + String(msg.payload?.text ?? ""));
            } else {
                log("Message: <span class='mono'>" + escapeHtml(msg?.type ?? "unknown") + "</span>");
            }
        } catch (e) {
            // ignore
        }
    };

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, (c) => ({
            "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;"
        }[c]));
    }

    // --- Bridge (UI -> engine) ---
    // For now: we just encode messages into document.title as a quick hack,
    // but ideally you will wire CefMessageRouter / query handler.
    function postToEngine(msg) {
        try {
            // lightweight hack channel (works even before message router exists)
            document.title = "KALITECH_UI_MSG:" + JSON.stringify(msg);
        } catch (e) {}
    }

    // bootstrap
    setConn("UI: loaded", "rgba(255,200,60,.95)");
    log("HUD loaded");
})();