let angle = 0;

({
    init(kalitech) {
        kalitech.info("JS init");

        kalitech.on("engine.ready", (payload) => {
            kalitech.info("Got engine.ready: " + payload);
        });

        kalitech.on("player.jump", (payload) => {
            kalitech.info("player.jump payload=" + payload);
        });

        kalitech.spawnBox("playerCube", 1, 1, 1);
        kalitech.setLocalTranslation("playerCube", 0, 0.5, 0);
    },

    update(kalitech, tpf) {
        angle += tpf;
        kalitech.rotateY("playerCube", tpf);

        // пример: раз в ~2 секунды шлём событие “jump”
        if (angle > 2.0) {
            angle = 0;
            kalitech.emit("player.jump", { power: 10, time: Date.now() });
        }
    },

    destroy(kalitech) {
        kalitech.info("JS destroy (hot reload / exit)");
    }
})