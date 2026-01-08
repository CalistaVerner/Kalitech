"use strict";

const {EntityController} = require("../../core/EntityController.js");

const PC_MOD = require("../PlayerController.js");

function pickControllerCtor(mod) {
    if (typeof mod === "function") return mod;
    if (mod && typeof mod.PlayerController === "function") return mod.PlayerController;
    return null;
}

function buildImpl(ctor, pawn) {
    // 1) класс/конструктор
    if (ctor) return new ctor(pawn);

    // 2) модуль-фабрика: create(pawn) / init(pawn) / make(pawn)
    if (PC_MOD) {
        if (typeof PC_MOD.create === "function") return PC_MOD.create(pawn);
        if (typeof PC_MOD.init === "function") return PC_MOD.init(pawn);
        if (typeof PC_MOD.make === "function") return PC_MOD.make(pawn);
    }

    throw new Error("[PlayerMovementController] PlayerController export must provide ctor or create/init/make()");
}

function adaptImpl(impl) {
    if (!impl) throw new Error("[PlayerMovementController] PlayerController instance is null");

    // идеальный контракт
    if (typeof impl.update === "function") return impl;

    // допустимые альтернативы (жёстко перечисленные)
    if (typeof impl.tick === "function") {
        impl.update = impl.tick;
        return impl;
    }
    if (typeof impl.onUpdate === "function") {
        impl.update = impl.onUpdate;
        return impl;
    }

    // жёсткая диагностика (без магии, но с правдой)
    const keys = [];
    for (const k in impl) keys.push(k);
    throw new Error("[PlayerMovementController] controller has no update/tick/onUpdate. keys=" + keys.join(","));
}

class PlayerMovementController extends EntityController {
    constructor() {
        super();
        this.impl = null;
    }

    onStart() {
        const ctor = pickControllerCtor(PC_MOD);
        this.impl = adaptImpl(buildImpl(ctor, this.entity));

        if (typeof this.impl.getMovementCfg === "function") {
            const movCfg = this.impl.getMovementCfg();
            this.entity.characterCfg.loadFrom(this.entity.cfg, movCfg);
        }
    }

    onUpdate(dt) {
        if (!this.impl) this.onStart();

        const pawn = this.entity;

        pawn.beginFrame(dt);
        pawn.syncPose();

        // HARD LEGACY INPUT CONTRACT:
        // legacy Scripts/player/PlayerController.js reads input via some frame.* reference
        const f = pawn.frame;
        const inp = pawn.d && pawn.d.input;
        if (!inp) throw new Error("[PlayerMovementController] pawn.d.input is required");

        // cover the common legacy names in ONE place (compat bridge)
        f.input = inp;
        f.controls = inp;
        f.ctrl = inp;
        f.inp = inp;

        this.impl.update(f);
    }


    onStop() {
        if (this.impl && typeof this.impl.destroy === "function") this.impl.destroy();
        this.impl = null;
    }
}

module.exports = {PlayerMovementController};
