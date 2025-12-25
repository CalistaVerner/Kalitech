// Centralized player runtime entry (what world should tick)
"use strict";

const Player = require("./Player.js");

exports.meta = {
    id: "kalitech.player.runtime",
    version: "1.0.0",
    apiMin: "0.1.0",
    name: "Player Runtime"
};

exports.create = function () {
    let player = null;

    return {
        init: function (ctx) {
            player = new Player(ctx).init({
                // spawn config can live here centrally
                pos: { x: 0, y: 3, z: 0 }
            });
        },

        update: function (tpf) {
            if (player) player.update(tpf);
        },

        destroy: function () {
            if (player) {
                player.destroy();
                player = null;
            }
        }
    };
};