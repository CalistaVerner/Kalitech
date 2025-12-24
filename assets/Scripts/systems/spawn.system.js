// Author: Calista Verner
module.exports.init = function (ctx) {
    const api = ctx.api;
    const log = api.log();
    const events = api.events();

    log.info("[spawn] init");

    // canonical: spawn player
    const playerId = require("../entities/player.entity.js").spawn({ api });
    ctx.state().set("spawn:playerId", playerId);

    events.emit("world:spawned", { playerId });
};

module.exports.destroy = function (ctx) {
    try { ctx.state().remove("spawn:playerId"); } catch (_) {}
};