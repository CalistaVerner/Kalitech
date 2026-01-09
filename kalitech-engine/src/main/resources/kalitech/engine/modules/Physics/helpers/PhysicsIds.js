"use strict";

function bodyIdOf(handleOrId) {
    if (typeof handleOrId === "number") return handleOrId | 0;
    if (!handleOrId) return 0;
    if (typeof handleOrId.id === "function") return handleOrId.id() | 0;
    if (typeof handleOrId.id === "number") return handleOrId.id | 0;
    if (typeof handleOrId.bodyId === "number") return handleOrId.bodyId | 0;
    return 0;
}

function surfaceIdOf(handleOrId) {
    if (typeof handleOrId === "number") return handleOrId | 0;
    if (!handleOrId) return 0;
    if (typeof handleOrId.id === "function") return handleOrId.id() | 0;
    if (typeof handleOrId.id === "number") return handleOrId.id | 0;
    if (typeof handleOrId.surfaceId === "number") return handleOrId.surfaceId | 0;
    return 0;
}

module.exports = Object.freeze({
    bodyIdOf,
    surfaceIdOf,
});