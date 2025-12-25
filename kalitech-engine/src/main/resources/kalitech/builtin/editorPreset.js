// FILE: resources/kalitech/builtin/editorPreset.js
// Author: Calista Verner
"use strict";

// ВАЖНО: это не системы, а ДАННЫЕ (дескрипторы систем).
// Пользовательские Scripts/world не должны их “переопределять” удалением.

const editorSystems = Object.freeze([
    Object.freeze({
        id: "jsSystem",
        order: 12,
        stableId: "sys.editor.grid",
        config: Object.freeze({
            module: "@core/editor/editor.grid.system",
            enabled: true,

            // “бесконечность” делаем логикой tiled/recenter (позже),
            // пока даём большой extent, остальное — конфиг
            halfExtent: 5000,
            step: 1,
            majorStep: 10,
            y: 0.02,
            opacity: 0.85,

            // тёмные линии (у тебя в world сейчас ошибочные значения: 0.5/0.9 — это СЛИШКОМ светло)
            minorColor: { r: 0.05, g: 0.06, b: 0.08 },
            majorColor: { r: 0.09, g: 0.11, b: 0.16 },

            // толщина
            minorThickness: 0.02,
            majorThickness: 0.06
        })
    }),

    Object.freeze({
        id: "jsSystem",
        order: 13,
        stableId: "sys.editor.pick",
        config: Object.freeze({
            module: "@core/editor/editor.pick.system",
            enabled: true,
            mode: "click",
            button: "LMB",
            maxDistance: 10000,
            debugLog: false
        })
    })
]);

module.exports = Object.freeze({
    editorSystems
});
