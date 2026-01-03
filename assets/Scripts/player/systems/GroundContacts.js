// FILE: Scripts/player/systems/GroundContacts.js
"use strict";

/**
 * GroundContacts (AAA)
 *
 * Stable grounded detection driven by physics collision events.
 *
 * Why:
 *  - relying on begin/end alone is brittle (sleeping bodies, missed end events, tick ordering)
 *  - we instead treat collision.stay as the source of truth per physics step
 *
 * Requires:
 *  - engine.physics.collision.stay (preferred)
 *  - engine.physics.postStep (preferred)
 *
 * Works with older builds too:
 *  - will still listen to begin/end as a fallback, but grounded quality is best with stay+postStep.
 */
class GroundContacts {
    constructor(player, cfg) {
        this.player = player;
        cfg = (cfg && typeof cfg === "object") ? cfg : {};

        // Walkable threshold: normal.y must be >= this
        this.minNy = Number.isFinite(Number(cfg.minNy)) ? Number(cfg.minNy) : 0.55;

        // How close the contact point Y must be to the player's foot to count as ground.
        // Set to <=0 to disable point test.
        this.maxFootPointDist = Number.isFinite(Number(cfg.maxFootPointDist)) ? Number(cfg.maxFootPointDist) : 0.25;

        // Expiration grace in physics steps (helps tiny event gaps). 0 = strict.
        this.graceSteps = Number.isFinite(Number(cfg.graceSteps)) ? (Number(cfg.graceSteps) | 0) : 1;
        if (this.graceSteps < 0) this.graceSteps = 0;

        // Debug logging
        this.debug = true;//!!(cfg.debug && (cfg.debug.enabled !== undefined ? cfg.debug.enabled : cfg.debug));
        this.debugEverySteps = Number.isFinite(Number(cfg.debugEverySteps)) ? (Number(cfg.debugEverySteps) | 0) : 60;
        if (this.debugEverySteps < 1) this.debugEverySteps = 1;

        // otherBodyId -> lastSeenStep
        this._seenStep = new Map();
        // otherBodyId -> bestNy for that step window
        this._seenNy = new Map();

        this._step = -1;
        this._dbgStepCounter = 0;

        this._onBegin = this._onBegin.bind(this);
        this._onStay = this._onStay.bind(this);
        this._onEnd = this._onEnd.bind(this);
        this._onPostStep = this._onPostStep.bind(this);
        this._bound = false;
    }

    bind(bus) {
        if (this._bound) return;
        if (!bus || typeof bus.on !== "function") throw new Error("[ground] bus.on missing");

        // stay+postStep are the reliable path
        bus.on("engine.physics.collision.stay", this._onStay);
        bus.on("engine.physics.postStep", this._onPostStep);

        // begin/end as fallback + extra safety
        bus.on("engine.physics.collision.begin", this._onBegin);
        bus.on("engine.physics.collision.end", this._onEnd);

        this._bound = true;
    }

    unbind(bus) {
        if (!this._bound) return;
        if (bus && typeof bus.off === "function") {
            bus.off("engine.physics.collision.stay", this._onStay);
            bus.off("engine.physics.postStep", this._onPostStep);
            bus.off("engine.physics.collision.begin", this._onBegin);
            bus.off("engine.physics.collision.end", this._onEnd);
        }
        this._seenStep.clear();
        this._seenNy.clear();
        this._step = -1;
        this._dbgStepCounter = 0;
        this._bound = false;
    }

    _myBodyId() {
        return (this.player && (this.player.bodyId | 0)) | 0;
    }

    _characterCfg() {
        return this.player ? (this.player.characterCfg || this.player.character || null) : null;
    }

    _footY() {
        // Best-effort foot computation to filter wall contacts.
        // Uses current body position and character capsule size.
        const my = this._myBodyId();
        if (!my) return NaN;

        // Prefer ref if available to avoid allocations
        let pos = null;
        try {
            const body = (this.player && this.player.body && typeof this.player.body.position === "function") ? this.player.body : null;
            pos = body ? body.position() : null;
        } catch (_) {}

        if (!pos && typeof PHYS !== "undefined" && PHYS && typeof PHYS.position === "function") {
            try { pos = PHYS.position(my); } catch (_) {}
        }
        if (!pos) return NaN;

        const y = Number(pos.y !== undefined ? pos.y : (pos[1] !== undefined ? pos[1] : NaN));
        if (!Number.isFinite(y)) return NaN;

        const cc = this._characterCfg() || {};
        const r = Number.isFinite(Number(cc.radius)) ? Number(cc.radius) : 0.35;
        const h = Number.isFinite(Number(cc.height)) ? Number(cc.height) : 1.80;

        // footY = centerY - (halfHeight - radius)
        return y - ((h * 0.5) - r);
    }

