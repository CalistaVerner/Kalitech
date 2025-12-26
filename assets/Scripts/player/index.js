// FILE: Scripts/player/index.js
// Author: Calista Verner
"use strict";

const Player = require("./Player.js");

let P = null;

module.exports.init = function (ctx) {
    // ctx даёт state(), поэтому Player ожидает ctx
    P = new Player(ctx).init({
        // можно тут держать дефолты спавна
        pos: {x: 0, y: 3, z: 0}
    });

    // опционально: сохранить для дебага
    try {
        ctx.state().set("player:instance", true);
    } catch (_) {
    }
};

module.exports.update = function (ctx, tpf) {
    if (!P) return;
    P.update(tpf);
};

module.exports.destroy = function (ctx) {
    if (!P) return;
    try {
        P.destroy();
    } catch (_) {
    }
    P = null;
};
