// FILE: resources/kalitech/builtin/helpers/hud/HudPlacement.js
"use strict";

const {isObj, num} = require("./HudUtil.js");

/**
 * Placement:
 *  place: { anchor:"tl|tr|bl|br|c|tc|bc|lc|rc", x,y }
 *  anchor defaults to "tl"
 *
 * Contract:
 * - Layer stores script-space coords (top-left by default)
 * - applyCoordY can flip for bottom-left legacy modes
 */

function parsePlace(p) {
    const c = isObj(p) ? p : {};
    const anchor = String(c.anchor || "tl");
    const x = num(c.x, 0);
    const y = num(c.y, 0);
    return {anchor, x, y};
}

function placeRect(vw, vh, w, h, place) {
    const p = place || {anchor: "tl", x: 0, y: 0};
    const a = p.anchor || "tl";

    let x = p.x;
    let y = p.y;

    if (a === "tr") x = vw - w + p.x;
    else if (a === "bl") y = vh - h + p.y;
    else if (a === "br") {
        x = vw - w + p.x;
        y = vh - h + p.y;
    } else if (a === "c") {
        x = (vw - w) * 0.5 + p.x;
        y = (vh - h) * 0.5 + p.y;
    } else if (a === "tc") {
        x = (vw - w) * 0.5 + p.x;
    } else if (a === "bc") {
        x = (vw - w) * 0.5 + p.x;
        y = vh - h + p.y;
    } else if (a === "lc") {
        y = (vh - h) * 0.5 + p.y;
    } else if (a === "rc") {
        x = vw - w + p.x;
        y = (vh - h) * 0.5 + p.y;
    }

    return {x, y};
}

function placePoint(vw, vh, place) {
    const p = place || {anchor: "tl", x: 0, y: 0};
    const a = p.anchor || "tl";

    let x = p.x;
    let y = p.y;

    if (a === "tr") x = vw + p.x;
    else if (a === "bl") y = vh + p.y;
    else if (a === "br") {
        x = vw + p.x;
        y = vh + p.y;
    } else if (a === "c") {
        x = vw * 0.5 + p.x;
        y = vh * 0.5 + p.y;
    } else if (a === "tc") {
        x = vw * 0.5 + p.x;
    } else if (a === "bc") {
        x = vw * 0.5 + p.x;
        y = vh + p.y;
    } else if (a === "lc") {
        y = vh * 0.5 + p.y;
    } else if (a === "rc") {
        x = vw + p.x;
        y = vh * 0.5 + p.y;
    }

    return {x, y};
}

function applyCoordY(coord, vh, y) {
    // coord: "topLeft" (default) or "bottomLeft"
    if (coord === "bottomLeft") return vh - y;
    return y;
}

module.exports = {parsePlace, placeRect, placePoint, applyCoordY};