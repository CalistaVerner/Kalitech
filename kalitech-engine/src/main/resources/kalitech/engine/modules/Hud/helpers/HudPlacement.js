// FILE: resources/kalitech/builtin/helpers/hud/HudPlacement.js
"use strict";

const {isObj, num} = require("./HudUtil.js");

const Anchor = Object.freeze({
    TL: 0,
    TR: 1,
    BL: 2,
    BR: 3,
    C: 4,
    TC: 5,
    BC: 6,
    LC: 7,
    RC: 8
});

const AnchorMap = Object.freeze({
    tl: Anchor.TL,
    tr: Anchor.TR,
    bl: Anchor.BL,
    br: Anchor.BR,
    c: Anchor.C,
    tc: Anchor.TC,
    bc: Anchor.BC,
    lc: Anchor.LC,
    rc: Anchor.RC
});

function toAnchor(v) {
    if (typeof v === "number" && Number.isFinite(v)) return v | 0;
    const s = String(v || "tl").trim().toLowerCase();
    const a = AnchorMap[s];
    return (a === undefined) ? Anchor.TL : a;
}

/**
 * Placement:
 *  place: { anchor:"tl|tr|bl|br|c|tc|bc|lc|rc" | number, x,y }
 *  anchor defaults to "tl"
 *
 * Contract:
 * - Layer stores script-space coords (top-left by default)
 * - applyCoordY can flip for bottom-left legacy modes
 */
function parsePlace(p) {
    const c = isObj(p) ? p : {};
    const anchor = toAnchor(c.anchor);
    const x = num(c.x, 0);
    const y = num(c.y, 0);
    return {anchor, x, y};
}

function placeRect(vw, vh, w, h, place) {
    const p = place || {anchor: Anchor.TL, x: 0, y: 0};
    const a = toAnchor(p.anchor);

    let x = p.x;
    let y = p.y;

    switch (a) {
        case Anchor.TR:
            x = vw - w + p.x;
            break;
        case Anchor.BL:
            y = vh - h + p.y;
            break;
        case Anchor.BR:
            x = vw - w + p.x;
            y = vh - h + p.y;
            break;
        case Anchor.C:
            x = (vw - w) * 0.5 + p.x;
            y = (vh - h) * 0.5 + p.y;
            break;
        case Anchor.TC:
            x = (vw - w) * 0.5 + p.x;
            break;
        case Anchor.BC:
            x = (vw - w) * 0.5 + p.x;
            y = vh - h + p.y;
            break;
        case Anchor.LC:
            y = (vh - h) * 0.5 + p.y;
            break;
        case Anchor.RC:
            x = vw - w + p.x;
            y = (vh - h) * 0.5 + p.y;
            break;
        default:
            break;
    }

    return {x, y};
}

function placePoint(vw, vh, place) {
    const p = place || {anchor: Anchor.TL, x: 0, y: 0};
    const a = toAnchor(p.anchor);

    let x = p.x;
    let y = p.y;

    switch (a) {
        case Anchor.TR:
            x = vw + p.x;
            break;
        case Anchor.BL:
            y = vh + p.y;
            break;
        case Anchor.BR:
            x = vw + p.x;
            y = vh + p.y;
            break;
        case Anchor.C:
            x = vw * 0.5 + p.x;
            y = vh * 0.5 + p.y;
            break;
        case Anchor.TC:
            x = vw * 0.5 + p.x;
            break;
        case Anchor.BC:
            x = vw * 0.5 + p.x;
            y = vh + p.y;
            break;
        case Anchor.LC:
            y = vh * 0.5 + p.y;
            break;
        case Anchor.RC:
            x = vw + p.x;
            y = vh * 0.5 + p.y;
            break;
        default:
            break;
    }

    return {x, y};
}

function applyCoordY(coord, vh, y) {
    // coord: "topLeft" (default) or "bottomLeft"
    if (coord === "bottomLeft") return vh - y;
    return y;
}

module.exports = {Anchor, parsePlace, placeRect, placePoint, applyCoordY};