    _extractOther(e) {
        const my = this._myBodyId();
        if (!my) return 0;

        const a = e && e.a;
        const b = e && e.b;
        const aId = a && (a.bodyId | 0);
        const bId = b && (b.bodyId | 0);
        if (!aId || !bId) return 0;

        return (aId === my) ? bId : (bId === my) ? aId : 0;
    }

    _eventNy(e) {
        const c = e && e.contact;
        const n = c && c.normal;
        const ny = n ? Number(n.y) : NaN;
        return Number.isFinite(ny) ? ny : 0;
    }

    _eventPointY(e) {
        const c = e && e.contact;
        const p = c && c.point;
        const py = p ? Number(p.y) : NaN;
        return Number.isFinite(py) ? py : NaN;
    }

    _isGroundContact(e) {
        const ny = this._eventNy(e);
        if (ny < this.minNy) return false;

        // Optional foot point filter.
        if (this.maxFootPointDist > 0) {
            const footY = this._footY();
            const py = this._eventPointY(e);
            if (Number.isFinite(footY) && Number.isFinite(py)) {
                // We expect ground point around foot level (slightly below/above allowed)
                if (Math.abs(py - footY) > this.maxFootPointDist) return false;
            }
        }

        return true;
    }

    _touch(otherId, step, ny) {
        this._seenStep.set(otherId, step | 0);

        const prev = this._seenNy.get(otherId);
        if (prev === undefined || ny > prev) this._seenNy.set(otherId, ny);
    }

    _dbgLog(prefix) {
        if (!this.debug || !LOG || !LOG.info) return;

        const my = this._myBodyId();
        let maxNy = 0;
        let maxOther = 0;
        for (const [k, v] of this._seenNy.entries()) {
            if (v > maxNy) { maxNy = v; maxOther = k | 0; }
        }

        LOG.info(
            "[ground] " + prefix +
            " my=" + my +
            " step=" + (this._step | 0) +
            " contacts=" + this._seenStep.size +
            " best=(other=" + maxOther + " ny=" + maxNy.toFixed(3) + ")"
        );
    }

    _onBegin(e) {
        // Fallback path: if we don't get stay, at least record begin (without normal filtering).
        const other = this._extractOther(e);
        if (!other) return;

        const step = (e && (e.step | 0)) || this._step;
        const ny = this._eventNy(e);
        if (ny > 0) this._touch(other, step, ny);
        else this._touch(other, step, 0);

        if (this.debug && LOG && LOG.info) {
            LOG.info("[ground] begin my=" + this._myBodyId() + " other=" + other + " ny=" + ny.toFixed(3));
        }
    }

    _onStay(e) {
        const other = this._extractOther(e);
        if (!other) return;

        if (!this._isGroundContact(e)) return;

        const step = (e && (e.step | 0)) || this._step;
        const ny = this._eventNy(e);
        this._touch(other, step, ny);
    }

    _onEnd(e) {
        // Extra safety: remove immediately on explicit end.
        const other = this._extractOther(e);
        if (!other) return;

        this._seenStep.delete(other);
        this._seenNy.delete(other);

        if (this.debug && LOG && LOG.info) {
            LOG.info("[ground] end my=" + this._myBodyId() + " other=" + other);
        }
    }

    _onPostStep(e) {
        const step = e ? (e.step | 0) : ((this._step + 1) | 0);
        this._step = step;

        const expireBefore = step - this.graceSteps;
        if (expireBefore >= 0 && this._seenStep.size > 0) {
            for (const [other, lastSeen] of this._seenStep.entries()) {
                if ((lastSeen | 0) < expireBefore) {
                    this._seenStep.delete(other);
                    this._seenNy.delete(other);
                }
            }
        }

        // Periodic debug
        if (this.debug) {
            this._dbgStepCounter = (this._dbgStepCounter + 1) | 0;
            if ((this._dbgStepCounter % this.debugEverySteps) === 0) {
                this._dbgLog("postStep");
            }
        }

        // Clear ny cache each step (we keep only what matters for "this" step window)
        // This prevents stale ny values from confusing debug.
        this._seenNy.clear();
    }

    grounded() {
        return this._seenStep.size > 0;
    }
}

module.exports = GroundContacts;
