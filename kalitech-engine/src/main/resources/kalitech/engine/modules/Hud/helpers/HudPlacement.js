// FILE: resources/kalitech/builtin/helpers/hud/HudPlacement.js
"use strict";

const {isObj, num, round} = require("./HudUtil.js");

/**
 * coord:
 *  - "topLeft": x,y from top-left, y grows down
 *  - "bottomLeft": x,y from bottom-left, y grows up (legacy conversion)
 */
function applyCoordY(coord, vpH, y) {
    if (coord === "bottomLeft" && vpH > 0) return vpH - y;
    return y;
}

function parsePlace(cfg) {
    const p = cfg && cfg.place;
    if (!isObj(p)) return null;
    const a = String(p.anchor || "tl");
    const x = num(p.x != null ? p.x : p.mx, 0);
    const y = num(p.y != null ? p.y : p.my, 0);
    return {a, x, y};
}

// anchors for rect (panel)
function placeRect(containerW, containerH, w, h, place) {
    const a = place ? place.a : "tl";
    const ox = place ? place.x : 0;
    const oy = place ? place.y : 0;

    let x = 0, y = 0;

    switch (a) {
        case "tr":
            x = containerW - w;
            y = 0;
            break;
        case "bl":
            x = 0;
            y = containerH - h;
            break;
        case "br":
            x = containerW - w;
            y = containerH - h;
            break;

        case "t":
            x = (containerW - w) * 0.5;
            y = 0;
            break;
        case "b":
            x = (containerW - w) * 0.5;
            y = containerH - h;
            break;
        case "l":
            x = 0;
            y = (containerH - h) * 0.5;
            break;
        case "r":
            x = containerW - w;
            y = (containerH - h) * 0.5;
            break;
        case "c":
            x = (containerW - w) * 0.5;
            y = (containerH - h) * 0.5;
            break;

        case "tl":
        default:
            x = 0;
            y = 0;
            break;
    }

    return {x: round(x + ox), y: round(y + oy)};
}

// anchors for point (label). We DO NOT know label size reliably.
function placePoint(containerW, containerH, place) {
    const a = place ? place.a : "tl";
    const ox = place ? place.x : 0;
    const oy = place ? place.y : 0;

    let x = 0, y = 0;

    switch (a) {
        case "tr":
            x = containerW;
            y = 0;
            break;
        case "bl":
            x = 0;
            y = containerH;
            break;
        case "br":
            x = containerW;
            y = containerH;
            break;

        case "t":
            x = containerW * 0.5;
            y = 0;
            break;
        case "b":
            x = containerW * 0.5;
            y = containerH;
            break;
        case "l":
            x = 0;
            y = containerH * 0.5;
            break;
        case "r":
            x = containerW;
            y = containerH * 0.5;
            break;
        case "c":
            x = containerW * 0.5;
            y = containerH * 0.5;
            break;

        case "tl":
        default:
            x = 0;
            y = 0;
            break;
    }

    return {x: round(x + ox), y: round(y + oy)};
}

module.exports = {
    applyCoordY,
    parsePlace,
    placeRect,
    placePoint
};