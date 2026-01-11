"use strict";

const U = require("../camUtil.js");

class ThirdPersonCameraMode {
    constructor() {
        this.id = "third";
        this.meta = {supportsZoom: true, hasCollision: true, numRays: 8, playerModelVisible: true};

        // Pivot = точка на игроке, вокруг которой вращаем камеру (НЕ включает плечо)
        this.pivotOffset = {x: 0.0, y: 1.45, z: 0.0};

        // Shoulder = смещение камеры/пивота в сторону (AAA "over-shoulder")
        this.shoulderX = 0.35;

        // Небольшой lift (чтобы камера не "лежала" на плече)
        this.verticalLift = 0.15;

        // Стабилизация (ключ к “не дергается на террейне”)
        this.pivotSmooth = 24.0;
        this.camSmooth = 22.0;

        // internal state (0 alloc per frame)
        this._pivot = {x: 0, y: 0, z: 0};
        this._cam = {x: 0, y: 0, z: 0};
        this._init = false;
    }

    update(ctx) {
        const p = ctx.bodyPos;

        const zo = ctx.zoneOverrides || null;

        const po = (zo && zo.pivotOffset) ? zo.pivotOffset : this.pivotOffset;
        const shoulder = (zo && zo.shoulderX != null) ? +zo.shoulderX : this.shoulderX;
        const lift = (zo && zo.verticalLift != null) ? +zo.verticalLift : this.verticalLift;

        // raw pivot centered on player
        const rawPx = U.vx(p, 0) + (+po.x || 0) + shoulder;
        const rawPy = U.vy(p, 0) + (+po.y || 0);
        const rawPz = U.vz(p, 0) + (+po.z || 0);

        // стабилизируем pivot (ступеньки/террейн/микроколебания)
        if (!this._init) {
            this._init = true;
            this._pivot.x = rawPx;
            this._pivot.y = rawPy;
            this._pivot.z = rawPz;
            this._cam.x = rawPx;
            this._cam.y = rawPy;
            this._cam.z = rawPz;
        } else {
            this._pivot.x = U.expSmooth(this._pivot.x, rawPx, this.pivotSmooth, ctx.dt);
            this._pivot.y = U.expSmooth(this._pivot.y, rawPy, this.pivotSmooth, ctx.dt);
            this._pivot.z = U.expSmooth(this._pivot.z, rawPz, this.pivotSmooth, ctx.dt);
        }

        // Камера всегда “смотрит” в центр игрока
        ctx.target.x = this._pivot.x;
        ctx.target.y = this._pivot.y;
        ctx.target.z = this._pivot.z;

        const yaw = ctx.look.yaw || 0;
        const sin = Math.sin(yaw);
        const cos = Math.cos(yaw);

        const dist = ctx.zoom.value();

        // desired camera (до коллизий)
        const wantX = this._pivot.x - sin * dist;
        const wantY = this._pivot.y + lift;
        const wantZ = this._pivot.z - cos * dist;

        // AAA smoothing (камера не телепортируется)
        this._cam.x = U.expSmooth(this._cam.x, wantX, this.camSmooth, ctx.dt);
        this._cam.y = U.expSmooth(this._cam.y, wantY, this.camSmooth, ctx.dt);
        this._cam.z = U.expSmooth(this._cam.z, wantZ, this.camSmooth, ctx.dt);

        ctx.outPos.x = this._cam.x;
        ctx.outPos.y = this._cam.y;
        ctx.outPos.z = this._cam.z;
    }
}

module.exports = ThirdPersonCameraMode;