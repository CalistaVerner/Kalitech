let t = 0;

({
    init(kalitech) {
        kalitech.info("JS init (world system)");

        kalitech.on("engine.ready", (payload) => {
            kalitech.info("engine.ready => " + payload);
        });

        kalitech.spawnBox("playerCube", 1, 1, 1);
        kalitech.setLocalTranslation("playerCube", 0, 0.5, 0);
    },

    update(kalitech, tpf) {
        t += tpf;
        kalitech.rotateY("playerCube", tpf);

        if (t > 2) {
            t = 0;
            kalitech.emit("player.jump", { power: 10, time: Date.now() });
        }
    },

    destroy(kalitech) {
        kalitech.info("JS destroy");
    }
})
