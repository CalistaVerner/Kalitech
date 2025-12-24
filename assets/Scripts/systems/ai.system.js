// Author: Calista Verner
module.exports.init = function (ctx) {
    const log = ctx.api.log();
    log.info("[ai] init");

    const st = ctx.state();
    st.set("ai:frame", 0);
    st.set("ai:enabled", true);

    ctx.api.events().emit("ai:init", { ok: true });
};

module.exports.update = function (ctx, tpf) {
    const st = ctx.state();
    if (!st.get("ai:enabled")) return;

    const frame = (st.get("ai:frame") | 0) + 1;
    st.set("ai:frame", frame);

    // Здесь позже будет директор мира/квесты/спавнер/поведенческие слои.
};

module.exports.destroy = function (ctx) {
    try {
        const st = ctx.state();
        st.remove("ai:frame");
        st.remove("ai:enabled");
    } catch (_) {}
};