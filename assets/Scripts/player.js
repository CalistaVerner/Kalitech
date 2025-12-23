({
    init(k) {
        k.info("player init, id=" + k.id());
        k.setPos(0, 0.5, 0);
        k.emit("player.spawned", { id: k.id() });
    },

    update(k, tpf) {
        // вращаемся “внутри компонента”, но записываем в Transform ECS
        k.rotateY(tpf);
    },

    destroy(k) {
        k.info("player destroy");
    }
})
