// FILE: Scripts/player/PlayerCamera.js
"use strict";

const CameraOrchestrator = require("../Camera/CameraOrchestrator.js");

class PlayerCamera {
    constructor(player) {
        this.player = player;
        this.camera = new CameraOrchestrator(player);
    }

    // --- COMPAT: старый код игрока ожидает camera.attach(bodyId)
    attach(bodyId) {
        this.camera.attachTo(bodyId);
    }

    // Дополнительные помощники (для скриптов)
    setType(type) {
        this.camera.setType(type); // Исправляем вызов setType для правильного контекста
    }

    toggle() {
        if (typeof this.camera.toggle === "function") {
            this.camera.toggle();
        }
    }

    update(frame) {
        if (!frame) return;

        // Поддержка старого кода: автоматически привязываем камеру, если bodyId доступен
        const bodyId = frame.ids ? (frame.ids.bodyId | 0) : 0;
        if (bodyId) this.attach(bodyId); // Привязка камеры

        this.camera.update(frame.dt, frame.snap); // Обновление камеры
    }

    destroy() {
        try {
            this.camera.attachTo(0); // Отключение привязки камеры
            this.camera.setType("third"); // Устанавливаем тип камеры на третье лицо по умолчанию
        } catch (_) {}
    }
}

module.exports = PlayerCamera;
